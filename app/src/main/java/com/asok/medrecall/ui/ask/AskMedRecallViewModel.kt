package com.asok.medrecall.ui.ask

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.asok.medrecall.data.local.MedRecallDatabase
import com.asok.medrecall.data.search.SearchRepository
import com.asok.medrecall.data.search.SearchResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private var debounceJob: Job? = null

    /**
     * Called on every keystroke. Live results are debounced so a fast typist
     * doesn't fire a fresh search per character, and anything under
     * [MIN_LIVE_QUERY_LENGTH] chars just waits for more input rather than
     * searching on 1 letter. An explicit submit (search icon/IME action,
     * [runSearch]) always runs immediately regardless of length.
     */
    fun onQueryChange(newQuery: String) {
        _uiState.value = _uiState.value.copy(query = newQuery)
        debounceJob?.cancel()

        val trimmed = newQuery.trim()
        if (trimmed.length < MIN_LIVE_QUERY_LENGTH) {
            searchJob?.cancel()
            _uiState.value = _uiState.value.copy(results = emptyList(), hasSearched = false, isSearching = false)
            return
        }

        debounceJob = viewModelScope.launch {
            delay(LIVE_SEARCH_DEBOUNCE_MS)
            runSearch()
        }
    }

    /** Runs a search for the current query text. Call from the search icon/IME action, or after the live-typing debounce settles. */
    fun runSearch() {
        debounceJob?.cancel()
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
        debounceJob?.cancel()
        searchJob?.cancel()
        _uiState.value = AskMedRecallUiState()
    }

    companion object {
        private const val MIN_LIVE_QUERY_LENGTH = 2
        private const val LIVE_SEARCH_DEBOUNCE_MS = 350L

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
