package com.asok.medrecall.ui.record

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.asok.medrecall.data.transcription.NativeTranscriber
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.asok.medrecall.data.account.DriveAuthOutcome
import com.asok.medrecall.data.account.GoogleAccountManager
import com.asok.medrecall.data.account.MicrosoftAccountManager
import com.asok.medrecall.data.account.MicrosoftAuthOutcome
import com.asok.medrecall.data.local.Doctor
import com.asok.medrecall.data.local.Note
import com.asok.medrecall.data.recordings.DriveRecordingUploader
import com.asok.medrecall.data.recordings.LocalRecordingStorage
import com.asok.medrecall.data.recordings.OneDriveRecordingUploader
import com.asok.medrecall.data.recordings.RecordingDestination
import com.asok.medrecall.data.recordings.RecordingSaveResult
import com.asok.medrecall.data.recordings.VisitNoteData
import com.asok.medrecall.data.recordings.VisitNotePdfWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.coroutines.resume
import com.asok.medrecall.ui.components.MedRecallTopBar

/** Where the mic/ring visual + Stop Recording control are in, punch items #38/#39. */
private enum class RecordingPhase { IDLE, RECORDING, PAUSED }

/** One file to save alongside the visit -- the audio recording, and (per punch item #43) the structured PDF note. */
private data class RecordingArtifact(val file: File, val fileName: String, val mimeType: String)

/**
 * Stub for the on-device transcription piece (Asok's 2026-09-23 decision:
 * keep on-device, no cloud, run automatically the instant recording stops --
 * replacing the old live SpeechRecognizer approach, which turned out to
 * produce near-empty transcripts due to MediaRecorder/SpeechRecognizer mic
 * contention). NOT YET IMPLEMENTED: this always returns blank, so until the
 * real on-device engine (planned: whisper.cpp via NDK/JNI + a bundled ggml
 * model) is wired in, the visit note's Call Log/body will be empty and no
 * searchable Note gets saved. This is the project's first native/NDK
 * dependency, scoped as its own follow-up rather than bundled into this
 * flow-rework pass.
 */
private suspend fun transcribeAudioOnDevice(audioFile: File): String = ""

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordVisitScreen(
    onOpenCalendar: () -> Unit,
    onScheduleAppointment: () -> Unit,
    onOpenImport: () -> Unit,
    onAddDoctor: () -> Unit,
    onGoHome: () -> Unit,
    viewModel: RecordVisitViewModel = viewModel(factory = RecordVisitViewModel.factory(LocalContext.current))
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Toolchain-verification checkpoint (2026-09-24): confirms the new
    // native-bridge library actually loads and links on a real device/build,
    // not just that Gradle/CMake compiled it. Check Logcat for tag
    // "NativeTranscriber" after opening this screen -- "native ok" means the
    // whole Gradle -> CMake -> NDK -> JNI -> Kotlin pipeline works end to end,
    // clearing the way to build whisper.cpp on top of it. Remove this
    // LaunchedEffect once that's confirmed and whisper.cpp work begins.
    LaunchedEffect(Unit) {
        try {
            Log.d("NativeTranscriber", "nativeTestString() = ${NativeTranscriber.nativeTestString()}")
        } catch (e: Throwable) {
            Log.e("NativeTranscriber", "native-bridge failed to load/link", e)
        }
    }

    var doctorMenuExpanded by remember { mutableStateOf(false) }
    val doctorFieldInteractionSource = remember { MutableInteractionSource() }
    LaunchedEffect(doctorFieldInteractionSource) {
        doctorFieldInteractionSource.interactions.collectLatest { interaction ->
            if (interaction is PressInteraction.Release) {
                doctorMenuExpanded = true
            }
        }
    }
    var selectedDoctor by remember { mutableStateOf<Doctor?>(null) }
    // Holds the finished transcript once on-device transcription (see
    // transcribeAudioOnDevice) completes, for the PDF's Call Log and the
    // auto-saved Note -- there's no live/partial display anymore now that
    // transcription runs after Stop Recording rather than during it.
    var transcript by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    // ---- Recording state (punch items #38/#39/#37/#43) -----------------------------
    var phase by remember { mutableStateOf(RecordingPhase.IDLE) }
    var pendingAudioFile by remember { mutableStateOf<File?>(null) }
    var pendingFileStamp by remember { mutableStateOf("") }
    var pendingDoctorName by remember { mutableStateOf("") }
    var pendingDoctorId by remember { mutableStateOf<Int?>(null) }
    var pendingDateLabel by remember { mutableStateOf("") }
    var pendingTimeLabel by remember { mutableStateOf("") }
    // Fix 2026-09-23 (Asok's call): no more "Finish visit note" dialog -- the
    // audio saves the instant Stop Recording is tapped, then on-device
    // transcription + the PDF note + the searchable Note record all follow
    // automatically. This dialog is now purely a progress/result indicator
    // ("Backing up..." -> "Backup complete"), same as before, just no longer
    // gated behind Continue/Skip Note/Cancel first.
    var showSaveStatusDialog by remember { mutableStateOf(false) }
    var isSavingRecording by remember { mutableStateOf(false) }
    var stageLabel by remember { mutableStateOf("") }
    var saveResults by remember { mutableStateOf<List<RecordingSaveResult>?>(null) }
    // Holds the in-flight continuation while Google's own consent screen is up --
    // resumed from driveAuthLauncher's callback below. Same NeedsResolution
    // hand-off shape as AccountViewModel/BackupViewModel use for Drive sign-in.
    var driveAuthContinuation by remember { mutableStateOf<((DriveAuthOutcome) -> Unit)?>(null) }

    fun beginRecording() {
        val started = viewModel.startAudioRecording()
        if (!started) {
            statusMessage = "Couldn't start the microphone recording — try again."
            return
        }
        phase = RecordingPhase.RECORDING
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            beginRecording()
        } else {
            statusMessage = "Microphone permission is needed to record a visit."
        }
    }

    // Only needed on API 26-28 for LocalRecordingStorage's legacy file path --
    // API 29+ writes its own MediaStore entry without this permission.
    // retrySaveTrigger + the LaunchedEffect below re-run the auto-save once
    // permission is granted (saveRecordingAndNote is declared further down, so
    // this can't call it directly without a forward reference).
    var retrySaveTrigger by remember { mutableStateOf(0) }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            retrySaveTrigger++
        } else {
            statusMessage = "Storage permission is needed to save to this phone."
        }
    }

    val driveAuthLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val activity = context.findActivity()
        val outcome = if (activity != null) {
            GoogleAccountManager.handleAuthorizationResult(activity, result.data)
        } else {
            DriveAuthOutcome.Failed("Could not complete Google Drive authorization.")
        }
        driveAuthContinuation?.invoke(outcome)
        driveAuthContinuation = null
    }

    suspend fun getGoogleDriveToken(activity: Activity): String? {
        return when (val outcome = GoogleAccountManager.requestDriveAuthorization(activity)) {
            is DriveAuthOutcome.Authorized -> outcome.accessToken
            is DriveAuthOutcome.NeedsResolution -> {
                val resolved = suspendCancellableCoroutine<DriveAuthOutcome> { cont ->
                    driveAuthContinuation = { cont.resume(it) }
                    driveAuthLauncher.launch(IntentSenderRequest.Builder(outcome.intentSender).build())
                }
                (resolved as? DriveAuthOutcome.Authorized)?.accessToken
            }
            is DriveAuthOutcome.Failed -> null
        }
    }

    suspend fun getMicrosoftToken(activity: Activity): String? {
        val outcome = MicrosoftAccountManager.getAccessToken(activity)
        return (outcome as? MicrosoftAuthOutcome.Authorized)?.info?.accessToken
    }

    /**
     * Called once the whole backup flow below (audio save -> transcription ->
     * note save) finishes, or if it's cancelled early (e.g. no Activity to
     * save from) -- clears the selected doctor/status so the idle prompt reads
     * "Choose a Doctor to begin" again for the next recording.
     */
    fun resetForNextRecording() {
        selectedDoctor = null
        transcript = ""
        statusMessage = null
    }

    suspend fun saveArtifactToDestination(
        destination: RecordingDestination,
        artifact: RecordingArtifact,
        doctorFolderName: String,
        activity: Activity
    ): RecordingSaveResult = try {
        val location = when (destination) {
            RecordingDestination.LOCAL -> withContext(Dispatchers.IO) {
                LocalRecordingStorage.save(context, artifact.file, doctorFolderName, artifact.fileName, artifact.mimeType)
            }
            RecordingDestination.GOOGLE_DRIVE -> {
                val token = getGoogleDriveToken(activity)
                    ?: throw IllegalStateException("Couldn't connect to Google Drive.")
                withContext(Dispatchers.IO) {
                    DriveRecordingUploader.upload(token, doctorFolderName, artifact.fileName, artifact.file, artifact.mimeType)
                }
            }
            RecordingDestination.ONE_DRIVE -> {
                val token = getMicrosoftToken(activity)
                    ?: throw IllegalStateException("Couldn't connect to OneDrive.")
                withContext(Dispatchers.IO) {
                    OneDriveRecordingUploader.upload(token, doctorFolderName, artifact.fileName, artifact.file, artifact.mimeType)
                }
            }
        }
        RecordingSaveResult.Success(destination, location)
    } catch (e: Exception) {
        RecordingSaveResult.Failure(destination, e.message ?: "Something went wrong.")
    }

    /**
     * Fix 2026-09-23, replacing the old saveRecordingAndNote() + "Finish visit
     * note" dialog (Asok's call: no more manual Summary/Next Steps step for
     * now -- revisit later; save audio immediately, let the PDF/transcript
     * follow automatically; one continuous "Backing up..." -> "Backup
     * complete" status box). Saves the audio to whichever destinations are
     * connected (local always, plus Drive/OneDrive if connected) right away,
     * THEN runs on-device transcription (see transcribeAudioOnDevice --
     * currently a stub, Phase 2 not yet built), THEN builds and saves the
     * structured PDF (Summary/Next Steps left blank for now) and auto-saves
     * the transcript as a searchable Note -- all without Asok touching
     * anything after Stop Recording. Re-entrant guard (isSavingRecording)
     * kept from the old version -- same double-trigger risk applies.
     */
    fun runAutoBackupFlow(audioFile: File) {
        if (isSavingRecording) return
        val destinations = buildSet {
            add(RecordingDestination.LOCAL)
            if (uiState.googleDriveConnected) add(RecordingDestination.GOOGLE_DRIVE)
            if (uiState.oneDriveConnected) add(RecordingDestination.ONE_DRIVE)
        }
        val activity = context.findActivity()
        if (activity == null) {
            saveResults = destinations.map { RecordingSaveResult.Failure(it, "Couldn't access the app screen — try again.") }
            showSaveStatusDialog = true
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingAudioFile = audioFile
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }

        val doctorFolderName = pendingDoctorName
        val doctorId = pendingDoctorId
        val fileStamp = pendingFileStamp
        val patientName = uiState.patientName ?: "Patient"
        val dateLabel = pendingDateLabel
        val timeLabel = pendingTimeLabel

        isSavingRecording = true
        saveResults = null
        showSaveStatusDialog = true
        coroutineScope.launch {
            val results = mutableListOf<RecordingSaveResult>()

            // Stage 1: save the audio file right away -- doesn't wait on transcription.
            stageLabel = "Saving your recording…"
            val audioArtifact = RecordingArtifact(audioFile, "Visit_$fileStamp.m4a", "audio/mp4")
            for (destination in destinations) {
                results += saveArtifactToDestination(destination, audioArtifact, doctorFolderName, activity)
            }
            saveResults = results.toList()

            // Stage 2: on-device transcription (see transcribeAudioOnDevice --
            // stub for now, returns blank until Phase 2/whisper.cpp lands).
            stageLabel = "Transcribing on your phone…"
            val transcriptText = transcribeAudioOnDevice(audioFile)
            transcript = transcriptText

            // Stage 3: structured PDF (Summary/Next Steps blank for now, per
            // Asok's call) + searchable Note, both automatic.
            stageLabel = "Saving your visit note…"
            val noteData = VisitNoteData(
                patientName = patientName,
                doctorName = doctorFolderName,
                dateLabel = dateLabel,
                timeLabel = timeLabel,
                summary = "",
                callLog = transcriptText.trim(),
                nextSteps = ""
            )
            val pdfFile = VisitNotePdfWriter.write(context, noteData, fileStamp)
            val pdfArtifact = RecordingArtifact(pdfFile, "VisitNote_$fileStamp.pdf", "application/pdf")
            for (destination in destinations) {
                results += saveArtifactToDestination(destination, pdfArtifact, doctorFolderName, activity)
            }
            saveResults = results.toList()

            if (transcriptText.isNotBlank()) {
                viewModel.saveNote(
                    Note(
                        title = "Visit with $doctorFolderName – $dateLabel",
                        body = transcriptText.trim(),
                        createdAt = System.currentTimeMillis(),
                        doctorId = doctorId
                    )
                )
            }

            isSavingRecording = false
            viewModel.discardTempFile(audioFile)
            viewModel.discardTempFile(pdfFile)
            pendingAudioFile = null
            resetForNextRecording()
        }
    }

    fun onMicTapped() {
        when (phase) {
            RecordingPhase.IDLE -> {
                if (selectedDoctor == null) {
                    statusMessage = "Choose a Doctor to begin."
                    return
                }
                val hasPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (hasPermission) {
                    beginRecording()
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
            RecordingPhase.RECORDING -> {
                viewModel.pauseAudioRecording()
                phase = RecordingPhase.PAUSED
            }
            RecordingPhase.PAUSED -> {
                viewModel.resumeAudioRecording()
                phase = RecordingPhase.RECORDING
            }
        }
    }

    /**
     * Fix 2026-09-23 (Asok's call): stopping now immediately kicks off the
     * whole automatic backup flow -- no "Finish visit note" dialog in between
     * anymore. See runAutoBackupFlow below for what actually happens.
     */
    fun onStopRecordingTapped() {
        val file = viewModel.stopAudioRecording()
        phase = RecordingPhase.IDLE
        if (file != null) {
            statusMessage = "Recording has finished."
            val now = LocalDateTime.now()
            pendingFileStamp = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm"))
            pendingDoctorName = selectedDoctor?.name ?: "Unknown Doctor"
            pendingDoctorId = selectedDoctor?.id
            pendingDateLabel = now.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
            pendingTimeLabel = now.format(DateTimeFormatter.ofPattern("h:mm a"))
            runAutoBackupFlow(file)
        } else {
            statusMessage = "The recording couldn't be saved — try again."
        }
    }

    LaunchedEffect(retrySaveTrigger) {
        if (retrySaveTrigger > 0) {
            pendingAudioFile?.let { runAutoBackupFlow(it) }
        }
    }

    Scaffold(
        topBar = {
            MedRecallTopBar(
                title = "Record Visit",
                onGoHome = onGoHome
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = selectedDoctor?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        placeholder = { Text("Choose a doctor...") },
                        trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) },
                        interactionSource = doctorFieldInteractionSource,
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = doctorMenuExpanded && uiState.doctors.isNotEmpty(),
                        onDismissRequest = { doctorMenuExpanded = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        uiState.doctors.forEach { doctor ->
                            DropdownMenuItem(
                                text = { Text(doctor.name) },
                                onClick = {
                                    selectedDoctor = doctor
                                    doctorMenuExpanded = false
                                    statusMessage = null
                                }
                            )
                        }
                    }
                }
                FilledIconButton(
                    onClick = onAddDoctor,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add doctor")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RecordVisitTile(
                    label = "Calendar",
                    icon = Icons.Default.CalendarMonth,
                    background = Color(0xFFE3E3E8),
                    contentColor = Color(0xFF3A3A3C),
                    onClick = onOpenCalendar,
                    modifier = Modifier.weight(1f)
                )
                RecordVisitTile(
                    label = "Schedule",
                    icon = Icons.Default.EditCalendar,
                    background = Color(0xFFFCE8A8),
                    contentColor = Color(0xFF8A6100),
                    onClick = onScheduleAppointment,
                    modifier = Modifier.weight(1f)
                )
                RecordVisitTile(
                    label = "Import",
                    icon = Icons.Default.Download,
                    background = Color(0xFFAEDED6),
                    contentColor = Color(0xFF0F5B4D),
                    onClick = onOpenImport,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            val (outerColor, innerColor) = when (phase) {
                RecordingPhase.IDLE -> Color(0xFFFBDCDC) to Color(0xFFE0435A)
                RecordingPhase.RECORDING -> Color(0xFFDCEFE3) to Color(0xFF2E8B57)
                RecordingPhase.PAUSED -> Color(0xFFFCE8A8) to Color(0xFFC79100)
            }

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .background(outerColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(innerColor, CircleShape)
                            .clickable { onMicTapped() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = when (phase) {
                                RecordingPhase.IDLE -> "Start recording"
                                RecordingPhase.RECORDING -> "Pause recording"
                                RecordingPhase.PAUSED -> "Resume recording"
                            },
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }

            if (phase == RecordingPhase.PAUSED) {
                TextButton(
                    onClick = { onStopRecordingTapped() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Stop Recording", color = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Punch-list follow-up (2026-09-22, Asok): all status text under the mic
            // (idle prompt, paused/listening/finished/error states) bumped up a size
            // and to medium weight so it reads more clearly at a glance.
            val micStatusTextStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)

            when {
                statusMessage != null -> Text(
                    statusMessage.orEmpty(),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error,
                    style = micStatusTextStyle,
                    modifier = Modifier.fillMaxWidth()
                )
                phase == RecordingPhase.PAUSED -> Text(
                    "Paused — tap the mic to resume, or Stop Recording when you're done.",
                    textAlign = TextAlign.Center,
                    style = micStatusTextStyle,
                    modifier = Modifier.fillMaxWidth()
                )
                phase == RecordingPhase.RECORDING -> Text(
                    "Recording…",
                    textAlign = TextAlign.Center,
                    style = micStatusTextStyle,
                    modifier = Modifier.fillMaxWidth()
                )
                selectedDoctor == null -> Text(
                    "Choose a Doctor to begin",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = micStatusTextStyle,
                    modifier = Modifier.fillMaxWidth()
                )
                else -> Text(
                    "Tap the mic to start recording",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = micStatusTextStyle,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            // Fix 2026-09-23 (Asok's call): no more on-screen transcript field or
            // manual Save/Clear -- transcription, the PDF note, and the searchable
            // Note record all happen automatically after Stop Recording (see
            // runAutoBackupFlow). Nothing left to show or edit here.
        }
    }

    // Fix 2026-09-23 (Asok's call): the old "Finish visit note" dialog is gone
    // entirely -- Stop Recording now goes straight into the automatic backup
    // flow (see runAutoBackupFlow). This dialog is a pure progress/result
    // indicator: "Backing up..." with a stage label while it runs, "Backup
    // complete" with the per-destination checklist once it's done.
    if (showSaveStatusDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSavingRecording) showSaveStatusDialog = false },
            title = { Text(if (isSavingRecording) "Backing up…" else "Backup complete") },
            text = {
                Column {
                    if (isSavingRecording) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            Text(stageLabel, modifier = Modifier.padding(start = 8.dp))
                        }
                    } else {
                        saveResults.orEmpty().forEach { result ->
                            when (result) {
                                is RecordingSaveResult.Success -> Text("✓ ${result.destination.label}: ${result.locationLabel}")
                                is RecordingSaveResult.Failure -> Text(
                                    "✗ ${result.destination.label}: ${result.message}",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (!isSavingRecording) {
                    TextButton(onClick = { showSaveStatusDialog = false }) {
                        Text("Done")
                    }
                }
            }
        )
    }
}

/** Walks up the ContextWrapper chain Compose sometimes hands back to find the real Activity -- needed because GoogleAccountManager.requestDriveAuthorization/MicrosoftAccountManager.getAccessToken both require an Activity, not just a Context, since either may need to show a consent screen. */
private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
private fun RecordVisitTile(
    label: String,
    icon: ImageVector,
    background: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = background)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = contentColor)
            Text(label, color = contentColor, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
