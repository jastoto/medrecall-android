package com.asok.medrecall.data.account

import android.app.Activity
import android.content.Context
import com.asok.medrecall.R
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAccount
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IPublicClientApplication
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.exception.MsalException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Basic identity + a fresh Graph access token, returned by a successful Microsoft sign-in/re-auth. */
data class MicrosoftSignInInfo(
    val email: String,
    val displayName: String?,
    val accessToken: String
)

/** Outcome of asking Microsoft for a fresh Graph access token (sign-in, reconnect, or per-action refresh). */
sealed class MicrosoftAuthOutcome {
    data class Authorized(val info: MicrosoftSignInInfo) : MicrosoftAuthOutcome()
    data object Cancelled : MicrosoftAuthOutcome()
    data class Failed(val message: String) : MicrosoftAuthOutcome()
}

/**
 * Wraps Microsoft Authentication Library (MSAL) for Settings > Account >
 * OneDrive and Settings > Backup & Restore's OneDrive destination.
 *
 * Unlike GoogleAccountManager's two-step sign-in-then-authorize dance, MSAL
 * combines identity + the Microsoft Graph scopes we need (profile +
 * Files.ReadWrite) into one [getAccessToken] call: it tries a silent token
 * refresh first (no UI, works once signed in and still within Microsoft's
 * refresh-token lifetime), and only falls back to an interactive
 * browser-tab sign-in when silent fails or nothing is signed in yet. This
 * is why AccountViewModel/BackupViewModel need no ActivityResultLauncher
 * "NeedsResolution" hand-off the way the Google Drive flow does -- MSAL's
 * browser tab is launched and awaited entirely inside this one call.
 *
 * Configuration lives in res/raw/msal_config.json (client_id, redirect_uri,
 * "Personal Microsoft accounts only" authority) -- see that file and the
 * BrowserTabActivity entry in AndroidManifest.xml for the Azure App
 * Registration setup this depends on.
 *
 * RISK FLAG: unverified external-SDK surface, same category as
 * GoogleAccountManager/HealthConnectManager -- no compiler available here.
 * The likeliest first-build failure points, in order: (1) the placeholder
 * client_id in msal_config.json not yet replaced with Asok's real Azure
 * Application (client) ID, (2) the redirect_uri there / in the manifest not
 * exactly matching what's registered on the Azure app's Android platform
 * config, (3) an MSAL API name that shifted between the 8.4.2 version
 * pinned here and whatever the docs/samples Asok finds describe.
 */
object MicrosoftAccountManager {

    /** Files.ReadWrite for OneDrive backup/restore; User.Read so the signed-in account's display name is available (email alone comes from IAccount.username for every account type). */
    private val GRAPH_SCOPES = arrayOf("User.Read", "Files.ReadWrite")

    @Volatile
    private var pca: ISingleAccountPublicClientApplication? = null

    private suspend fun getPca(context: Context): ISingleAccountPublicClientApplication {
        pca?.let { return it }
        return suspendCancellableCoroutine { cont ->
            PublicClientApplication.createSingleAccountPublicClientApplication(
                context.applicationContext,
                R.raw.msal_config,
                object : IPublicClientApplication.ISingleAccountApplicationCreatedListener {
                    override fun onCreated(application: ISingleAccountPublicClientApplication) {
                        pca = application
                        cont.resume(application)
                    }

                    override fun onError(exception: MsalException) {
                        cont.resumeWithException(exception)
                    }
                }
            )
        }
    }

    private suspend fun getCurrentAccount(app: ISingleAccountPublicClientApplication): IAccount? =
        suspendCancellableCoroutine { cont ->
            app.getCurrentAccountAsync(object : ISingleAccountPublicClientApplication.CurrentAccountCallback {
                override fun onAccountLoaded(activeAccount: IAccount?) {
                    cont.resume(activeAccount)
                }

                override fun onAccountChanged(priorAccount: IAccount?, currentAccount: IAccount?) {
                    // Handled by the next getCurrentAccountAsync call instead of here.
                }

                override fun onError(exception: MsalException) {
                    cont.resume(null)
                }
            })
        }

    /**
     * Gets a fresh Graph access token for whichever account is signed in,
     * signing in first if none is. [activity] is required (not just a
     * Context) because an interactive fallback may need to show MSAL's
     * browser tab.
     */
    suspend fun getAccessToken(activity: Activity): MicrosoftAuthOutcome {
        return try {
            val app = getPca(activity)
            val currentAccount = getCurrentAccount(app)

            if (currentAccount != null) {
                val silentResult = trySilent(app, currentAccount)
                if (silentResult != null) return MicrosoftAuthOutcome.Authorized(toSignInInfo(silentResult))
            }

            interactive(app, activity, currentAccount)
        } catch (e: MsalException) {
            MicrosoftAuthOutcome.Failed(e.message ?: "Microsoft sign-in failed.")
        } catch (e: Exception) {
            MicrosoftAuthOutcome.Failed(e.message ?: "Microsoft sign-in failed.")
        }
    }

    /**
     * Blocking MSAL API, per Microsoft's own guidance run off the main
     * thread; returns null (never throws) so the caller falls back to
     * interactive.
     *
     * SPECIFIC RISK: this positional acquireTokenSilent(scopes, authority)
     * overload is the classic MSAL quickstart signature. If MSAL 8.4.2 has
     * dropped it in favor of only the AcquireTokenSilentParameters.Builder()
     * form, this is the one line to fix -- swap in
     * app.acquireTokenSilent(AcquireTokenSilentParameters.Builder()
     * .fromAuthority(account.authority).forAccount(account)
     * .withScopes(GRAPH_SCOPES.toList()).build()) instead.
     */
    private suspend fun trySilent(app: ISingleAccountPublicClientApplication, account: IAccount): IAuthenticationResult? =
        withContext(Dispatchers.IO) {
            try {
                app.acquireTokenSilent(GRAPH_SCOPES, account.authority)
            } catch (e: MsalException) {
                null
            }
        }

    /** Interactive sign-in (no account yet) or interactive re-auth (silent failed) -- either way shows MSAL's browser tab. Must be called with an Activity, and MSAL requires this on the main thread. */
    private suspend fun interactive(
        app: ISingleAccountPublicClientApplication,
        activity: Activity,
        currentAccount: IAccount?
    ): MicrosoftAuthOutcome = suspendCancellableCoroutine { cont ->
        val callback = object : AuthenticationCallback {
            override fun onSuccess(authenticationResult: IAuthenticationResult) {
                cont.resume(MicrosoftAuthOutcome.Authorized(toSignInInfo(authenticationResult)))
            }

            override fun onError(exception: MsalException) {
                cont.resume(MicrosoftAuthOutcome.Failed(exception.message ?: "Microsoft sign-in failed."))
            }

            override fun onCancel() {
                cont.resume(MicrosoftAuthOutcome.Cancelled)
            }
        }

        if (currentAccount == null) {
            app.signIn(activity, null, GRAPH_SCOPES, callback)
        } else {
            app.acquireToken(activity, GRAPH_SCOPES, callback)
        }
    }

    private fun toSignInInfo(result: IAuthenticationResult): MicrosoftSignInInfo {
        val displayName = result.account.claims?.get("name") as? String
        return MicrosoftSignInInfo(
            email = result.account.username,
            displayName = displayName,
            accessToken = result.accessToken
        )
    }

    /** Signs out of MSAL's single-account cache entirely (Settings > Account > OneDrive > Disconnect). */
    suspend fun signOut(context: Context) {
        try {
            val app = getPca(context)
            suspendCancellableCoroutine<Unit> { cont ->
                app.signOut(object : ISingleAccountPublicClientApplication.SignOutCallback {
                    override fun onSignOut() {
                        cont.resume(Unit)
                    }

                    override fun onError(exception: MsalException) {
                        // Best-effort, matching GoogleAccountManager.signOut -- MedRecall's
                        // own "connected" flag is cleared by the caller regardless.
                        cont.resume(Unit)
                    }
                })
            }
        } catch (e: Exception) {
            // Best-effort; see above.
        }
    }
}
