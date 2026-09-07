package com.asok.medrecall.ui.lock

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.asok.medrecall.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class AppLockUiState(
    /** True until the first read of the lock settings comes back -- callers should show a blank splash, not the lock screen or the app, while this is true. */
    val isLoading: Boolean = true,
    val biometricEnabled: Boolean = false,
    val pinEnabled: Boolean = false,
    /** Whether the app is currently allowed to be shown. Always true when neither lock method is on. */
    val isUnlocked: Boolean = false
)

/**
 * App-wide lock gate, held above the NavHost in MedRecallApp. Whenever
 * neither Biometric Lock nor a PIN is turned on in Settings > Security,
 * [AppLockUiState.isUnlocked] is always true and the app behaves as if
 * this didn't exist. When either is on, the app starts locked and
 * [relock] (wired to the Activity's ON_STOP) re-locks it every time
 * MedRecall+ goes to the background.
 */
class AppLockViewModel(private val repository: SettingsRepository) : ViewModel() {

    private val explicitlyUnlocked = MutableStateFlow(false)

    val uiState: StateFlow<AppLockUiState> = combine(
        repository.biometricLockEnabled,
        repository.pinEnabled,
        explicitlyUnlocked
    ) { biometric, pin, unlocked ->
        val lockRequired = biometric || pin
        AppLockUiState(
            isLoading = false,
            biometricEnabled = biometric,
            pinEnabled = pin,
            isUnlocked = unlocked || !lockRequired
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppLockUiState())

    fun markUnlocked() {
        explicitlyUnlocked.value = true
    }

    fun relock() {
        explicitlyUnlocked.value = false
    }

    suspend fun verifyPin(pin: String): Boolean = repository.verifyPin(pin)

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AppLockViewModel(SettingsRepository.getInstance(context)) as T
            }
        }
    }
}
