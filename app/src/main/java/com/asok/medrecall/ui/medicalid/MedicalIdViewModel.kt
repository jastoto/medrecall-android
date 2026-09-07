package com.asok.medrecall.ui.medicalid

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.asok.medrecall.data.local.Condition
import com.asok.medrecall.data.local.MedRecallDatabase
import com.asok.medrecall.data.local.Medication
import com.asok.medrecall.data.local.Patient
import com.asok.medrecall.data.repository.PatientRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class MedicalIdUiState(
    val patient: Patient? = null,
    val conditions: List<Condition> = emptyList(),
    val medications: List<Medication> = emptyList(),
    val isLoading: Boolean = true
) {
    // Only the ones the user has opted into showing on the card itself --
    // the edit screen still lists every condition so they can toggle this.
    val medicalIdConditions: List<Condition> get() = conditions.filter { it.includedInMedicalId }
}

/**
 * Same shape as DoctorsViewModel/MedicationsViewModel: a Repository-backed
 * StateFlow plus a manual ViewModelProvider.Factory (no DI framework in this
 * app). savePatient/toggleConditionIncluded are plain suspend functions so a
 * screen can await the write before navigating away or reacting.
 */
class MedicalIdViewModel(private val repository: PatientRepository) : ViewModel() {

    val uiState: StateFlow<MedicalIdUiState> =
        combine(
            repository.observePatient(),
            repository.observeConditions(),
            repository.observeActiveMedications()
        ) { patient, conditions, medications ->
            MedicalIdUiState(
                patient = patient,
                conditions = conditions,
                medications = medications,
                isLoading = false
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = MedicalIdUiState()
        )

    suspend fun savePatient(patient: Patient) = repository.save(patient)

    suspend fun setConditionIncluded(condition: Condition, included: Boolean) =
        repository.updateCondition(condition.copy(includedInMedicalId = included))

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val db = MedRecallDatabase.getInstance(context)
                val repo = PatientRepository(db.patientDao(), db.conditionDao(), db.medicationDao())
                return MedicalIdViewModel(repo) as T
            }
        }
    }
}
