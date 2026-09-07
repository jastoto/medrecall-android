package com.asok.medrecall.ui.vitals

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.asok.medrecall.data.health.HealthConnectManager
import com.asok.medrecall.data.local.MedRecallDatabase
import com.asok.medrecall.data.local.VitalReading
import com.asok.medrecall.data.repository.VitalRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VitalReadingsUiState(
    val readings: List<VitalReading> = emptyList(),
    val isLoading: Boolean = true
)

enum class HealthConnectStatus { CHECKING, NOT_INSTALLED, NEEDS_PERMISSION, CONNECTED }

/**
 * Scoped to a single VitalType (see factory) -- the detail/list screen and
 * the entry form for that type share one instance via Compose nav, same
 * shape as every other feature ViewModel in this app. Manual readings
 * (Room, via repository) and Health Connect readings are exposed as two
 * separate StateFlows -- VitalDetailScreen merges them for display, since
 * only the manual ones are editable. save/delete/getReading stay plain
 * suspend functions so a screen can await the write before navigating away.
 */
class VitalsViewModel(
    private val repository: VitalRepository,
    private val type: String,
    private val appContext: Context
) : ViewModel() {

    val uiState: StateFlow<VitalReadingsUiState> =
        repository.observeReadings(type)
            .map { readings -> VitalReadingsUiState(readings = readings, isLoading = false) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = VitalReadingsUiState()
            )

    private val _healthConnectStatus = MutableStateFlow(HealthConnectStatus.CHECKING)
    val healthConnectStatus: StateFlow<HealthConnectStatus> = _healthConnectStatus.asStateFlow()

    private val _healthConnectReadings = MutableStateFlow<List<VitalReadingDisplay>>(emptyList())
    val healthConnectReadings: StateFlow<List<VitalReadingDisplay>> = _healthConnectReadings.asStateFlow()

    fun refreshHealthConnect(vitalType: VitalType) {
        viewModelScope.launch {
            if (!HealthConnectManager.isAvailable(appContext)) {
                _healthConnectStatus.value = HealthConnectStatus.NOT_INSTALLED
                return@launch
            }
            val client = HealthConnectManager.getClient(appContext)
            if (!HealthConnectManager.hasPermission(client, vitalType)) {
                _healthConnectStatus.value = HealthConnectStatus.NEEDS_PERMISSION
                return@launch
            }
            _healthConnectStatus.value = HealthConnectStatus.CONNECTED
            _healthConnectReadings.value = HealthConnectManager.readReadings(client, vitalType)
        }
    }

    suspend fun saveReading(reading: VitalReading) = repository.save(reading)

    suspend fun deleteReading(reading: VitalReading) = repository.delete(reading)

    suspend fun getReading(id: Int): VitalReading? = repository.getReading(id)

    companion object {
        fun factory(context: Context, type: String): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val db = MedRecallDatabase.getInstance(context)
                val repo = VitalRepository(db.vitalReadingDao())
                return VitalsViewModel(repo, type, context.applicationContext) as T
            }
        }
    }
}
