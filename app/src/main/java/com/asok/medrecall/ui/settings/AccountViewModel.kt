package com.asok.medrecall.ui.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.asok.medrecall.data.account.DriveAuthOutcome
import com.asok.medrecall.data.account.GoogleAccountManager
import com.asok.medrecall.data.settings.GoogleAccountSelection
import com.asok.medrecall.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the Google Drive row under Settings > Account is currently doing. */
sealed class GoogleConnectState {
    data object Idle : GoogleConnectState()
    data object SigningIn : GoogleConnectState()
    data object RequestingDriveAccess : GoogleConnectState()
    data class Error(val message: String) : GoogleConnectState()
}

/**
 * Backs Settings > Account's "Google Drive" row: sign-in identity is
 * persisted via [SettingsRepository]; the actual OAuth calls go through
 * [GoogleAccountManager]. See that class's doc comment for the two-step
 * sign-in-then-authorize flow and the RISK FLAG on this being unverified
 * external-SDK code.
 */
class AccountViewModel(private val repository: SettingsRepository) : ViewModel() {

    val googleAccount: StateFlow<GoogleAccountSelection?> = repository.googleAccount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _connectState = MutableStateFlow<GoogleConnectState>(GoogleConnectState.Idle)
    val connectState: StateFlow<GoogleConnectState> = _connectState

    /** Set by [requestDriveAuthorization] when Play Services needs its own consent screen; the screen (AccountScreen) launches this via an ActivityResultLauncher. */
    private val _pendingResolution = MutableStateFlow<IntentSender?>(null)
    val pendingResolution: StateFlow<IntentSender?> = _pendingResolution

    /** Starts the full connect flow: sign in, then request Drive access. [activity] is required for both steps (Credential Manager UI, then Drive consent). */
    fun connectGoogleDrive(activity: Activity) {
        viewModelScope.launch {
            _connectState.value = GoogleConnectState.SigningIn
            try {
                val signInInfo = GoogleAccountManager.signIn(activity)
                if (signInInfo == null) {
                    // User cancelled the account picker -- not an error, just stop.
                    _connectState.value = GoogleConnectState.Idle
                    return@launch
                }
                repository.setGoogleAccount(signInInfo.email, signInInfo.displayName)

                _connectState.value = GoogleConnectState.RequestingDriveAccess
                applyDriveOutcome(activity, GoogleAccountManager.requestDriveAuthorization(activity))
            } catch (e: Exception) {
                _connectState.value = GoogleConnectState.Error(e.message ?: "Google sign-in failed.")
            }
        }
    }

    /** Called by AccountScreen's ActivityResultLauncher callback once the user responds to a launched consent screen. */
    fun onDriveConsentResult(activity: Activity, resultCode: Int, data: Intent?) {
        _pendingResolution.value = null
        if (resultCode != Activity.RESULT_OK) {
            _connectState.value = GoogleConnectState.Idle
            return
        }
        viewModelScope.launch {
            applyDriveOutcome(activity, GoogleAccountManager.handleAuthorizationResult(activity, data))
        }
    }

    private suspend fun applyDriveOutcome(activity: Activity, outcome: DriveAuthOutcome) {
        when (outcome) {
            is DriveAuthOutcome.Authorized -> {
                repository.setGoogleDriveConnected(true)
                _connectState.value = GoogleConnectState.Idle
            }
            is DriveAuthOutcome.NeedsResolution -> {
                _pendingResolution.value = outcome.intentSender
                // connectState stays RequestingDriveAccess until the launcher result comes back.
            }
            is DriveAuthOutcome.Failed -> {
                _connectState.value = GoogleConnectState.Error(outcome.message)
            }
        }
    }

    /** Disconnects the Google account entirely (Settings > Account > Google Drive > Disconnect). */
    fun disconnectGoogleDrive(context: Context) {
        viewModelScope.launch {
            GoogleAccountManager.signOut(context)
            repository.clearGoogleAccount()
        }
    }

    fun dismissError() {
        _connectState.value = GoogleConnectState.Idle
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AccountViewModel(SettingsRepository.getInstance(context)) as T
            }
        }
    }
}
