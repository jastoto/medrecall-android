package com.asok.medrecall.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** How many digits MedRecall's app-lock PIN is -- shared by setup and unlock. */
const val PIN_LENGTH = 4

/** A row of filled/empty dots showing how many of [PIN_LENGTH] digits have been entered. */
@Composable
fun PinDotsRow(enteredLength: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        repeat(PIN_LENGTH) { index ->
            val filled = index < enteredLength
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(
                        color = if (filled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape
                    )
            )
        }
    }
}

/**
 * A standard 0-9 numeric keypad with a backspace key, and an optional
 * fingerprint key in the bottom-left slot (pass null to leave that slot
 * blank -- used on the plain PIN-setup screen, which has nothing to fall
 * back to).
 */
@Composable
fun NumericKeypad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onBiometric: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val digitRows = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9')
    )
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        digitRows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                row.forEach { digit -> KeypadKey(label = digit.toString(), onClick = { onDigit(digit) }) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            KeypadIconSlot(onClick = onBiometric) {
                Icon(Icons.Default.Fingerprint, contentDescription = "Unlock with biometrics")
            }
            KeypadKey(label = "0", onClick = { onDigit('0') })
            KeypadIconSlot(onClick = onBackspace) {
                Icon(Icons.Default.Backspace, contentDescription = "Backspace")
            }
        }
    }
}

@Composable
private fun KeypadKey(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, style = MaterialTheme.typography.headlineMedium)
    }
}

/** A 64dp tappable slot for an icon key; renders empty (but still takes up its grid space) if [onClick] is null. */
@Composable
private fun KeypadIconSlot(onClick: (() -> Unit)?, icon: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        contentAlignment = Alignment.Center
    ) {
        if (onClick != null) icon()
    }
}
