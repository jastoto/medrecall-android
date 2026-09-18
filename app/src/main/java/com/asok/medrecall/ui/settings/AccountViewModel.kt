package com.asok.medrecall.ui.settings

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.asok.medrecall.data.calendar.GoogleCalendarInfo
import com.asok.medrecall.data.calendar.GoogleCalendarService
import com.asok.medrecall.data.calendar.GoogleCalendarSyncManager
import com.asok.medrecall.data.local.MedRecallDatabase
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

data class AccountUiState(
    val signedInLabel: String? = null,
    val availableCalendars: List<GoogleCalendarInfo> = emptyList(),
    val selectedCalendarId: String? = null,
    val selectedCalendarName: String? = null,
    val isBusy: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

/**
 * Drives Settings > Account's Calendar section: sign in to Google, pick
 * which of the signed-in account's calendars to sync Appointments to,
 * and switch/disconnect later. This is a separate Google sign-in from
 * Backup & Restore's Google Drive connector -- own scopes, own account
 * state (see GoogleCalendarService) -- even though the sign-in UI works
 * the same way for both.
 *
 * The first time a calendar is selected, every existing appointment is
 * pushed to it retroactively via [GoogleCalendarSyncManager.syncAllExisting];
 * from then on, [com.asok.medrecall.ui.appointments.AppointmentsViewModel]
 * keeps it in sync automatically as appointments are saved/deleted.
 */
class AccountViewModel(
    context: Context,
    private val calendarService: GoogleCalendarService,
    private val syncManager: GoogleCalendarSyncManager
) : ViewModel() {

    private val appContext = context.applicationContext

    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    val googleSignInClient: GoogleSignInClient by lazy {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(
                Scope(GoogleCalendarService.READONLY_SCOPE),
                Scope(GoogleCalendarService.EVENTS_SCOPE)
            )
            .build()
        GoogleSignIn.getClient(appContext, options)
    }

    init {
        viewModelScope.launch {
            val signedInLabel = calendarService.signedInAccountLabel()
            _uiState.update {
                it.copy(
                    signedInLabel = signedInLabel,
                    selectedCalendarId = calendarService.selectedCalendarId(),
                    selectedCalendarName = calendarService.selectedCalendarName()
                )
            }
            if (signedInLabel != null && calendarService.selectedCalendarId() == null) {
                refreshCalendars()
            }
        }
    }

    /** Called by the screen once GoogleSignInClient's own sign-in Activity result comes back. */
    fun onGoogleSignInResult(data: Intent?) {
        viewModelScope.launch {
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException::class.java)
                val label = account.email ?: account.displayName ?: "Google account"
                calendarService.onSignedIn(label)
                _uiState.update { it.copy(signedInLabel = label, errorMessage = null) }
                refreshCalendars()
            } catch (e: ApiException) {
                _uiState.update { it.copy(errorMessage = "Google sign-in failed: ${e.message ?: e.statusCode}") }
            }
        }
    }

    fun refreshCalendars() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            calendarService.listCalendars()
                .onSuccess { calendars ->
                    _uiState.update { it.copy(isBusy = false, availableCalendars = calendars) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isBusy = false, errorMessage = "Couldn't load your calendars: ${e.message}") }
                }
        }
    }

    fun selectCalendar(calendar: GoogleCalendarInfo) {
        val isFirstConnect = _uiState.value.selectedCalendarId == null
        calendarService.selectCalendar(calendar)
        _uiState.update {
            it.copy(
                selectedCalendarId = calendar.id,
                selectedCalendarName = calendar.name,
                statusMessage = null,
                errorMessage = null
            )
        }
        if (isFirstConnect) {
            viewModelScope.launch {
                _uiState.update { it.copy(isBusy = true) }
                syncManager.syncAllExisting()
                    .onSuccess { count ->
                        _uiState.update {
                            it.copy(isBusy = false, statusMessage = "Connected -- pushed $count existing appointment(s) to ${calendar.name}.")
                        }
                    }
                    .onFailure { e ->
                        _uiState.update { it.copy(isBusy = false, errorMessage = "Connected, but the initial sync failed: ${e.message}") }
                    }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            calendarService.signOut()
            googleSignInClient.signOut()
            _uiState.value = AccountUiState()
        }
    }

    fun dismissMessages() {
        _uiState.update { it.copy(statusMessage = null, errorMessage = null) }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val appContext = context.applicationContext
                val db = MedRecallDatabase.getInstance(appContext)
                val calendarService = GoogleCalendarService(appContext)
                val syncManager = GoogleCalendarSyncManager(
                    calendarService,
                    db.appointmentDao(),
                    db.doctorDao()
                )
                return AccountViewModel(context, calendarService, syncManager) as T
            }
        }
    }
}
