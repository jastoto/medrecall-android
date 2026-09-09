package com.asok.medrecall.ui.conditions

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.asok.medrecall.data.local.Condition
import com.asok.medrecall.data.local.Doctor
import com.asok.medrecall.data.local.Medication
import com.asok.medrecall.data.local.MedRecallDatabase
import com.asok.medrecall.data.repository.ConditionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ConditionsUiState(
    val conditions: List<Condition> = emptyList(),
    val medications: List<Medication> = emptyList(),
    val doctors: List<Doctor> = emptyList(),
    val isLoading: Boolean = true
)

/**
 * save/delete/getCondition are plain suspend functions (not wrapped in
 * viewModelScope.launch) so a screen can await the write before navigating
 * away -- same reasoning as the other feature ViewModels in this app.
 */
class ConditionsViewModel(private val repository: ConditionRepository) : ViewModel() {

    val uiState: StateFlow<ConditionsUiState> =
        combine(
            repository.observeConditions(),
            repository.observeMedications(),
            repository.observeDoctors()
        ) { conditions, medications, doctors ->
            ConditionsUiState(conditions = conditions, medications = medications, doctors = doctors, isLoading = false)
        }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = ConditionsUiState()
            )

    suspend fun saveCondition(condition: Condition) = repository.save(condition)

    suspend fun deleteCondition(condition: Condition) = repository.delete(condition)

    suspend fun getCondition(id: Int): Condition? = repository.getCondition(id)

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val db = MedRecallDatabase.getInstance(context)
                val repo = ConditionRepository(db.conditionDao(), db.medicationDao(), db.doctorDao())
                return ConditionsViewModel(repo) as T
            }
        }
    }
}
