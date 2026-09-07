package com.asok.medrecall.ui.doctors

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.asok.medrecall.data.local.Appointment
import com.asok.medrecall.data.local.Doctor
import com.asok.medrecall.data.local.MedRecallDatabase
import com.asok.medrecall.data.repository.DoctorRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DoctorsUiState(
    val doctors: List<Doctor> = emptyList(),
    val upcomingAppointments: List<Appointment> = emptyList(),
    val isLoading: Boolean = true
)

/**
 * Note: save/delete/getDoctor are plain suspend functions (not wrapped in
 * viewModelScope.launch) so a screen can await them and only navigate away
 * once the write has actually landed in the database — same reasoning as
 * AppointmentsViewModel / MedicationsViewModel.
 */
class DoctorsViewModel(private val repository: DoctorRepository) : ViewModel() {

    val uiState: StateFlow<DoctorsUiState> =
        combine(
            repository.observeDoctors(),
            repository.observeUpcomingAppointments(System.currentTimeMillis())
        ) { doctors, appointments ->
            DoctorsUiState(
                doctors = doctors.sortedBy { it.name },
                upcomingAppointments = appointments.take(3),
                isLoading = false
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DoctorsUiState()
        )

    suspend fun saveDoctor(doctor: Doctor) = repository.save(doctor)

    suspend fun deleteDoctor(doctor: Doctor) = repository.delete(doctor)

    suspend fun getDoctor(id: Int): Doctor? = repository.getDoctor(id)

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val db = MedRecallDatabase.getInstance(context)
                val repo = DoctorRepository(db.doctorDao(), db.appointmentDao())
                return DoctorsViewModel(repo) as T
            }
        }
    }
}
