package com.asok.medrecall.ui.medicalid

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.asok.medrecall.data.local.Condition
import com.asok.medrecall.data.local.MedRecallDatabase
import com.asok.medrecall.data.local.Patient
import com.asok.medrecall.data.repository.PatientRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class MedicalIdUiState(
    val patient: Patient? = null,
    val conditions: List<Condition> = emptyList(),
    val isLoading: Boolean = true
)

/**
 * Same shape as DoctorsViewModel/MedicationsViewModel: a Repository-backed
 * StateFlow plus a manual ViewModelProvider.Factory (no DI framework in this
 * app). savePatient is a plain suspend function so the form screen can await
 * the write before navigating away.
 */
class MedicalIdViewModel(private val repository: PatientRepository) : ViewModel() {

    val uiState: StateFlow<MedicalIdUiState> =
        combine(
            repository.observePatient(),
            repository.observeConditions()
        ) { patient, conditions ->
            MedicalIdUiState(patient = patient, conditions = conditions, isLoading = false)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = MedicalIdUiState()
        )

    suspend fun savePatient(patient: Patient) = repository.save(patient)

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val db = MedRecallDatabase.getInstance(context)
                val repo = PatientRepository(db.patientDao(), db.conditionDao())
                return MedicalIdViewModel(repo) as T
            }
        }
    }
}
