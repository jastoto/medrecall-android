package com.asok.medrecall.ui.appointments

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.asok.medrecall.data.calendar.DeviceCalendarManager
import com.asok.medrecall.data.local.Appointment
import com.asok.medrecall.data.local.Doctor
import com.asok.medrecall.data.local.MedRecallDatabase
import com.asok.medrecall.data.repository.AppointmentRepository
import com.asok.medrecall.data.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn

data class AppointmentsUiState(
    val appointments: List<Appointment> = emptyList(),
    val doctors: List<Doctor> = emptyList(),
    val isLoading: Boolean = true
)

/**
 * Note: save/delete/addDoctor/getAppointment are plain suspend functions
 * (not wrapped in viewModelScope.launch) so a screen can await them and only
 * navigate away once the write has actually landed in the database -- if we
 * fired-and-forgot inside viewModelScope, navigating away right after could
 * clear this ViewModel and cancel the write before it completed.
 */
class AppointmentsViewModel(
    private val repository: AppointmentRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val uiState: StateFlow<AppointmentsUiState> =
        combine(repository.observeAppointments(), repository.observeDoctors()) { appointments, doctors ->
            AppointmentsUiState(appointments = appointments, doctors = doctors, isLoading = false)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppointmentsUiState()
        )

    suspend fun saveAppointment(appointment: Appointment): Appointment = repository.save(appointment)

    suspend fun deleteAppointment(context: Context, appointment: Appointment) {
        appointment.deviceCalendarEventId?.let { DeviceCalendarManager.deleteEvent(context, it) }
        repository.delete(appointment)
    }

    suspend fun getAppointment(id: Int): Appointment? = repository.getAppointment(id)

    suspend fun addDoctor(doctor: Doctor): Int = repository.addDoctor(doctor).toInt()

    /**
     * Best-effort sync of one appointment to whichever device calendar the
     * user picked in Settings > Calendar Sync (see DeviceCalendarManager).
     * No-ops silently if no calendar is selected or calendar permission
     * isn't granted -- MedRecall's own Room record is always the source of
     * truth regardless of whether this succeeds.
     */
    suspend fun syncToDeviceCalendar(context: Context, appointment: Appointment, doctorName: String?) {
        val selection = settingsRepository.syncCalendar.first() ?: return
        if (!DeviceCalendarManager.hasPermission(context)) return

        val title = if (!doctorName.isNullOrBlank()) "${appointment.reason} — $doctorName" else appointment.reason
        val begin = appointment.dateTime
        val end = begin + DEFAULT_EVENT_DURATION_MILLIS
        val existingId = appointment.deviceCalendarEventId

        val eventId = if (existingId != null &&
            DeviceCalendarManager.updateEvent(context, existingId, selection.calendarId, title, appointment.notes, appointment.location, begin, end)
        ) {
            existingId
        } else {
            DeviceCalendarManager.insertEvent(context, selection.calendarId, title, appointment.notes, appointment.location, begin, end)
        }

        if (eventId != null && eventId != existingId) {
            repository.updateDeviceCalendarEventId(appointment.id, eventId)
        }
    }

    companion object {
        private const val DEFAULT_EVENT_DURATION_MILLIS = 30 * 60 * 1000L

        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val db = MedRecallDatabase.getInstance(context)
                val repo = AppointmentRepository(db.appointmentDao(), db.doctorDao())
                val settingsRepo = SettingsRepository.getInstance(context)
                return AppointmentsViewModel(repo, settingsRepo) as T
            }
        }
    }
}
