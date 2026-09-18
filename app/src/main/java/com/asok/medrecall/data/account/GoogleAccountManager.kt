package com.asok.medrecall.data.account

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.asok.medrecall.R
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.tasks.await

/** Basic identity returned by a successful Google sign-in -- no Drive access implied yet. */
data class GoogleSignInInfo(
    val email: String,
    val displayName: String?
)

/** Outcome of asking Google for Drive access, either just after sign-in or on reconnect. */
sealed class DriveAuthOutcome {
    /** Drive access was granted; [accessToken] is short-lived (about 1 hour) -- never persisted, only held in memory for the current session. */
    data class Authorized(val accessToken: String?) : DriveAuthOutcome()

    /** Play Services needs to show its own consent screen -- launch [intentSender] and feed the result back to [handleAuthorizationResult]. */
    data class NeedsResolution(val intentSender: IntentSender) : DriveAuthOutcome()

    data class Failed(val message: String) : DriveAuthOutcome()
}

/**
 * Wraps the two calls behind Settings > Account > Google Drive:
 *  1. [signIn] -- Credential Manager's "Sign in with Google" flow, returns the
 *     signed-in account's email/name. This alone proves identity; it grants
 *     no access to Drive or anything else.
 *  2. [requestDriveAuthorization] -- Google Identity Services' Authorization
 *     API, asked for full Drive access (scope "https://www.googleapis.com/auth/drive",
 *     Asok's choice -- broader than the app-only "drive.file" scope). Play
 *     Services may be able to grant this silently if it was already approved
 *     for this Google account, or may need to show its own consent screen --
 *     see [DriveAuthOutcome.NeedsResolution].
 *
 * RISK FLAG: unverified external-SDK surface, same category as
 * HealthConnectManager/BiometricPrompt -- no compiler available here, so the
 * most likely failure points on Asok's first build are an unresolved
 * Credential Manager / Play Services Auth symbol, or the placeholder
 * @string/google_web_client_id not yet replaced with a real Web client ID
 * from Google Cloud Console.
 */
object GoogleAccountManager {

    private const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive"

    /**
     * Shows Android's "Sign in with Google" bottom sheet. [context] must be
     * an Activity context (Credential Manager may need to show UI).
     * Returns null if the user cancels rather than throwing.
     */
    suspend fun signIn(context: Context): GoogleSignInInfo? {
        val credentialManager = CredentialManager.create(context)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(context.getString(R.string.google_web_client_id))
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val result = credentialManager.getCredential(context = context, request = request)
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                GoogleSignInInfo(
                    email = googleIdTokenCredential.id,
                    displayName = googleIdTokenCredential.displayName
                )
            } else {
                null
            }
        } catch (e: GetCredentialCancellationException) {
            null
        } catch (e: GetCredentialException) {
            throw e
        }
    }

    /**
     * Requests full Drive access for whichever Google account just signed in.
     * [activity] is required (not just a Context) because the Authorization
     * API's consent screen, if needed, is launched as an Activity result.
     */
    suspend fun requestDriveAuthorization(activity: Activity): DriveAuthOutcome {
        val authorizationRequest = AuthorizationRequest.Builder()
            .setRequestedScopes(listOf(Scope(DRIVE_SCOPE)))
            .build()

        return try {
            val result = Identity.getAuthorizationClient(activity)
                .authorize(authorizationRequest)
                .await()

            if (result.hasResolution()) {
                val pendingIntent = result.pendingIntent
                val intentSender = pendingIntent?.intentSender
                if (intentSender != null) {
                    DriveAuthOutcome.NeedsResolution(intentSender)
                } else {
                    DriveAuthOutcome.Failed("Google did not provide a consent screen to show.")
                }
            } else {
                DriveAuthOutcome.Authorized(result.accessToken)
            }
        } catch (e: ApiException) {
            DriveAuthOutcome.Failed(e.message ?: "Google Drive authorization failed.")
        }
    }

    /** Call this from the ActivityResultLauncher callback after launching a [DriveAuthOutcome.NeedsResolution] intent sender. */
    fun handleAuthorizationResult(activity: Activity, data: Intent?): DriveAuthOutcome {
        if (data == null) return DriveAuthOutcome.Failed("Google Drive authorization was cancelled.")
        return try {
            val result = Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data)
            DriveAuthOutcome.Authorized(result.accessToken)
        } catch (e: ApiException) {
            DriveAuthOutcome.Failed(e.message ?: "Google Drive authorization failed.")
        }
    }

    /** Clears Credential Manager's remembered sign-in state so the account picker shows again next time. */
    suspend fun signOut(context: Context) {
        try {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            // Best-effort -- MedRecall's own "connected" flag is cleared by the
            // caller regardless, so a failure here just means Android might
            // still remember the credential for next time (harmless).
        }
    }
}
