package com.asok.medrecall.ui.lock

import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.asok.medrecall.ui.settings.NumericKeypad
import com.asok.medrecall.ui.settings.PIN_LENGTH
import com.asok.medrecall.ui.settings.PinDotsRow
import kotlinx.coroutines.launch

/**
 * Full-screen gate shown instead of the app whenever Settings > Security
 * has Biometric Lock and/or a PIN turned on. Biometric is offered first
 * (a system prompt pops up automatically) when it's enabled; the PIN, if
 * also set, is always reachable as a fallback so a failed or unavailable
 * biometric read never locks Asok out entirely.
 *
 * Requires the hosting Activity to be a FragmentActivity -- BiometricPrompt
 * needs one to attach its dialog fragment to (see MainActivity).
 */
@Composable
fun LockScreen(
    uiState: AppLockUiState,
    onVerifyPin: suspend (String) -> Boolean,
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showPinEntry by remember { mutableStateOf(!uiState.biometricEnabled) }
    var pinEntry by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    fun promptBiometric() {
        val activity = context as? FragmentActivity ?: return
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock MedRecall+")
            .setAllowedAuthenticators(BIOMETRIC_STRONG or BIOMETRIC_WEAK)
            .setNegativeButtonText(if (uiState.pinEnabled) "Use PIN" else "Cancel")
            .build()
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onUnlocked()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (uiState.pinEnabled) showPinEntry = true
                }

                override fun onAuthenticationFailed() {
                    // The system prompt itself shows the "try again" feedback; nothing to do here.
                }
            }
        )
        prompt.authenticate(promptInfo)
    }

    // Pop the biometric prompt automatically as soon as the lock screen appears.
    LaunchedEffect(uiState.biometricEnabled) {
        if (uiState.biometricEnabled) promptBiometric()
    }

    val biometricTap: (() -> Unit)? = if (uiState.biometricEnabled) {
        { promptBiometric() }
    } else null

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(Icons.Default.Lock, contentDescription = null)
            Text("MedRecall+ is locked", style = MaterialTheme.typography.headlineSmall)

            if (showPinEntry && uiState.pinEnabled) {
                PinDotsRow(enteredLength = pinEntry.length)
                if (pinError) {
                    Text("Incorrect PIN", color = MaterialTheme.colorScheme.error)
                }
                NumericKeypad(
                    onDigit = { digit ->
                        if (pinEntry.length < PIN_LENGTH) {
                            pinError = false
                            pinEntry += digit
                            if (pinEntry.length == PIN_LENGTH) {
                                val attempt = pinEntry
                                scope.launch {
                                    if (onVerifyPin(attempt)) {
                                        onUnlocked()
                                    } else {
                                        pinError = true
                                        pinEntry = ""
                                    }
                                }
                            }
                        }
                    },
                    onBackspace = { if (pinEntry.isNotEmpty()) pinEntry = pinEntry.dropLast(1) },
                    onBiometric = biometricTap
                )
            } else if (uiState.biometricEnabled) {
                Button(onClick = { promptBiometric() }) {
                    Text("Unlock")
                }
                if (uiState.pinEnabled) {
                    TextButton(onClick = { showPinEntry = true }) {
                        Text("Use PIN instead")
                    }
                }
            }
        }
    }
}
