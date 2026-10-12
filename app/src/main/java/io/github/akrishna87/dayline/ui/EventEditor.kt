package io.github.akrishna87.dayline.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.github.akrishna87.dayline.Store
import io.github.akrishna87.dayline.model.Data
import io.github.akrishna87.dayline.model.Dates
import io.github.akrishna87.dayline.model.Event
import io.github.akrishna87.dayline.model.QuickEvent
import io.github.akrishna87.dayline.model.ReminderChoices
import io.github.akrishna87.dayline.model.Repeat
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

private enum class Picking { START_DATE, START_TIME, END_DATE, END_TIME, UNTIL }

/**
 * Makes or changes an event. Going back saves it. For a new event, dates, times, a repeat and a
 * #calendar typed in the title are understood ("Lunch with Sam friday 1pm") until a field is
 * changed by hand.
 */
@Composable
fun EventPage(page: Page.EventPage, data: Data) {
    val nav = LocalNav.current
    val clock = LocalClock.current
    val messages = LocalMessages.current
    val askNotifications = LocalAskNotifications.current
    // The event as it was when the page opened; edits stay on this page until it's saved.
    val original = remember(page.id) { data.event(page.id) }
    if (!page.isNew && original == null) {
        LaunchedEffect(Unit) { nav.pop() }
        return
    }
    val initial = remember(page.id) {
        original ?: run {
            val day = page.startDay ?: clock.today
            val start = if (day == clock.today) Dates.nextHour(clock.now.toLocalTime()) else LocalTime.of(9, 0)
            Event(
                id = page.id,
                title = "",
                date = day,
                start = start,
                end = Dates.defaultEnd(start),
                calendarId = data.allCalendars.first().id,
                reminder = data.settings.defaultReminder,
            )
        }
    }

    var title by remember { mutableStateOf(TextFieldValue(initial.title, TextRange(initial.title.length))) }
    var date by remember { mutableStateOf(initial.date) }
    var endDate by remember { mutableStateOf(initial.endDate) }
    var start by remember { mutableStateOf(initial.start) }
    var end by remember { mutableStateOf(initial.end) }
    var repeat by remember { mutableStateOf(initial.repeat) }
    var repeatUntil by remember { mutableStateOf(initial.repeatUntil) }
    var reminder by remember { mutableStateOf(initial.reminder) }
    var calendarId by remember { mutableStateOf(initial.calendarId) }
    var location by remember { mutableStateOf(TextFieldValue(initial.location)) }
    var notes by remember { mutableStateOf(TextFieldValue(initial.notes)) }
    // The time an all-day event goes back to when "All day" is switched off.
    var lastStart by remember { mutableStateOf(initial.start ?: LocalTime.of(9, 0)) }
    var byHand by remember { mutableStateOf(!page.isNew) }

    var picking by remember { mutableStateOf<Picking?>(null) }
    var repeatMenu by remember { mutableStateOf(false) }
    var reminderMenu by remember { mutableStateOf(false) }
    var calendarMenu by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    // What the title says, while it still decides the date and time.
    val parsed = if (byHand) null else QuickEvent.parse(title.text, clock.today, clock.now.toLocalTime(), data.allCalendars, Dates.weekStart(data.settings))
    val effDate = parsed?.date ?: date
    val effEndDate = effDate.plusDays(ChronoUnit.DAYS.between(date, endDate).coerceAtLeast(0))
    val effStart = parsed?.start ?: start
    val effEnd = when {
        parsed?.end != null -> parsed.end
        parsed?.start != null -> Dates.defaultEnd(parsed.start)
        else -> end
    }
    val effRepeat = parsed?.repeat ?: repeat
    val effCalendar = parsed?.calendarId ?: calendarId
    val effTitle = parsed?.title ?: title.text

    /** Fixes what the title said into the fields, so changing one by hand doesn't lose the rest. */
    fun takeOver() {
        if (byHand) return
        if (parsed != null) {
            title = TextFieldValue(parsed.title, TextRange(parsed.title.length))
            date = effDate
            endDate = effEndDate
            start = effStart
            end = effEnd
            repeat = effRepeat
            calendarId = effCalendar
        }
        byHand = true
    }

    fun build(): Event {
        val s = effStart
        var e = effEnd
        if (s != null && (e == null || (effEndDate == effDate && !e.isAfter(s)))) e = Dates.defaultEnd(s)
        return (original ?: initial).copy(
            title = effTitle.trim(),
            date = effDate,
            endDate = effEndDate,
            start = s,
            end = if (s == null) null else e,
            repeat = effRepeat,
            repeatUntil = if (effRepeat == Repeat.NONE) null else repeatUntil?.takeIf { !it.isBefore(effDate) },
            reminder = reminder,
            calendarId = effCalendar,
            location = location.text.trim(),
            notes = notes.text.trim(),
        )
    }

    fun saveAndClose() {
        val event = build()
        val empty = event.title.isBlank() && event.location.isBlank() && event.notes.isBlank()
        if (page.isNew && empty) {
            nav.pop()
            return
        }
        if (event != original) {
            Store.saveEvent(event)
            if (event.reminder != null && Store.data.value.settings.reminders) askNotifications()
        }
        if (page.isNew) {
            nav.selected = event.date
            nav.month = java.time.YearMonth.from(event.date)
        }
        nav.pop()
    }

    BackHandler { saveAndClose() }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        PageBar(onBack = ::saveAndClose) {
            if (!page.isNew) {
                IconButton(onClick = { deleting = true }, Modifier.semantics { contentDescription = "Delete event" }) { Icon(Icons.Rounded.Delete, null) }
            }
            TextButton(onClick = ::saveAndClose, Modifier.padding(end = 4.dp)) { Text("Save") }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            PlainField(
                value = title,
                onChange = { title = it },
                placeholder = "Add title",
                description = "Event title",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 64.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
            )
            if (!byHand) {
                Text(
                    if (parsed != null && parsed.title != title.text.trim()) "Understood: ${understood(effDate, effStart, effRepeat, clock.today, clock.is24Hour)}"
                    else "Try “Lunch with Sam friday 1pm” or “Standup every weekday 9:30am”",
                    Modifier.padding(start = 64.dp, end = 20.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            val allDay = effStart == null
            FieldRow(Icons.Rounded.Schedule, onClick = {
                takeOver()
                if (allDay) {
                    start = lastStart
                    end = Dates.defaultEnd(lastStart)
                } else {
                    lastStart = start ?: lastStart
                    start = null
                    end = null
                }
            }, description = "All day") {
                Text("All day", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = allDay, onCheckedChange = null)
            }
            FieldRow(null) {
                Text(
                    Dates.fieldDate(effDate, clock.today),
                    Modifier.weight(1f).clickable { takeOver(); picking = Picking.START_DATE }.semantics { contentDescription = "Starts ${Dates.fieldDate(effDate, clock.today)}" },
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (effStart != null) {
                    Text(
                        Dates.timeFormatter(clock.is24Hour).format(effStart),
                        Modifier.clickable { takeOver(); picking = Picking.START_TIME },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            FieldRow(null) {
                Text(
                    Dates.fieldDate(effEndDate, clock.today),
                    Modifier.weight(1f).clickable { takeOver(); picking = Picking.END_DATE }.semantics { contentDescription = "Ends ${Dates.fieldDate(effEndDate, clock.today)}" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (effStart != null && effEnd != null) {
                    Text(
                        Dates.timeFormatter(clock.is24Hour).format(effEnd),
                        Modifier.clickable { takeOver(); picking = Picking.END_TIME },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Box {
                FieldRow(Icons.Rounded.Repeat, onClick = { takeOver(); repeatMenu = true }) {
                    Text(effRepeat.label, style = MaterialTheme.typography.bodyLarge)
                }
                RepeatMenu(repeatMenu, effRepeat, { repeatMenu = false }) { repeat = it }
            }
            if (effRepeat != Repeat.NONE) {
                FieldRow(null, onClick = { picking = Picking.UNTIL }) {
                    Text(
                        repeatUntil?.let { "Until ${Dates.fieldDate(it, clock.today)}" } ?: "Repeats forever",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (repeatUntil != null) {
                        IconButton(onClick = { repeatUntil = null }, Modifier.size(32.dp).semantics { contentDescription = "Repeat forever" }) {
                            Icon(Icons.Rounded.Close, null, Modifier.size(18.dp))
                        }
                    }
                }
            }
            Box {
                FieldRow(Icons.Rounded.Notifications, onClick = { reminderMenu = true }) {
                    Text(ReminderChoices.label(reminder), style = MaterialTheme.typography.bodyLarge)
                }
                ReminderMenu(reminderMenu, reminder, { reminderMenu = false }) { reminder = it }
            }
            Box {
                val cal = data.calendar(effCalendar)
                FieldRow(Icons.Rounded.Event, onClick = { takeOver(); calendarMenu = true }, description = "Calendar ${cal.name}") {
                    ColorDot(cal.color, 14.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(cal.name, style = MaterialTheme.typography.bodyLarge)
                }
                CalendarMenu(calendarMenu, data.allCalendars, effCalendar, { calendarMenu = false }) { calendarId = it }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            FieldRow(Icons.Rounded.Place) {
                PlainField(location, { location = it }, "Add location", "Location", MaterialTheme.typography.bodyLarge, singleLine = true)
            }
            FieldRow(Icons.AutoMirrored.Rounded.Notes) {
                PlainField(notes, { notes = it }, "Add notes", "Notes", MaterialTheme.typography.bodyLarge, Modifier.heightIn(min = 72.dp))
            }
            Spacer(Modifier.heightIn(min = 48.dp))
        }
    }

    when (picking) {
        Picking.START_DATE -> DatePickDialog(date, { d ->
            val span = ChronoUnit.DAYS.between(date, endDate).coerceAtLeast(0)
            date = d
            endDate = d.plusDays(span)
        }) { picking = null }
        Picking.END_DATE -> DatePickDialog(endDate, { d -> endDate = if (d.isBefore(date)) date else d }) { picking = null }
        Picking.START_TIME -> TimePickDialog(start ?: lastStart, clock.is24Hour, { t ->
            // Keep the length of the event when its start moves.
            val old = start
            val e = end
            end = if (old != null && e != null && endDate == date) {
                val minutes = ChronoUnit.MINUTES.between(old, e).coerceAtLeast(15)
                t.plusMinutes(minutes).takeIf { it.isAfter(t) } ?: LocalTime.of(23, 59)
            } else {
                e ?: Dates.defaultEnd(t)
            }
            start = t
            lastStart = t
        }) { picking = null }
        Picking.END_TIME -> TimePickDialog(end ?: Dates.defaultEnd(start ?: lastStart), clock.is24Hour, { t -> end = t }) { picking = null }
        Picking.UNTIL -> DatePickDialog(repeatUntil ?: effDate.plusMonths(3), { d -> repeatUntil = if (d.isBefore(effDate)) effDate else d }) { picking = null }
        null -> {}
    }

    if (deleting && original != null) {
        val day = page.day ?: original.date
        fun deleted(text: String) {
            messages.offerUndo(text) { Store.restoreEvent(original) }
            nav.pop()
        }
        if (original.repeat == Repeat.NONE) {
            ConfirmDialog(
                "Delete this event?",
                "“${original.title.ifBlank { "(No title)" }}” is deleted.",
                "Delete",
                onConfirm = {
                    Store.deleteEvent(original.id)
                    deleted("Event deleted")
                },
                onDismiss = { deleting = false },
            )
        } else {
            AlertDialog(
                onDismissRequest = { deleting = false },
                title = { Text("Delete repeating event") },
                text = {
                    Column {
                        TextButton(onClick = {
                            deleting = false
                            Store.skipOccurrence(original.id, day)
                            deleted("Deleted on ${Dates.dayLabel(day, clock.today)}")
                        }) { Text("This event") }
                        TextButton(onClick = {
                            deleting = false
                            Store.endRepeatBefore(original.id, day)
                            deleted("Deleted from ${Dates.dayLabel(day, clock.today)} on")
                        }) { Text("This and following events") }
                        TextButton(onClick = {
                            deleting = false
                            Store.deleteEvent(original.id)
                            deleted("Event deleted")
                        }) { Text("All events") }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } },
            )
        }
    }
}

/** A line of the form: an icon on the left, then the field. */
@Composable
private fun FieldRow(icon: ImageVector?, onClick: (() -> Unit)? = null, description: String? = null, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp)
            .heightIn(min = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(44.dp)) {
            if (icon != null) Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        content()
    }
}

/** "Fri 16 Oct, 13:00, every week": what was read from the title. */
private fun understood(date: LocalDate, start: LocalTime?, repeat: Repeat, today: LocalDate, is24Hour: Boolean): String =
    listOfNotNull(
        Dates.dayLabel(date, today),
        start?.let { Dates.timeFormatter(is24Hour).format(it) },
        repeat.takeIf { it != Repeat.NONE }?.label?.lowercase(),
    ).joinToString(", ")
