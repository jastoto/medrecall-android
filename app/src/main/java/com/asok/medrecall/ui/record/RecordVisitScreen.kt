package com.asok.medrecall.ui.record

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.asok.medrecall.data.local.Doctor
import com.asok.medrecall.data.local.Note
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

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
    var transcript by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var justSaved by remember { mutableStateOf(false) }

    val recognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }
    }

    DisposableEffect(Unit) {
        onDispose { recognizer?.destroy() }
    }

    fun startListening() {
        val speechRecognizer = recognizer
        if (speechRecognizer == null) {
            statusMessage = "Speech recognition isn't available on this device."
            return
        }
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListening = false }
            override fun onError(error: Int) {
                isListening = false
                statusMessage = "Didn't catch that — tap the mic to try again."
            }
            override fun onResults(results: Bundle?) {
                isListening = false
                val heard = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!heard.isNullOrBlank()) {
                    transcript = if (transcript.isBlank()) heard else "$transcript $heard"
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        }
        statusMessage = null
        isListening = true
        speechRecognizer.startListening(intent)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startListening()
        } else {
            statusMessage = "Microphone permission is needed to record a visit."
        }
    }

    fun onMicTapped() {
        if (selectedDoctor == null) {
            statusMessage = "Choose a doctor above to begin."
            return
        }
        if (isListening) {
            recognizer?.stopListening()
            isListening = false
            return
        }
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            startListening()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(justSaved) {
        if (justSaved) {
            delay(2000)
            justSaved = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Record Visit") },
                navigationIcon = {
                    IconButton(onClick = onGoHome) {
                        Icon(Icons.Default.Home, contentDescription = "Home")
                    }
                }
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

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .background(Color(0xFFFBDCDC), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(
                                if (isListening) Color(0xFFB71C2B) else Color(0xFFE0435A),
                                CircleShape
                            )
                            .clickable { onMicTapped() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = if (isListening) "Stop recording" else "Start recording",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when {
                justSaved -> Text(
                    "Visit note saved.",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                statusMessage != null -> Text(
                    statusMessage.orEmpty(),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth()
                )
                isListening -> Text(
                    "Listening…",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                transcript.isBlank() && selectedDoctor == null -> Text(
                    "Choose a doctor above to begin",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
                transcript.isBlank() -> Text(
                    "Tap the mic to start recording",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (transcript.isNotBlank()) {
                OutlinedTextField(
                    value = transcript,
                    onValueChange = { transcript = it },
                    label = { Text("Visit notes") },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    minLines = 5
                )
                Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Button(
                        onClick = {
                            val doctor = selectedDoctor
                            if (doctor != null) {
                                coroutineScope.launch {
                                    val dateLabel = LocalDate.now().format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
                                    viewModel.saveNote(
                                        Note(
                                            title = "Visit with ${doctor.name} – $dateLabel",
                                            body = transcript.trim(),
                                            createdAt = System.currentTimeMillis(),
                                            doctorId = doctor.id
                                        )
                                    )
                                    transcript = ""
                                    justSaved = true
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = selectedDoctor != null && transcript.isNotBlank()
                    ) {
                        Text("Save")
                    }
                    TextButton(
                        onClick = { transcript = "" },
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text("Clear")
                    }
                }
            }
        }
    }
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
