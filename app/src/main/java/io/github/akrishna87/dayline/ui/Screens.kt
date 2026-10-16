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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
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
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).clip(MaterialTheme.shapes.small).clickable { jumping = true }
                    .semantics { contentDescription = "Showing ${DateTimeFormatter.ofPattern("MMMM yyyy").format(shown)}. Jump to a date" }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(shown.month.getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, java.util.Locale.getDefault()), style = MaterialTheme.typography.headlineLarge, maxLines = 1)
                Text(
                    " " + shown.year,
                    Modifier.padding(bottom = 4.dp),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (!(nav.selected == today && (view != CalendarView.MONTH || nav.month == YearMonth.from(today)))) {
                Text(
                    "Today",
                    Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable { nav.select(today); nav.month = YearMonth.from(today) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            SearchAction { nav.push(Page.Search) }
        }
        SegmentedControl(
            GRID_VIEWS.map { it.label },
            GRID_VIEWS.indexOf(view),
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        ) { i ->
            val v = GRID_VIEWS[i]
            nav.view = v
            if (v == CalendarView.MONTH) nav.month = YearMonth.from(nav.selected)
            Store.updateSettings { it.copy(view = v) }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp).pointerInput(view, nav.selected, nav.month) {
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
        grid.flatten().associateWith { day -> all.filter { it.covers(day) }.sortedWith(Recurrence.dayOrder(day)).map { data.colorOf(it.event) to it.event.title.ifBlank { "(No title)" } } }
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
    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)) {
        item {
            KitCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp), padding = PaddingValues(top = 12.dp, bottom = 8.dp)) {
                Text(
                    Dates.dayHeading(selected, today),
                    Modifier.padding(start = 16.dp, bottom = 6.dp),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = if (selected == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                if (onDay.isEmpty()) {
                    Text(
                        "Nothing planned",
                        Modifier.fillMaxWidth().clickable { nav.newEvent(selected) }.padding(horizontal = 16.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                for (o in onDay) {
                    EventRow(o, selected, data, Modifier.padding(horizontal = 10.dp, vertical = 3.dp)) { nav.push(Page.EventPage(o.event.id, o.day)) }
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: LocalDate, inMonth: Boolean, isToday: Boolean, isSelected: Boolean, colors: List<Pair<Int, String>>, modifier: Modifier, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val label = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).format(day) +
        if (colors.isEmpty()) "" else ", ${colors.size} event${if (colors.size == 1) "" else "s"}"
    Column(
        modifier
            .height(64.dp)
            .padding(1.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected && !isToday) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = label
                selected = isSelected
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.padding(top = 3.dp).size(26.dp).clip(CircleShape).background(if (isToday) primary else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                day.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isToday || isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                color = when {
                    isToday -> MaterialTheme.colorScheme.onPrimary
                    isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                    inMonth -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.outline
                },
            )
        }
        for ((c, title) in colors.take(2)) {
            val (bg, fg) = eventTint(Color(c))
            Text(
                title,
                Modifier.padding(top = 2.dp, start = 2.dp, end = 2.dp).fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(bg).padding(horizontal = 3.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold),
                color = fg.copy(alpha = if (inMonth) 1f else 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
        if (colors.size > 2) Text("+${colors.size - 2}", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                CircleAction(Icons.Rounded.EventAvailable, "Jump to a date") { jumping = true }
                SearchAction { nav.push(Page.Search) }
            }
        }
        if (days.isEmpty()) {
            item { EmptyState(Icons.Rounded.EventAvailable, "Nothing planned", "Events for the next three months show here. Tap + to add one.") }
        }
        for ((day, list) in days) {
            item(key = "d-$day") { ScheduleDay(day, today, list, data) }
        }
    }
    if (jumping) DatePickDialog(start, onPick = { from = it }, onDismiss = { jumping = false })
}

/** A day in the Schedule: its date in a badge on the left, its events as cards beside it. */
@Composable
private fun ScheduleDay(day: LocalDate, today: LocalDate, list: List<io.github.akrishna87.dayline.model.Occurrence>, data: Data) {
    val nav = LocalNav.current
    val isToday = day == today
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.Top) {
        Column(
            Modifier.width(56.dp).padding(top = 4.dp).semantics(mergeDescendants = true) { contentDescription = Dates.dayHeading(day, today) },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                day.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp),
                color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                Modifier.padding(top = 2.dp).size(38.dp).clip(CircleShape).background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    day.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                )
            }
            if (day.dayOfMonth == 1 || isToday) {
                Text(DateTimeFormatter.ofPattern("MMM").format(day), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (o in list) EventRow(o, day, data, Modifier) { nav.push(Page.EventPage(o.event.id, o.day)) }
        }
    }
}

const val AGENDA_DAYS = 92
