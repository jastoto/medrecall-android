package com.asok.medrecall.ui.health

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.asok.medrecall.data.local.CUSTOM_VITAL_TYPE
import com.asok.medrecall.data.local.Condition
import com.asok.medrecall.data.local.MedRecallDatabase
import com.asok.medrecall.data.local.VitalReading
import com.asok.medrecall.data.repository.ConditionRepository
import com.asok.medrecall.data.repository.VitalRepository
import com.asok.medrecall.ui.vitals.VitalType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** One featured type's readings, most-recent-first (empty if never logged). */
data class FeaturedTrend(val vitalType: VitalType, val readings: List<VitalReading>)

data class OtherTypeSummary(val vitalType: VitalType, val latest: VitalReading?)

data class HealthUiState(
    val featured: List<FeaturedTrend> = emptyList(),
    val otherTypes: List<OtherTypeSummary> = emptyList(),
    val customReadings: List<VitalReading> = emptyList(),
    val activeConditions: List<Condition> = emptyList(),
    val isLoading: Boolean = true
)

/**
 * Backs the new Health page (punch item #13). Reuses VitalRepository (the
 * same Room table Vitals writes to -- Asok's call to share data rather
 * than build a second reading system) and ConditionRepository for the
 * Active Conditions section, which per Asok's call just shows every
 * condition on file with no active/resolved filtering.
 */
class HealthViewModel(
    private val vitalRepository: VitalRepository,
    private val conditionRepository: ConditionRepository
) : ViewModel() {

    val uiState: StateFlow<HealthUiState> = run {
        val featuredFlows = healthPageFeaturedTypes.map { vitalRepository.observeReadings(it.id) }
        val otherFlows = healthPageOtherTypes.map { vitalRepository.observeReadings(it.id) }
        val customFlow = vitalRepository.observeReadings(CUSTOM_VITAL_TYPE)
        val conditionsFlow = conditionRepository.observeConditions()

        combine(
            combine(featuredFlows) { it },
            combine(otherFlows) { it },
            customFlow,
            conditionsFlow
        ) { featuredArray, otherArray, custom, conditions ->
            HealthUiState(
                featured = healthPageFeaturedTypes.mapIndexed { i, type -> FeaturedTrend(type, featuredArray[i]) },
                otherTypes = healthPageOtherTypes.mapIndexed { i, type -> OtherTypeSummary(type, otherArray[i].firstOrNull()) },
                customReadings = custom,
                activeConditions = conditions,
                isLoading = false
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HealthUiState()
        )
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val db = MedRecallDatabase.getInstance(context)
                val vitalRepo = VitalRepository(db.vitalReadingDao())
                val conditionRepo = ConditionRepository(db.conditionDao(), db.medicationDao(), db.doctorDao())
                return HealthViewModel(vitalRepo, conditionRepo) as T
            }
        }
    }
}
