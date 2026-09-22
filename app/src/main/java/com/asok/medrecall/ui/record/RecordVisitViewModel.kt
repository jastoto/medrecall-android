package com.asok.medrecall.ui.record

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.asok.medrecall.data.local.Doctor
import com.asok.medrecall.data.local.MedRecallDatabase
import com.asok.medrecall.data.local.Note
import com.asok.medrecall.data.repository.NoteRepository
import com.asok.medrecall.data.repository.PatientRepository
import com.asok.medrecall.data.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.io.File

data class RecordVisitUiState(
    val doctors: List<Doctor> = emptyList(),
    // Punch item #43 (note-organization rework): the patient name for the new
    // structured PDF note's header is pulled from the existing single-profile
    // Patient record (see PatientRepository) rather than typed in each time.
    val patientName: String? = null,
    // Punch item #43 follow-up (2026-09-21): recordings/notes now save
    // automatically to whichever cloud account(s) are already connected under
    // Settings > Account -- matching how iOS backs up -- instead of asking
    // Asok to pick a destination each time. These mirror
    // SettingsRepository.googleAccount.driveConnected / .microsoftAccount != null.
    val googleDriveConnected: Boolean = false,
    val oneDriveConnected: Boolean = false,
    val isLoading: Boolean = true
)

class RecordVisitViewModel(
    private val repository: NoteRepository,
    private val patientRepository: PatientRepository,
    private val settingsRepository: SettingsRepository,
    private val appContext: Context
) : ViewModel() {

    val uiState: StateFlow<RecordVisitUiState> =
        combine(
            repository.observeDoctors(),
            patientRepository.observePatient(),
            settingsRepository.googleAccount,
            settingsRepository.microsoftAccount
        ) { doctors, patient, googleAccount, microsoftAccount ->
            RecordVisitUiState(
                doctors = doctors.sortedBy { it.name },
                patientName = patient?.name,
                googleDriveConnected = googleAccount?.driveConnected ?: false,
                oneDriveConnected = microsoftAccount != null,
                isLoading = false
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = RecordVisitUiState()
        )

    // Plain suspend function (not fire-and-forget) so the screen can await the write
    // before clearing its transcript — same reasoning as the other feature ViewModels.
    suspend fun saveNote(note: Note): Long = repository.save(note)

    // ---- Audio recording (punch items #38/#39/#37) -------------------------------
    //
    // This lives alongside the existing SpeechRecognizer-based live transcription
    // (still owned by the screen) rather than replacing it: MediaRecorder captures
    // the actual audio continuously to one file, while SpeechRecognizer keeps
    // producing the editable text transcript in short stop/start bursts the way it
    // always has. Pausing only pauses MediaRecorder (same file, true pause/resume,
    // API 24+) and tells the screen to stop the current SpeechRecognizer session;
    // resuming un-pauses MediaRecorder and lets the screen start a fresh
    // SpeechRecognizer session that appends onto the existing transcript, same as
    // before. The two systems are independent, so a resumed recording doesn't need
    // to "reconnect" anything -- it just keeps writing to the same audio file and
    // keeps appending to the same transcript string.
    //
    // RISK FLAG: unverified against a real build/device, no compiler available here.
    // The specific thing to watch for on Asok's first test: some devices only allow
    // one component to hold the microphone at a time, and here MediaRecorder and
    // SpeechRecognizer are both requesting mic access concurrently (recording the
    // whole time, recognizing in bursts). If that conflicts on his Samsung phone
    // (e.g. MediaRecorder.start() or SpeechRecognizer.startListening() failing/
    // erroring while the other is active), the fix would be to route the live
    // transcript off the same recording (e.g. AudioRecord + on-device recognition)
    // instead of two independent mic consumers -- flag back if that's what happens.

    private var mediaRecorder: MediaRecorder? = null
    private var recordingFile: File? = null

    /**
     * Starts a brand-new recording, writing continuous AAC/M4A audio to a temp
     * file under the app's cache dir. Returns false (and leaves nothing running)
     * if MediaRecorder couldn't be set up -- caller should show an error rather
     * than flip into a "recording" UI state.
     */
    fun startAudioRecording(): Boolean {
        return try {
            val dir = File(appContext.cacheDir, "recordings").apply { mkdirs() }
            val file = File(dir, "visit_${System.currentTimeMillis()}.m4a")
            // Fix 2026-09-22, round 3 -- ABANDONED: tried to attach
            // AutomaticGainControl to MediaRecorder's own audio session two ways
            // (recorder.audioSessionId, then generating one via
            // AudioManager.generateAudioSessionId() + recorder.setAudioSessionId())
            // and BOTH produced real "Unresolved reference" build errors -- neither
            // method actually exists on MediaRecorder in this project's SDK.
            // Reverted rather than guess a third time; MediaRecorder just doesn't
            // give app code a supported way to attach audio effects to its session
            // pre-API 34. Volume now rests solely on the VOICE_COMMUNICATION source
            // switch below. RISK FLAG / NEXT: if that alone isn't loud enough
            // (Asok's last report before this attempt said it wasn't), the only
            // remaining lever discussed with him is #2 -- rewriting capture around
            // AudioRecord (which HAS always supported getting/attaching a session
            // id) plus manual sample gain, replacing MediaRecorder entirely. Bigger
            // change, not started -- revisit with Asok before beginning it.
            val recorder = newMediaRecorder().apply {
                // Fix 2026-09-23, round 3: reverted back to VOICE_RECOGNITION.
                // Round 2's VOICE_COMMUNICATION swap re-confirmed the SAME
                // regression the original volume saga already found: quieter and
                // muffled, this time even with confirmed-zero background noise
                // (TV muted) -- so it's a genuine property of this source on
                // Asok's phone, not a noise-driven illusion. VOICE_RECOGNITION is
                // now the settled choice for audio source -- it gave good voice
                // quality both times it's been tested; its only downside
                // (background noise pickup, since it's raw/unprocessed with no
                // suppression) is a separate problem to solve later, likely via a
                // software noise-reduction pass on the saved file rather than by
                // switching MediaRecorder sources again -- both processed options
                // (this one and VOICE_COMMUNICATION) have now been tried and both
                // hurt voice quality on this device's audio stack.
                setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128_000)
                setAudioSamplingRate(44_100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder
            recordingFile = file
            true
        } catch (e: Exception) {
            try { mediaRecorder?.release() } catch (inner: Exception) { /* best-effort */ }
            mediaRecorder = null
            recordingFile = null
            false
        }
    }

    /**
     * True pause -- MediaRecorder.pause() (API 24+, fine for this app's minSdk 26)
     * keeps writing to the SAME file on resume, it doesn't start a new one.
     * RISK FLAG: a handful of OEM audio stacks are known to throw here
     * unexpectedly; caught defensively so a failed pause just means "recording
     * keeps going" rather than losing audio already captured.
     */
    fun pauseAudioRecording(): Boolean = try {
        mediaRecorder?.pause()
        true
    } catch (e: Exception) {
        false
    }

    fun resumeAudioRecording(): Boolean = try {
        mediaRecorder?.resume()
        true
    } catch (e: Exception) {
        false
    }

    /**
     * Stops and releases the recorder, returning the finished audio file (or
     * null if nothing was recording, or if MediaRecorder threw on stop() --
     * which can happen if stop is called too soon after start/resume, in which
     * case there's no usable file). The file stays in the cache dir until the
     * screen's destination picker copies/uploads it to wherever Asok chooses;
     * call [discardAudioRecording] afterward once that's done (or if he backs
     * out without saving anywhere) to clean it up.
     */
    fun stopAudioRecording(): File? {
        val file = recordingFile
        val stoppedCleanly = try {
            mediaRecorder?.stop()
            true
        } catch (e: Exception) {
            false
        }
        try { mediaRecorder?.release() } catch (e: Exception) { /* best-effort */ }
        mediaRecorder = null
        recordingFile = null
        return if (stoppedCleanly) file else null
    }

    /** Deletes a temp file (recording or generated PDF note) once it's been saved everywhere Asok picked, or discarded without saving. */
    fun discardTempFile(file: File?) {
        file?.delete()
    }

    override fun onCleared() {
        super.onCleared()
        try { mediaRecorder?.release() } catch (e: Exception) { /* best-effort */ }
    }

    private fun newMediaRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(appContext)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val db = MedRecallDatabase.getInstance(context)
                val repo = NoteRepository(db.noteDao(), db.doctorDao())
                val patientRepo = PatientRepository(db.patientDao(), db.conditionDao(), db.medicationDao())
                val settingsRepo = SettingsRepository.getInstance(context)
                return RecordVisitViewModel(repo, patientRepo, settingsRepo, context.applicationContext) as T
            }
        }
    }
}
