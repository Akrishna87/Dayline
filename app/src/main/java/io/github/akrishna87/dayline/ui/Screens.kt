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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.ViewDay
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import io.github.akrishna87.dayline.Store
import io.github.akrishna87.dayline.model.CalendarView
import io.github.akrishna87.dayline.model.Data
import io.github.akrishna87.dayline.model.Dates
import io.github.akrishna87.dayline.model.Recurrence
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The views you can switch between on the Calendar tab, as in Google Calendar. */
val GRID_VIEWS = listOf(CalendarView.DAY, CalendarView.THREE_DAYS, CalendarView.WEEK, CalendarView.MONTH)

/** The days a day, 3-day or week view shows around [selected]. */
fun daysFor(view: CalendarView, selected: LocalDate, data: Data): List<LocalDate> = when (view) {
    CalendarView.DAY -> listOf(selected)
    CalendarView.THREE_DAYS -> (0L..2L).map { selected.plusDays(it) }
    else -> {
        val start = Dates.startOfWeek(selected, Dates.weekStart(data.settings))
        (0L..6L).map { start.plusDays(it) }.filter { data.settings.showWeekends || (it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY) }
    }
}

/** The Calendar tab: a header to switch views and move around, then the day, 3-day, week or month view. */
@Composable
fun CalendarScreen(data: Data, padding: PaddingValues) {
    val nav = LocalNav.current
    val today = LocalClock.current.today
    val view = nav.view
    var viewMenu by remember { mutableStateOf(false) }
    var jumping by remember { mutableStateOf(false) }
    val days = if (view == CalendarView.MONTH) emptyList() else daysFor(view, nav.selected, data)
    val shown = if (view == CalendarView.MONTH) nav.month else YearMonth.from(days.firstOrNull() ?: nav.selected)

    fun move(by: Long) {
        when (view) {
            CalendarView.MONTH -> {
                val m = nav.month.plusMonths(by)
                nav.month = m
                nav.selected = if (m == YearMonth.from(today)) today else m.atDay(1)
            }
            CalendarView.DAY -> nav.select(nav.selected.plusDays(by))
            CalendarView.THREE_DAYS -> nav.select(nav.selected.plusDays(3 * by))
            else -> nav.select(nav.selected.plusWeeks(by))
        }
    }

    Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).clip(MaterialTheme.shapes.small).clickable { jumping = true }
                    .semantics { contentDescription = "Showing ${DateTimeFormatter.ofPattern("MMMM yyyy").format(shown)}. Jump to a date" }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(Dates.monthTitle(shown, today), style = MaterialTheme.typography.headlineSmall, maxLines = 1)
                Icon(Icons.Rounded.ArrowDropDown, null)
            }
            if (!(nav.selected == today && (view != CalendarView.MONTH || nav.month == YearMonth.from(today)))) {
                TextButton(onClick = { nav.select(today); nav.month = YearMonth.from(today) }) { Text("Today") }
            }
            Box {
                IconButton(onClick = { viewMenu = true }, Modifier.semantics { contentDescription = "View: ${view.label}" }) { Icon(Icons.Rounded.ViewDay, null) }
                DropdownMenu(expanded = viewMenu, onDismissRequest = { viewMenu = false }) {
                    for (v in GRID_VIEWS) {
                        DropdownMenuItem(
                            text = { Text(v.label) },
                            onClick = {
                                viewMenu = false
                                nav.view = v
                                if (v == CalendarView.MONTH) nav.month = YearMonth.from(nav.selected)
                                Store.updateSettings { it.copy(view = v) }
                            },
                            trailingIcon = { if (v == view) Icon(Icons.Rounded.Check, null) },
                        )
                    }
                }
            }
            SearchAction { nav.push(Page.Search) }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp).pointerInput(view, nav.selected, nav.month) {
                var dx = 0f
                detectHorizontalDragGestures(onDragStart = { dx = 0f }, onDragEnd = { if (dx < -100) move(1) else if (dx > 100) move(-1) }) { _, a -> dx += a }
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { move(-1) }, Modifier.semantics { contentDescription = if (view == CalendarView.MONTH) "Previous month" else "Previous" }) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null)
            }
            Text(
                when (view) {
                    CalendarView.MONTH -> ""
                    CalendarView.DAY -> Dates.dayHeading(nav.selected, today)
                    else -> days.firstOrNull()?.let { f -> DateTimeFormatter.ofPattern("d MMM").format(f) + " – " + DateTimeFormatter.ofPattern("d MMM").format(days.last()) } ?: ""
                },
                Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = { move(1) }, Modifier.semantics { contentDescription = if (view == CalendarView.MONTH) "Next month" else "Next" }) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
            }
        }
        if (view == CalendarView.MONTH) {
            MonthBody(data, ::move)
        } else {
            Box(Modifier.weight(1f)) { TimeGrid(days, data) }
        }
    }
    if (jumping) DatePickDialog(nav.selected, onPick = { nav.select(it); nav.month = YearMonth.from(it) }, onDismiss = { jumping = false })
}

/** A month grid with a dot for each event, and the picked day's events under it. */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.MonthBody(data: Data, move: (Long) -> Unit) {
    val nav = LocalNav.current
    val today = LocalClock.current.today
    val weekStart = Dates.weekStart(data.settings)
    val month = nav.month
    val grid = remember(month, weekStart) { Dates.monthGrid(month, weekStart) }
    val events = data.visibleEvents
    // Colours of what's on each day in the grid, worked out once for the whole grid.
    val dots = remember(events, data.calendars, grid) {
        val all = Recurrence.between(events, grid.first().first(), grid.last().last())
        grid.flatten().associateWith { day -> all.filter { it.covers(day) }.map { data.colorOf(it.event) } }
    }
    val selected = nav.selected
    val onDay = remember(events, selected) { Recurrence.on(events, selected) }

    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        if (data.settings.showWeekNumbers) Spacer(Modifier.width(24.dp))
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
                    onDragEnd = { if (dx < -120) move(1) else if (dx > 120) move(-1) },
                ) { _, amount -> dx += amount }
            },
    ) {
        for (week in grid) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (data.settings.showWeekNumbers) {
                    Text(Dates.weekNumber(week.first()).toString(), Modifier.width(24.dp), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.Center)
                }
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

/** The Schedule: everything coming up, day by day, for the next three months (or from a date you jump to). */
@Composable
fun ScheduleScreen(data: Data, padding: PaddingValues) {
    val nav = LocalNav.current
    val today = LocalClock.current.today
    var from by remember { mutableStateOf<LocalDate?>(null) }
    var jumping by remember { mutableStateOf(false) }
    val start = from ?: today
    val events = data.visibleEvents
    val days = remember(events, start) { Recurrence.agenda(events, start, AGENDA_DAYS) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 88.dp),
    ) {
        item {
            ScreenHeader("Schedule", subtitle = if (from == null) "The next three months" else "From " + Dates.fieldDate(start, today)) {
                if (from != null) TextButton(onClick = { from = null }) { Text("Today") }
                IconButton(onClick = { jumping = true }, Modifier.semantics { contentDescription = "Jump to a date" }) { Icon(Icons.Rounded.EventAvailable, null) }
                SearchAction { nav.push(Page.Search) }
            }
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
    if (jumping) DatePickDialog(start, onPick = { from = it }, onDismiss = { jumping = false })
}

const val AGENDA_DAYS = 92
