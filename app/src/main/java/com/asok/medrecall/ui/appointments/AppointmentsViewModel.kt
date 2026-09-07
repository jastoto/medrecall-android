package com.asok.medrecall.ui.appointments

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.asok.medrecall.data.local.Appointment
import com.asok.medrecall.data.local.Doctor
import com.asok.medrecall.data.local.MedRecallDatabase
import com.asok.medrecall.data.repository.AppointmentRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class AppointmentsUiState(
    val appointments: List<Appointment> = emptyList(),
    val doctors: List<Doctor> = emptyList(),
    val isLoading: Boolean = true
)

/**
 * Note: save/delete/addDoctor/getAppointment are plain suspend functions
 * (not wrapped in viewModelScope.launch) so a screen can await them and only
 * navigate away once the write has actually landed in the database — if we
 * fired-and-forgot inside viewModelScope, navigating away right after could
 * clear this ViewModel and cancel the write before it completed.
 */
class AppointmentsViewModel(private val repository: AppointmentRepository) : ViewModel() {

    val uiState: StateFlow<AppointmentsUiState> =
        combine(repository.observeAppointments(), repository.observeDoctors()) { appointments, doctors ->
            AppointmentsUiState(appointments = appointments, doctors = doctors, isLoading = false)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppointmentsUiState()
        )

    suspend fun saveAppointment(appointment: Appointment) = repository.save(appointment)

    suspend fun deleteAppointment(appointment: Appointment) = repository.delete(appointment)

    suspend fun getAppointment(id: Int): Appointment? = repository.getAppointment(id)

    suspend fun addDoctor(doctor: Doctor): Int = repository.addDoctor(doctor).toInt()

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val db = MedRecallDatabase.getInstance(context)
                val repo = AppointmentRepository(db.appointmentDao(), db.doctorDao())
                return AppointmentsViewModel(repo) as T
            }
        }
    }
}
