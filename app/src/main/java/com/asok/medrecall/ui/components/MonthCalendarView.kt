package com.asok.medrecall.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IconButton
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private const val MONTHS_BACK = 12
private const val MONTHS_FORWARD = 12
private const val PAGE_COUNT = MONTHS_BACK + MONTHS_FORWARD + 1
private const val START_PAGE = MONTHS_BACK // page index for the current month

private val ORDERED_WEEKDAYS = listOf(
    DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY
)

/**
 * A swipeable month-grid calendar: today's month centered, up to 12 months
 * back (appointment history) and 12 months forward (scheduling ahead).
 * [markedDates] gets a small dot under any day that has at least one
 * appointment; tapping a day calls [onDateSelected].
 */
@Composable
fun MonthCalendarView(
    selectedDate: LocalDate,
    markedDates: Set<LocalDate>,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = remember { LocalDate.now() }
    val baseMonth = remember { YearMonth.from(today) }
    val pagerState = rememberPagerState(initialPage = START_PAGE, pageCount = { PAGE_COUNT })
    val coroutineScope = rememberCoroutineScope()

    // Keep the visible month in sync if the selected date changes from
    // outside this component (e.g. jumping to a date in a different month).
    LaunchedEffect(selectedDate) {
        val targetPage = START_PAGE + monthsBetween(baseMonth, YearMonth.from(selectedDate))
        if (targetPage in 0 until PAGE_COUNT && targetPage != pagerState.currentPage) {
            pagerState.scrollToPage(targetPage)
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            val visibleMonth = baseMonth.plusMonths((pagerState.currentPage - START_PAGE).toLong())
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        coroutineScope.launch { pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0)) }
                    },
                    enabled = pagerState.currentPage > 0
                ) { Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month") }

                Text(
                    text = "${visibleMonth.month.getDisplayName(TextStyle.FULL, Locale.US)} ${visibleMonth.year}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                IconButton(
                    onClick = {
                        coroutineScope.launch { pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(PAGE_COUNT - 1)) }
                    },
                    enabled = pagerState.currentPage < PAGE_COUNT - 1
                ) { Icon(Icons.Default.ChevronRight, contentDescription = "Next month") }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                for (dayOfWeek in ORDERED_WEEKDAYS) {
                    Text(
                        text = dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.US),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
                val month = baseMonth.plusMonths((page - START_PAGE).toLong())
                MonthGrid(
                    month = month,
                    today = today,
                    selectedDate = selectedDate,
                    markedDates = markedDates,
                    onDateSelected = onDateSelected
                )
            }
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    selectedDate: LocalDate,
    markedDates: Set<LocalDate>,
    onDateSelected: (LocalDate) -> Unit
) {
    val firstOfMonth = month.atDay(1)
    val leadingBlanks = ORDERED_WEEKDAYS.indexOf(firstOfMonth.dayOfWeek)
    val daysInMonth = month.lengthOfMonth()
    val totalCells = leadingBlanks + daysInMonth
    val rows = (totalCells + 6) / 7

    Column(modifier = Modifier.padding(top = 4.dp)) {
        var day = 1
        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val cellIndex = row * 7 + col
                    if (cellIndex < leadingBlanks || day > daysInMonth) {
                        Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val date = month.atDay(day)
                        DayCell(
                            date = date,
                            isToday = date == today,
                            isSelected = date == selectedDate,
                            isMarked = date in markedDates,
                            onClick = { onDateSelected(date) },
                            modifier = Modifier.weight(1f).aspectRatio(1f)
                        )
                        day++
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isToday: Boolean,
    isSelected: Boolean,
    isMarked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val background = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val dotColor = if (isSelected) textColor else MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .padding(2.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = date.dayOfMonth.toString(), color = textColor, style = MaterialTheme.typography.bodyMedium)
            if (isMarked) {
                Box(
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
        }
    }
}

private fun monthsBetween(from: YearMonth, to: YearMonth): Int =
    (to.year - from.year) * 12 + (to.monthValue - from.monthValue)
