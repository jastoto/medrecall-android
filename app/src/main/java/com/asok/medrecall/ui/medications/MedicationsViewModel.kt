package com.asok.medrecall.ui.medications

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.asok.medrecall.data.local.Condition
import com.asok.medrecall.data.local.Doctor
import com.asok.medrecall.data.local.Medication
import com.asok.medrecall.data.local.MedRecallDatabase
import com.asok.medrecall.data.repository.MedicationRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class MedicationsUiState(
    val medications: List<Medication> = emptyList(),
    val doctors: List<Doctor> = emptyList(),
    val conditions: List<Condition> = emptyList(),
    val isLoading: Boolean = true
)

/**
 * Note: save/delete/addDoctor/getMedication are plain suspend functions
 * (not wrapped in viewModelScope.launch) so a screen can await them and only
 * navigate away once the write has actually landed in the database — same
 * reasoning as AppointmentsViewModel.
 */
class MedicationsViewModel(private val repository: MedicationRepository) : ViewModel() {

    val uiState: StateFlow<MedicationsUiState> =
        combine(
            repository.observeMedications(),
            repository.observeDoctors(),
            repository.observeConditions()
        ) { medications, doctors, conditions ->
            MedicationsUiState(medications = medications, doctors = doctors, conditions = conditions, isLoading = false)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = MedicationsUiState()
        )

    suspend fun saveMedication(medication: Medication) = repository.save(medication)

    suspend fun deleteMedication(medication: Medication) = repository.delete(medication)

    suspend fun getMedication(id: Int): Medication? = repository.getMedication(id)

    suspend fun addDoctor(doctor: Doctor): Int = repository.addDoctor(doctor).toInt()

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val db = MedRecallDatabase.getInstance(context)
                val repo = MedicationRepository(db.medicationDao(), db.doctorDao(), db.conditionDao())
                return MedicationsViewModel(repo) as T
            }
        }
    }
}
