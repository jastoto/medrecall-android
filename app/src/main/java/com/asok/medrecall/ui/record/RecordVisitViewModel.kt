package com.asok.medrecall.ui.record

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.asok.medrecall.data.local.Doctor
import com.asok.medrecall.data.local.MedRecallDatabase
import com.asok.medrecall.data.local.Note
import com.asok.medrecall.data.repository.NoteRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class RecordVisitUiState(
    val doctors: List<Doctor> = emptyList(),
    val isLoading: Boolean = true
)

class RecordVisitViewModel(private val repository: NoteRepository) : ViewModel() {

    val uiState: StateFlow<RecordVisitUiState> =
        repository.observeDoctors()
            .map { doctors -> RecordVisitUiState(doctors = doctors.sortedBy { it.name }, isLoading = false) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = RecordVisitUiState()
            )

    // Plain suspend function (not fire-and-forget) so the screen can await the write
    // before clearing its transcript — same reasoning as the other feature ViewModels.
    suspend fun saveNote(note: Note): Long = repository.save(note)

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val db = MedRecallDatabase.getInstance(context)
                val repo = NoteRepository(db.noteDao(), db.doctorDao())
                return RecordVisitViewModel(repo) as T
            }
        }
    }
}
