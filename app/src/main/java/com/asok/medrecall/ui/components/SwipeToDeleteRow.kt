package com.asok.medrecall.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Shared swipe-to-delete wrapper used by list rows (Conditions, Medications)
 * that support "swipe left to remove". Swiping end-to-start (right-to-left,
 * the natural delete gesture) reveals a red trash background and calls
 * [onDelete] as soon as the swipe commits.
 *
 * [onDelete] is expected to be immediate-with-undo, not a hard delete: the
 * caller should remove the item from the visible list right away and show
 * an "Undo" snackbar, only actually deleting from the database if the
 * snackbar times out without Undo being tapped (see ConditionsScreen /
 * MedicationsScreen). This composable itself has no confirmation dialog
 * and does not know whether the delete is later undone -- it only reports
 * the gesture.
 *
 * Swiping the other direction (start-to-end) is disabled so a stray swipe
 * while scrolling horizontally-adjacent content doesn't also trigger it.
 *
 * [content] is wrapped in an opaque background ([contentBackgroundColor])
 * before being laid over [backgroundContent]. This matters: SwipeToDismissBox
 * only ever draws the background layer *behind* content and lets content's
 * own translation reveal it -- if content isn't fully opaque, the red trash
 * background shows through any empty/transparent space in content (e.g. the
 * gap to the right of a short row) even at rest, before any swipe happens.
 * Default matches the app's Card container color so rows sit flush inside
 * the Card groups used on Conditions/Medications with no visible seam.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDeleteRow(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    contentBackgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                // Returning true would leave the box visually "dismissed"
                // (fully swiped away) even though the caller hasn't
                // actually removed the item from the list yet. The caller
                // is expected to filter the item out on its next
                // recomposition instead, so this composable is discarded
                // rather than left in a stuck half-swiped state.
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFD32F2F))
                    .padding(end = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxWidth().background(contentBackgroundColor)) {
            content()
        }
    }
}
