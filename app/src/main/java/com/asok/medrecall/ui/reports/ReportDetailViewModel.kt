package com.asok.medrecall.ui.reports

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.asok.medrecall.data.local.MedRecallDatabase
import com.asok.medrecall.data.reports.ReportContentBuilder
import com.asok.medrecall.data.reports.ReportPdfWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ReportDetailUiState(
    val isGenerating: Boolean = false,
    val generatedFile: File? = null,
    val generatedAt: Long? = null,
    val error: String? = null
)

/**
 * Generates a PDF for one [ReportType] into the app's cache dir (see
 * ReportPdfWriter). View/Print and Share are handled by the screen itself
 * via the plain functions in ReportActions.kt, since those need a real
 * Activity/Application Context to launch system UI.
 */
class ReportDetailViewModel(
    private val appContext: Context,
    private val reportType: ReportType,
    private val db: MedRecallDatabase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportDetailUiState())
    val uiState: StateFlow<ReportDetailUiState> = _uiState.asStateFlow()

    fun generate() {
        if (_uiState.value.isGenerating) return
        _uiState.value = _uiState.value.copy(isGenerating = true, error = null)
        viewModelScope.launch {
            try {
                val document = withContext(Dispatchers.IO) {
                    ReportContentBuilder(db).build(reportType)
                }
                val file = withContext(Dispatchers.IO) {
                    ReportPdfWriter.write(appContext, reportType, document)
                }
                _uiState.value = ReportDetailUiState(
                    isGenerating = false,
                    generatedFile = file,
                    generatedAt = System.currentTimeMillis()
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    error = "Couldn't generate report: ${e.message}"
                )
            }
        }
    }

    fun regenerate() {
        _uiState.value = ReportDetailUiState()
    }

    companion object {
        fun factory(context: Context, reportType: ReportType): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val db = MedRecallDatabase.getInstance(context)
                    return ReportDetailViewModel(context.applicationContext, reportType, db) as T
                }
            }
    }
}
