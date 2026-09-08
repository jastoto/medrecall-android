package com.asok.medrecall.ui.ask

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.asok.medrecall.data.local.MedRecallDatabase
import com.asok.medrecall.data.search.SearchRepository
import com.asok.medrecall.data.search.SearchResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * hasSearched distinguishes "never searched yet" (show the intro prompt)
 * from "searched, found nothing" (show the no-results message) -- both
 * states have an empty [results] list, so a flag is needed on top of it.
 */
data class AskMedRecallUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false
)

class AskMedRecallViewModel(private val repository: SearchRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AskMedRecallUiState())
    val uiState: StateFlow<AskMedRecallUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(newQuery: String) {
        _uiState.value = _uiState.value.copy(query = newQuery)
    }

    /** Runs a search for the current query text. Call from the search icon/IME action. */
    fun runSearch() {
        val query = _uiState.value.query.trim()
        searchJob?.cancel()
        if (query.isEmpty()) {
            _uiState.value = _uiState.value.copy(results = emptyList(), hasSearched = false, isSearching = false)
            return
        }
        searchJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearching = true)
            val results = repository.search(query)
            _uiState.value = _uiState.value.copy(
                results = results,
                isSearching = false,
                hasSearched = true
            )
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.value = AskMedRecallUiState()
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val db = MedRecallDatabase.getInstance(context)
                val repo = SearchRepository(
                    doctorDao = db.doctorDao(),
                    medicationDao = db.medicationDao(),
                    conditionDao = db.conditionDao(),
                    appointmentDao = db.appointmentDao(),
                    noteDao = db.noteDao(),
                    vitalReadingDao = db.vitalReadingDao(),
                    patientDao = db.patientDao()
                )
                return AskMedRecallViewModel(repo) as T
            }
        }
    }
}
