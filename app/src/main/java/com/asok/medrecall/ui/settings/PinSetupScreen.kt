package com.asok.medrecall.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.asok.medrecall.ui.components.MedRecallTopBar

private enum class PinStep { ENTER_NEW, CONFIRM_NEW }

/**
 * Create/change/remove the app's local unlock PIN. This screen only ever
 * writes a salted hash (see SettingsRepository) -- the raw PIN never
 * leaves this composable's own state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinSetupScreen(
    onDone: () -> Unit,
    onGoHome: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(LocalContext.current))
) {
    val pinEnabled by viewModel.pinEnabled.collectAsState()
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(PinStep.ENTER_NEW) }
    var firstEntry by remember { mutableStateOf("") }
    var currentEntry by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showRemoveConfirm by remember { mutableStateOf(false) }

    fun reset() {
        step = PinStep.ENTER_NEW
        firstEntry = ""
        currentEntry = ""
    }

    fun onDigit(digit: Char) {
        if (currentEntry.length >= PIN_LENGTH) return
        errorMessage = null
        currentEntry += digit
        if (currentEntry.length == PIN_LENGTH) {
            when (step) {
                PinStep.ENTER_NEW -> {
                    firstEntry = currentEntry
                    currentEntry = ""
                    step = PinStep.CONFIRM_NEW
                }
                PinStep.CONFIRM_NEW -> {
                    if (currentEntry == firstEntry) {
                        val newPin = currentEntry
                        scope.launch {
                            viewModel.setPin(newPin)
                            onDone()
                        }
                    } else {
                        errorMessage = "PINs didn't match -- try again."
                        reset()
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            MedRecallTopBar(
                title = "Backup PIN",
                onGoHome = onGoHome
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                Text(
                    text = when (step) {
                        PinStep.ENTER_NEW -> "Enter a new PIN"
                        PinStep.CONFIRM_NEW -> "Confirm your new PIN"
                    },
                    style = MaterialTheme.typography.titleMedium
                )
                PinDotsRow(enteredLength = currentEntry.length)
                errorMessage?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                NumericKeypad(
                    onDigit = ::onDigit,
                    onBackspace = { if (currentEntry.isNotEmpty()) currentEntry = currentEntry.dropLast(1) },
                    onBiometric = null
                )
                if (pinEnabled) {
                    TextButton(onClick = { showRemoveConfirm = true }) {
                        Text("Remove PIN")
                    }
                }
            }
        }
    }

    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = { Text("Remove PIN?") },
            text = { Text("You won't be able to unlock MedRecall+ with a PIN anymore.") },
            confirmButton = {
                TextButton(onClick = {
                    showRemoveConfirm = false
                    viewModel.clearPin()
                    onDone()
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
