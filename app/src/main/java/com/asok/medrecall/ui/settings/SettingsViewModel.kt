package com.asok.medrecall.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.asok.medrecall.data.calendar.DeviceCalendarInfo
import com.asok.medrecall.data.calendar.DeviceCalendarManager
import com.asok.medrecall.data.local.MedRecallDatabase
import com.asok.medrecall.data.repository.AppointmentRepository
import com.asok.medrecall.data.settings.SettingsRepository
import com.asok.medrecall.data.settings.SyncCalendarSelection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val database: MedRecallDatabase,
    private val appointmentRepository: AppointmentRepository
) : ViewModel() {

    val biometricLockEnabled: StateFlow<Boolean> = repository.biometricLockEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val pinEnabled: StateFlow<Boolean> = repository.pinEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val syncCalendar: StateFlow<SyncCalendarSelection?> = repository.syncCalendar
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _availableCalendars = MutableStateFlow<List<DeviceCalendarInfo>>(emptyList())
    val availableCalendars: StateFlow<List<DeviceCalendarInfo>> = _availableCalendars

    fun setBiometricLockEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setBiometricLockEnabled(enabled) }
    }

    suspend fun setPin(pin: String) = repository.setPin(pin)

    fun clearPin() {
        viewModelScope.launch { repository.clearPin() }
    }

    /** Re-reads the calendars available on this device (call right after permission is granted, or when opening the picker). */
    fun loadAvailableCalendars(context: Context) {
        _availableCalendars.value = DeviceCalendarManager.queryAvailableCalendars(context)
    }

    /**
     * Switches the sync target to [calendar]. When [detachExisting] is true,
     * every appointment's link to its old calendar event is cleared first --
     * those events stay put in the old calendar untouched, they just stop
     * being kept in sync. Only new saves after this point sync to [calendar].
     */
    fun applySyncCalendar(calendar: DeviceCalendarInfo, detachExisting: Boolean) {
        viewModelScope.launch {
            if (detachExisting) appointmentRepository.detachAllFromDeviceCalendar()
            repository.setSyncCalendar(calendar)
        }
    }

    /** Turns calendar sync off entirely. See [applySyncCalendar] for what [detachExisting] does. */
    fun turnOffSyncCalendar(detachExisting: Boolean) {
        viewModelScope.launch {
            if (detachExisting) appointmentRepository.detachAllFromDeviceCalendar()
            repository.clearSyncCalendar()
        }
    }

    /**
     * Wipes every Room table and every stored setting (lock/PIN/calendar-sync included).
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
                val appointmentRepo = AppointmentRepository(db.appointmentDao(), db.doctorDao())
                return SettingsViewModel(repo, db, appointmentRepo) as T
            }
        }
    }
}
