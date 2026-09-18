package com.asok.medrecall.ui.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.asok.medrecall.data.backup.BackupCodec
import com.asok.medrecall.data.backup.BackupDestination
import com.asok.medrecall.data.backup.BackupSection
import com.asok.medrecall.data.backup.CloudBackupFile
import com.asok.medrecall.data.backup.CloudBackupService
import com.asok.medrecall.data.backup.GoogleDriveBackupService
import com.asok.medrecall.data.backup.OneDriveBackupService
import com.asok.medrecall.data.local.MedRecallDatabase
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BackupUiState(
    val selectedDestination: BackupDestination = BackupDestination.GOOGLE_DRIVE,
    val selectedSections: Set<BackupSection> = BackupSection.entries.toSet(),
    val googleSignedInLabel: String? = null,
    val oneDriveSignedInLabel: String? = null,
    val availableBackups: List<CloudBackupFile> = emptyList(),
    val isBusy: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val pendingRestoreFile: CloudBackupFile? = null
)

/**
 * Drives Settings > Backup & Restore. Google Drive and OneDrive are real
 * connectors (see data/backup/); "Files on This Phone" is still a UI
 * shell -- [backUpNow] reports it as coming soon rather than doing
 * anything, same as before this punch-list item.
 *
 * Sign-in itself is split across this ViewModel and the screen because
 * both providers need an Activity/Intent to show their login UI, which
 * a ViewModel can't hold onto: the screen launches the actual intent
 * (Google) or passes its Activity through (OneDrive/MSAL), and reports
 * back here once that's done.
 */
class BackupViewModel(
    context: Context,
    private val db: MedRecallDatabase,
    private val googleService: GoogleDriveBackupService,
    private val oneDriveService: OneDriveBackupService
) : ViewModel() {

    private val appContext = context.applicationContext

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    val googleSignInClient: GoogleSignInClient by lazy {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(GoogleDriveBackupService.DRIVE_FILE_SCOPE))
            .build()
        GoogleSignIn.getClient(appContext, options)
    }

    init {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    googleSignedInLabel = googleService.signedInAccountLabel(),
                    oneDriveSignedInLabel = oneDriveService.signedInAccountLabel()
                )
            }
        }
    }

    fun selectDestination(destination: BackupDestination) {
        _uiState.update {
            it.copy(
                selectedDestination = destination,
                availableBackups = emptyList(),
                statusMessage = null,
                errorMessage = null
            )
        }
    }

    fun toggleSection(section: BackupSection) {
        _uiState.update {
            val current = it.selectedSections
            it.copy(selectedSections = if (section in current) current - section else current + section)
        }
    }

    fun selectAllSections() {
        _uiState.update { it.copy(selectedSections = BackupSection.entries.toSet()) }
    }

    /** Called by the screen once GoogleSignInClient's own sign-in Activity result comes back. */
    fun onGoogleSignInResult(data: Intent?) {
        viewModelScope.launch {
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException::class.java)
                val label = account.email ?: account.displayName ?: "Google account"
                googleService.onSignedIn(label)
                _uiState.update { it.copy(googleSignedInLabel = label, errorMessage = null) }
            } catch (e: ApiException) {
                _uiState.update { it.copy(errorMessage = "Google sign-in failed: ${e.message ?: e.statusCode}") }
            }
        }
    }

    fun signInToOneDrive(activity: Activity) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            oneDriveService.signIn(activity)
                .onSuccess { label ->
                    _uiState.update { it.copy(isBusy = false, oneDriveSignedInLabel = label) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isBusy = false, errorMessage = "OneDrive sign-in failed: ${e.message}") }
                }
        }
    }

    private fun serviceFor(destination: BackupDestination): CloudBackupService? = when (destination) {
        BackupDestination.GOOGLE_DRIVE -> googleService
        BackupDestination.ONE_DRIVE -> oneDriveService
        BackupDestination.THIS_PHONE -> null
    }

    fun refreshBackupList() {
        val destination = _uiState.value.selectedDestination
        val service = serviceFor(destination) ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            service.listBackups()
                .onSuccess { files ->
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            availableBackups = files.sortedByDescending { f -> f.modifiedAtMillis ?: 0L }
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isBusy = false, errorMessage = describeError(destination, e)) }
                }
        }
    }

    fun backUpNow() {
        val state = _uiState.value
        if (state.selectedSections.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Choose at least one section to back up.") }
            return
        }
        val service = serviceFor(state.selectedDestination)
        if (service == null) {
            _uiState.update { it.copy(errorMessage = "Backing up to ${state.selectedDestination.label} is coming in a future update.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null, statusMessage = null) }
            val json = BackupCodec.export(db, state.selectedSections)
            val fileName = "medrecall_backup_${System.currentTimeMillis()}.json"
            service.upload(fileName, json)
                .onSuccess {
                    _uiState.update { it.copy(isBusy = false, statusMessage = "Backed up to ${state.selectedDestination.label}.") }
                    refreshBackupList()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isBusy = false, errorMessage = describeError(state.selectedDestination, e)) }
                }
        }
    }

    fun requestRestore(file: CloudBackupFile) {
        _uiState.update { it.copy(pendingRestoreFile = file) }
    }

    fun cancelRestore() {
        _uiState.update { it.copy(pendingRestoreFile = null) }
    }

    fun confirmRestore() {
        val state = _uiState.value
        val file = state.pendingRestoreFile ?: return
        val service = serviceFor(state.selectedDestination) ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null, statusMessage = null, pendingRestoreFile = null) }
            service.download(file)
                .mapCatching { json -> BackupCodec.import(db, json) }
                .onSuccess { sections ->
                    _uiState.update {
                        it.copy(isBusy = false, statusMessage = "Restored ${sections.size} section(s) from ${file.name}.")
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isBusy = false, errorMessage = "Restore failed: ${e.message}") }
                }
        }
    }

    fun dismissMessages() {
        _uiState.update { it.copy(statusMessage = null, errorMessage = null) }
    }

    private fun describeError(destination: BackupDestination, e: Throwable): String {
        if (e is UserRecoverableAuthException) {
            return "Google needs you to approve Drive access again -- please sign in once more."
        }
        return "${destination.label} error: ${e.message ?: e.javaClass.simpleName}"
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val appContext = context.applicationContext
                val db = MedRecallDatabase.getInstance(appContext)
                return BackupViewModel(
                    context,
                    db,
                    GoogleDriveBackupService(appContext),
                    OneDriveBackupService(appContext)
                ) as T
            }
        }
    }
}
