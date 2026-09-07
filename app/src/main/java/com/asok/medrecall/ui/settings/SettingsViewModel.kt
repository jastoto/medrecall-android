package com.asok.medrecall.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.asok.medrecall.data.local.MedRecallDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.asok.medrecall.data.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val database: MedRecallDatabase
) : ViewModel() {

    val biometricLockEnabled: StateFlow<Boolean> = repository.biometricLockEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val pinEnabled: StateFlow<Boolean> = repository.pinEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setBiometricLockEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setBiometricLockEnabled(enabled) }
    }

    suspend fun setPin(pin: String) = repository.setPin(pin)

    fun clearPin() {
        viewModelScope.launch { repository.clearPin() }
    }

    /**
     * Wipes every Room table and every stored setting (lock/PIN included).
     * Runs on the ViewModel's own scope so the confirming screen can just
     * fire-and-forget and rely on [onDone] for navigation.
     */
    fun deleteAllData(onDone: () -> Unit) {
        viewModelScope.launch {
            // clearAllTables() is a synchronous, blocking call -- Room throws
            // "Cannot access database on the main thread" if it's run on the
            // ViewModel's default Main dispatcher, which crashed the app the
            // instant Delete All Data was tapped. Move it to IO explicitly.
            withContext(Dispatchers.IO) {
                database.clearAllTables()
            }
            repository.clearAll()
            onDone()
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val repo = SettingsRepository.getInstance(context)
                val db = MedRecallDatabase.getInstance(context)
                return SettingsViewModel(repo, db) as T
            }
        }
    }
}
