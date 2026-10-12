package io.github.akrishna87.dayline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.akrishna87.dayline.model.Data
import io.github.akrishna87.dayline.model.Dates
import io.github.akrishna87.dayline.model.Recurrence
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** A month grid with a dot for each event, and the picked day's events under it. */
@Composable
fun MonthScreen(data: Data, padding: PaddingValues) {
    val nav = LocalNav.current
    val today = LocalClock.current.today
    val weekStart = Dates.weekStart(data.settings)
    val month = nav.month
    val grid = remember(month, weekStart) { Dates.monthGrid(month, weekStart) }
    val events = data.visibleEvents
    // Colours of what's on each day in the grid, worked out once for the whole grid.
    val dots = remember(events, data.calendars, grid) {
        val all = Recurrence.between(events, grid.first().first(), grid.last().last())
        grid.flatten().associateWith { day -> all.filter { it.covers(day) }.map { data.calendar(it.event.calendarId).color } }
    }
    val selected = nav.selected
    val onDay = remember(events, selected) { Recurrence.on(events, selected).sortedWith(Recurrence.dayOrder(selected)) }

    fun go(m: YearMonth) {
        nav.month = m
        nav.selected = if (m == YearMonth.from(today)) today else m.atDay(1)
    }

    Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                Dates.monthTitle(month, today),
                Modifier.weight(1f).semantics { contentDescription = "Showing ${DateTimeFormatter.ofPattern("MMMM yyyy").format(month)}" },
                style = MaterialTheme.typography.headlineMedium,
            )
            if (month != YearMonth.from(today)) TextButton(onClick = { go(YearMonth.from(today)) }) { Text("Today") }
            SearchAction { nav.push(Page.Search) }
            IconButton(onClick = { go(month.minusMonths(1)) }, Modifier.semantics { contentDescription = "Previous month" }) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null)
            }
            IconButton(onClick = { go(month.plusMonths(1)) }, Modifier.semantics { contentDescription = "Next month" }) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            for (letter in Dates.weekdayLetters(weekStart)) {
                Text(letter, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(
            Modifier
                .padding(horizontal = 8.dp)
                .pointerInput(month) {
                    // Swipe the grid sideways to change month.
                    var dx = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dx = 0f },
                        onDragEnd = {
                            if (dx < -120) go(month.plusMonths(1)) else if (dx > 120) go(month.minusMonths(1))
                        },
                    ) { _, amount -> dx += amount }
                },
        ) {
            for (week in grid) {
                Row(Modifier.fillMaxWidth()) {
                    for (day in week) {
                        DayCell(
                            day = day,
                            inMonth = YearMonth.from(day) == month,
                            isToday = day == today,
                            isSelected = day == selected,
                            colors = dots[day].orEmpty(),
                            modifier = Modifier.weight(1f),
                        ) {
                            nav.selected = day
                            if (YearMonth.from(day) != month) nav.month = YearMonth.from(day)
                        }
                    }
                }
            }
        }
        HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 88.dp)) {
            item {
                Text(
                    Dates.dayHeading(selected, today),
                    Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
            if (onDay.isEmpty()) {
                item {
                    Text(
                        "Nothing planned",
                        Modifier.fillMaxWidth().clickable { nav.newEvent(selected) }.padding(horizontal = 20.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(onDay, key = { it.event.id + it.day }) { o ->
                EventRow(o, selected, data) { nav.push(Page.EventPage(o.event.id, o.day)) }
            }
        }
    }
}

@Composable
private fun DayCell(day: LocalDate, inMonth: Boolean, isToday: Boolean, isSelected: Boolean, colors: List<Int>, modifier: Modifier, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val label = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).format(day) +
        if (colors.isEmpty()) "" else ", ${colors.size} event${if (colors.size == 1) "" else "s"}"
    Column(
        modifier
            .height(52.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = label
                selected = isSelected
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .padding(top = 4.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isToday -> primary
                        isSelected -> MaterialTheme.colorScheme.primaryContainer
                        else -> Color.Transparent
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                day.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isToday -> MaterialTheme.colorScheme.onPrimary
                    isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                    inMonth -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.outline
                },
            )
        }
        Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (c in colors.take(3)) Box(Modifier.size(5.dp).clip(CircleShape).background(Color(c).copy(alpha = if (inMonth) 1f else 0.5f)))
        }
    }
}

/** Everything coming up, day by day, for the next three months. */
@Composable
fun AgendaScreen(data: Data, padding: PaddingValues) {
    val nav = LocalNav.current
    val today = LocalClock.current.today
    val events = data.visibleEvents
    val days = remember(events, today) { Recurrence.agenda(events, today, AGENDA_DAYS) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 88.dp),
    ) {
        item {
            ScreenHeader("Agenda", subtitle = "The next three months") { SearchAction { nav.push(Page.Search) } }
        }
        if (days.isEmpty()) {
            item { EmptyState(Icons.Rounded.EventAvailable, "Nothing planned", "Events for the next three months show here. Tap + to add one.") }
        }
        for ((day, list) in days) {
            item(key = "h-$day") {
                SectionHeader(Dates.dayHeading(day, today), color = if (day == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
            items(list, key = { "$day-${it.event.id}-${it.day}" }) { o ->
                EventRow(o, day, data) { nav.push(Page.EventPage(o.event.id, o.day)) }
            }
            item(key = "s-$day") { Spacer(Modifier.height(4.dp)) }
        }
    }
}

const val AGENDA_DAYS = 92
