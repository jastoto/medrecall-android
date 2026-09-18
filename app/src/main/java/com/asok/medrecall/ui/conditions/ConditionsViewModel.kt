package com.asok.medrecall.ui.conditions

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.asok.medrecall.data.local.Condition
import com.asok.medrecall.data.local.Doctor
import com.asok.medrecall.data.local.MedRecallDatabase
import com.asok.medrecall.data.repository.ConditionRepository
import com.asok.medrecall.data.repository.DoctorRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ConditionsUiState(
    val conditions: List<Condition> = emptyList(),
    val doctors: List<Doctor> = emptyList(),
    val isLoading: Boolean = true
)

/**
 * save/delete/getCondition are plain suspend functions (not wrapped in
 * viewModelScope.launch) so a screen can await the write before navigating
 * away -- same reasoning as the other feature ViewModels in this app.
 *
 * Also pulls in DoctorRepository (read-only list + addDoctor) so
 * ConditionFormScreen can offer the same "pick an existing doctor or type
 * a new one" field that MedicationFormScreen already has.
 */
class ConditionsViewModel(
    private val repository: ConditionRepository,
    private val doctorRepository: DoctorRepository
) : ViewModel() {

    val uiState: StateFlow<ConditionsUiState> =
        combine(repository.observeConditions(), doctorRepository.observeDoctors()) { conditions, doctors ->
            ConditionsUiState(conditions = conditions, doctors = doctors, isLoading = false)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ConditionsUiState()
        )

    suspend fun saveCondition(condition: Condition) = repository.save(condition)

    suspend fun deleteCondition(condition: Condition) = repository.delete(condition)

    suspend fun getCondition(id: Int): Condition? = repository.getCondition(id)

    suspend fun addDoctor(doctor: Doctor): Int = doctorRepository.addDoctor(doctor).toInt()

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val db = MedRecallDatabase.getInstance(context)
                val repo = ConditionRepository(db.conditionDao())
                val doctorRepo = DoctorRepository(db.doctorDao(), db.appointmentDao())
                return ConditionsViewModel(repo, doctorRepo) as T
            }
        }
    }
}
