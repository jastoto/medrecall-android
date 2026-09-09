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
import com.asok.medrecall.data.backup.BackupResult
import com.asok.medrecall.data.backup.BackupSection
import com.asok.medrecall.data.backup.DriveBackupFile
import com.asok.medrecall.data.backup.DriveBackupManager
import com.asok.medrecall.data.backup.DriveRestoreManager
import com.asok.medrecall.data.backup.RestoreResult
import com.asok.medrecall.data.backup.SectionBackups
import com.asok.medrecall.data.local.MedRecallDatabase
import com.asok.medrecall.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Whether "Back Up Now" covers every section or just the ones checked -- mutually exclusive, per Asok's spec. */
enum class BackupMode { FULL, INDIVIDUAL }

sealed class BackupRunState {
    data object Idle : BackupRunState()
    data object AuthorizingDrive : BackupRunState()
    data object Uploading : BackupRunState()
    data class Done(val results: List<BackupResult>) : BackupRunState()
    data class Error(val message: String) : BackupRunState()
}

sealed class RestoreRunState {
    data object Idle : RestoreRunState()
    data object AuthorizingDrive : RestoreRunState()
    data object LoadingBackups : RestoreRunState()
    data class Picking(val sections: List<SectionBackups>, val accessToken: String) : RestoreRunState()
    data object Restoring : RestoreRunState()
    data class Done(val results: List<RestoreResult>) : RestoreRunState()
    data class Error(val message: String) : RestoreRunState()
}

/**
 * Backs Settings > Backup & Restore's Google Drive "Back Up Now" flow: mode
 * (Full vs Individual sections) and section selection live here; the
 * connect-then-authorize-then-upload sequence goes through
 * GoogleAccountManager / DriveBackupManager -- see those classes' doc
 * comments for the two-step OAuth flow and the JSON export format.
 */
class BackupViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    val googleAccount = settingsRepository.googleAccount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _mode = MutableStateFlow(BackupMode.FULL)
    val mode: StateFlow<BackupMode> = _mode

    private val _selectedSections = MutableStateFlow<Set<BackupSection>>(emptySet())
    val selectedSections: StateFlow<Set<BackupSection>> = _selectedSections

    private val _runState = MutableStateFlow<BackupRunState>(BackupRunState.Idle)
    val runState: StateFlow<BackupRunState> = _runState

    /** Set when Play Services needs its own consent screen; the screen launches this via an ActivityResultLauncher. */
    private val _pendingResolution = MutableStateFlow<IntentSender?>(null)
    val pendingResolution: StateFlow<IntentSender?> = _pendingResolution

    private val _restoreState = MutableStateFlow<RestoreRunState>(RestoreRunState.Idle)
    val restoreState: StateFlow<RestoreRunState> = _restoreState

    /** Same Play-Services-consent-screen hand-off as [pendingResolution], for the restore flow's own auth request. */
    private val _restorePendingResolution = MutableStateFlow<IntentSender?>(null)
    val restorePendingResolution: StateFlow<IntentSender?> = _restorePendingResolution

    fun setMode(newMode: BackupMode) {
        _mode.value = newMode
    }

    fun toggleSection(section: BackupSection) {
        _selectedSections.value = _selectedSections.value.toMutableSet().apply {
            if (contains(section)) remove(section) else add(section)
        }
    }

    /** Kicks off Back Up Now for Google Drive: re-authorize (silent if Drive access was already granted) then upload. */
    fun backUpToGoogleDrive(activity: Activity) {
        val sections = sectionsForCurrentMode()
        if (sections.isEmpty()) {
            _runState.value = BackupRunState.Error("Pick at least one section to back up.")
            return
        }

        viewModelScope.launch {
            _runState.value = BackupRunState.AuthorizingDrive
            try {
                applyAuthOutcome(activity, GoogleAccountManager.requestDriveAuthorization(activity), sections)
            } catch (e: Exception) {
                _runState.value = BackupRunState.Error(e.message ?: "Could not connect to Google Drive.")
            }
        }
    }

    /** Called by BackupRestoreScreen's ActivityResultLauncher callback once the user responds to a launched consent screen. */
    fun onDriveConsentResult(activity: Activity, resultCode: Int, data: Intent?) {
        _pendingResolution.value = null
        if (resultCode != Activity.RESULT_OK) {
            _runState.value = BackupRunState.Idle
            return
        }
        val sections = sectionsForCurrentMode()
        viewModelScope.launch {
            applyAuthOutcome(activity, GoogleAccountManager.handleAuthorizationResult(activity, data), sections)
        }
    }

    private fun sectionsForCurrentMode(): List<BackupSection> =
        if (_mode.value == BackupMode.FULL) BackupSection.entries.toList() else _selectedSections.value.toList()

    private suspend fun applyAuthOutcome(activity: Activity, outcome: DriveAuthOutcome, sections: List<BackupSection>) {
        when (outcome) {
            is DriveAuthOutcome.Authorized -> {
                val token = outcome.accessToken
                if (token == null) {
                    _runState.value = BackupRunState.Error("Google didn't return an access token.")
                    return
                }
                _runState.value = BackupRunState.Uploading
                val database = MedRecallDatabase.getInstance(activity.applicationContext)
                _runState.value = BackupRunState.Done(DriveBackupManager.backupSections(database, token, sections))
            }
            is DriveAuthOutcome.NeedsResolution -> {
                _pendingResolution.value = outcome.intentSender
                // runState stays AuthorizingDrive until the launcher result comes back.
            }
            is DriveAuthOutcome.Failed -> {
                _runState.value = BackupRunState.Error(outcome.message)
            }
        }
    }


    /** Kicks off Settings > Backup & Restore's "Restore" for Google Drive: re-authorize (silent if already granted), then list what's available to restore from. */
    fun startGoogleDriveRestore(activity: Activity) {
        viewModelScope.launch {
            _restoreState.value = RestoreRunState.AuthorizingDrive
            try {
                applyRestoreAuthOutcome(activity, GoogleAccountManager.requestDriveAuthorization(activity))
            } catch (e: Exception) {
                _restoreState.value = RestoreRunState.Error(e.message ?: "Could not connect to Google Drive.")
            }
        }
    }

    /** Called by BackupRestoreScreen's restore ActivityResultLauncher callback once the user responds to a launched consent screen. */
    fun onRestoreDriveConsentResult(activity: Activity, resultCode: Int, data: Intent?) {
        _restorePendingResolution.value = null
        if (resultCode != Activity.RESULT_OK) {
            _restoreState.value = RestoreRunState.Idle
            return
        }
        viewModelScope.launch {
            applyRestoreAuthOutcome(activity, GoogleAccountManager.handleAuthorizationResult(activity, data))
        }
    }

    private suspend fun applyRestoreAuthOutcome(activity: Activity, outcome: DriveAuthOutcome) {
        when (outcome) {
            is DriveAuthOutcome.Authorized -> {
                val token = outcome.accessToken
                if (token == null) {
                    _restoreState.value = RestoreRunState.Error("Google didn't return an access token.")
                    return
                }
                _restoreState.value = RestoreRunState.LoadingBackups
                try {
                    val grouped = DriveRestoreManager.groupForPicker(DriveRestoreManager.listAvailableBackups(token))
                    _restoreState.value = if (grouped.isEmpty()) {
                        RestoreRunState.Error("No backups found yet in My Drive > MedRecall > Android Backup.")
                    } else {
                        RestoreRunState.Picking(grouped, token)
                    }
                } catch (e: Exception) {
                    _restoreState.value = RestoreRunState.Error(e.message ?: "Could not read backups from Google Drive.")
                }
            }
            is DriveAuthOutcome.NeedsResolution -> {
                _restorePendingResolution.value = outcome.intentSender
                // restoreState stays AuthorizingDrive until the launcher result comes back.
            }
            is DriveAuthOutcome.Failed -> {
                _restoreState.value = RestoreRunState.Error(outcome.message)
            }
        }
    }

    /** Called once the user confirms which section/date combinations to restore in the picker dialog. Each chosen file REPLACES that section's current data. */
    fun confirmRestore(activity: Activity, accessToken: String, chosen: List<DriveBackupFile>) {
        if (chosen.isEmpty()) {
            _restoreState.value = RestoreRunState.Error("Pick at least one section to restore.")
            return
        }
        viewModelScope.launch {
            _restoreState.value = RestoreRunState.Restoring
            val database = MedRecallDatabase.getInstance(activity.applicationContext)
            val results = chosen.map { file -> DriveRestoreManager.restoreFile(database, accessToken, file) }
            _restoreState.value = RestoreRunState.Done(results)
        }
    }

    fun cancelRestorePicker() {
        _restoreState.value = RestoreRunState.Idle
    }

    fun dismissRestoreResult() {
        _restoreState.value = RestoreRunState.Idle
    }

    fun dismissResult() {
        _runState.value = BackupRunState.Idle
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return BackupViewModel(SettingsRepository.getInstance(context)) as T
            }
        }
    }
}
