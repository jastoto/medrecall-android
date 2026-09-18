package com.asok.medrecall.ui.appointments

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.asok.medrecall.data.calendar.GoogleCalendarService
import com.asok.medrecall.data.calendar.GoogleCalendarSyncManager
import com.asok.medrecall.data.local.Appointment
import com.asok.medrecall.data.local.Doctor
import com.asok.medrecall.data.local.MedRecallDatabase
import com.asok.medrecall.data.repository.AppointmentRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class AppointmentsUiState(
    val appointments: List<Appointment> = emptyList(),
    val doctors: List<Doctor> = emptyList(),
    val isLoading: Boolean = true
)

class AppointmentsViewModel(
    private val repository: AppointmentRepository,
    private val syncManager: GoogleCalendarSyncManager
) : ViewModel() {

    // "Active" = not marked Completed and not yet in the past. Once either
    // is true, an appointment drops off this list and shows up in
    // [pastAppointments] instead (see PastAppointmentsScreen).
    val uiState: StateFlow<AppointmentsUiState> =
        combine(repository.observeAppointments(), repository.observeDoctors()) { appointments, doctors ->
            val now = System.currentTimeMillis()
            val active = appointments.filter { !it.completed && it.dateTime >= now }
            AppointmentsUiState(appointments = active, doctors = doctors, isLoading = false)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppointmentsUiState()
        )

    /** Completed and/or past-dated appointments, newest first -- backs PastAppointmentsScreen. */
    val pastAppointments: StateFlow<List<Appointment>> =
        repository.observeAppointments()
            .map { appointments ->
                val now = System.currentTimeMillis()
                appointments
                    .filter { it.completed || it.dateTime < now }
                    .sortedByDescending { it.dateTime }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    // Saves to the local DB, then mirrors the change to Google Calendar if a
    // calendar has been connected (a no-op otherwise) -- see GoogleCalendarSyncManager.
    suspend fun saveAppointment(appointment: Appointment) {
        val saved = repository.save(appointment)
        syncManager.onAppointmentSaved(saved)
    }

    suspend fun deleteAppointment(appointment: Appointment) {
        repository.delete(appointment)
        syncManager.onAppointmentDeleted(appointment)
    }

    suspend fun getAppointment(id: Int): Appointment? = repository.getAppointment(id)

    suspend fun addDoctor(doctor: Doctor): Int = repository.addDoctor(doctor).toInt()

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val appContext = context.applicationContext
                val db = MedRecallDatabase.getInstance(appContext)
                val repo = AppointmentRepository(db.appointmentDao(), db.doctorDao())
                val syncManager = GoogleCalendarSyncManager(
                    GoogleCalendarService(appContext),
                    db.appointmentDao(),
                    db.doctorDao()
                )
                return AppointmentsViewModel(repo, syncManager) as T
            }
        }
    }
}
