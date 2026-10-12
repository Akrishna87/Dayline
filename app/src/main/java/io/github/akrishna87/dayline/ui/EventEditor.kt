package io.github.akrishna87.dayline.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.github.akrishna87.dayline.Scope
import io.github.akrishna87.dayline.Sharing
import io.github.akrishna87.dayline.Store
import io.github.akrishna87.dayline.model.Availability
import io.github.akrishna87.dayline.model.CalendarColors
import io.github.akrishna87.dayline.model.Data
import io.github.akrishna87.dayline.model.Dates
import io.github.akrishna87.dayline.model.Event
import io.github.akrishna87.dayline.model.QuickEvent
import io.github.akrishna87.dayline.model.ReminderChoices
import io.github.akrishna87.dayline.model.newId
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private enum class Picking { START_DATE, START_TIME, END_DATE, END_TIME }

/**
 * Makes or changes an event. Going back saves it; for a repeating event Google Calendar's question
 * comes first (this event, this and following, or all). For a new event, dates, times, a repeat
 * and a #calendar typed in the title are understood ("Lunch with Sam friday 1pm") until a field is
 * changed by hand.
 */
@Composable
fun EventPage(page: Page.EventPage, data: Data) {
    val nav = LocalNav.current
    val clock = LocalClock.current
    val messages = LocalMessages.current
    val askNotifications = LocalAskNotifications.current
    val context = LocalContext.current
    // The event as it was when the page opened; edits stay on this page until it's saved.
    val original = remember(page.id) { data.event(page.id) }
    if (!page.isNew && original == null) {
        LaunchedEffect(Unit) { nav.pop() }
        return
    }
    // The occurrence being edited (for a repeating event, the one that was tapped).
    val day = page.day ?: original?.date
    val initial = remember(page.id) {
        val o = original
        when {
            o != null -> {
                val d = day ?: o.date
                o.copy(date = d, endDate = d.plusDays(o.spanDays))
            }
            page.template != null -> page.template.copy(id = page.id)
            else -> {
                val d = page.startDay ?: clock.today
                val start = page.startTime ?: if (d == clock.today) Dates.nextHour(clock.now.toLocalTime()) else LocalTime.of(9, 0)
                val end = start.plusMinutes(data.settings.defaultDuration.toLong()).let { if (it.isBefore(start)) LocalTime.of(23, 59) else it }
                Event(
                    id = page.id, title = "", date = d, start = start, end = end,
                    calendarId = data.allCalendars.first().id, reminders = data.settings.defaultReminders,
                )
            }
        }
    }

    var title by remember { mutableStateOf(TextFieldValue(initial.title, TextRange(initial.title.length))) }
    var date by remember { mutableStateOf(initial.date) }
    var endDate by remember { mutableStateOf(initial.endDate) }
    var start by remember { mutableStateOf(initial.start) }
    var end by remember { mutableStateOf(initial.end) }
    var zone by remember { mutableStateOf(initial.zone) }
    var rule by remember { mutableStateOf(initial.rule) }
    var reminders by remember { mutableStateOf(initial.reminders) }
    var calendarId by remember { mutableStateOf(initial.calendarId) }
    var color by remember { mutableStateOf(initial.color) }
    var availability by remember { mutableStateOf(initial.availability) }
    var guests by remember { mutableStateOf(initial.guests) }
    var guestDraft by remember { mutableStateOf(TextFieldValue("")) }
    var location by remember { mutableStateOf(TextFieldValue(initial.location)) }
    var notes by remember { mutableStateOf(TextFieldValue(initial.notes)) }
    // The time an all-day event goes back to when "All day" is switched off.
    var lastStart by remember { mutableStateOf(initial.start ?: LocalTime.of(9, 0)) }
    var byHand by remember { mutableStateOf(!page.isNew || page.template != null) }

    var picking by remember { mutableStateOf<Picking?>(null) }
    var repeatMenu by remember { mutableStateOf(false) }
    var customRepeat by remember { mutableStateOf(false) }
    var reminderMenu by remember { mutableStateOf(false) }
    var calendarMenu by remember { mutableStateOf(false) }
    var colorMenu by remember { mutableStateOf(false) }
    var zonePicking by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var askingScope by remember { mutableStateOf<Event?>(null) }
    val titleFocus = remember { FocusRequester() }
    LaunchedEffect(page.id) {
        // A new event opens ready to type its title.
        if (page.isNew && page.template == null) runCatching { titleFocus.requestFocus() }
    }

    // What the title says, while it still decides the date and time.
    val parsed = if (byHand) null else QuickEvent.parse(title.text, clock.today, clock.now.toLocalTime(), data.allCalendars, Dates.weekStart(data.settings))
    val effDate = parsed?.date ?: date
    val effEndDate = effDate.plusDays(ChronoUnit.DAYS.between(date, endDate).coerceAtLeast(0))
    val effStart = parsed?.start ?: start
    val effEnd = when {
        parsed?.end != null -> parsed.end
        parsed?.start != null -> parsed.start.plusMinutes(data.settings.defaultDuration.toLong()).let { if (it.isBefore(parsed.start)) LocalTime.of(23, 59) else it }
        else -> end
    }
    val effRule = parsed?.repeat ?: rule
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
            rule = effRule
            calendarId = effCalendar
        }
        byHand = true
    }

    fun build(): Event {
        val s = effStart
        var e = effEnd
        if (s != null && (e == null || (effEndDate == effDate && !e.isAfter(s)))) e = Dates.defaultEnd(s)
        val pendingGuest = guestDraft.text.trim().takeIf { it.contains('@') }
        return (original ?: initial).copy(
            title = effTitle.trim(),
            date = effDate,
            endDate = effEndDate,
            start = s,
            end = if (s == null) null else e,
            zone = if (s == null) null else zone,
            rule = effRule?.let { r -> if (r.until != null && r.until.isBefore(effDate)) r.copy(until = effDate) else r },
            reminders = reminders.distinct().sorted(),
            calendarId = effCalendar,
            color = color,
            availability = availability,
            guests = (guests + listOfNotNull(pendingGuest)).distinct(),
            location = location.text.trim(),
            notes = notes.text.trim(),
        )
    }

    fun finish(event: Event) {
        if (event.reminders.isNotEmpty() && Store.data.value.settings.reminders) askNotifications()
        if (page.isNew) nav.select(event.date)
        nav.pop()
    }

    fun saveAndClose() {
        val event = build()
        val empty = event.title.isBlank() && event.location.isBlank() && event.notes.isBlank()
        if (page.isNew && empty) {
            nav.pop()
            return
        }
        val o = original
        if (o == null) {
            Store.saveEvent(event)
            finish(event)
            return
        }
        val unchanged = event == o.copy(date = event.date, endDate = event.date.plusDays(o.spanDays)) && event.date == (day ?: o.date)
        if (unchanged) {
            nav.pop()
            return
        }
        if (o.rule != null) {
            askingScope = event
        } else {
            Store.saveEvent(event)
            finish(event)
        }
    }

    BackHandler(enabled = askingScope == null && !deleting) { saveAndClose() }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        PageBar(onBack = ::saveAndClose) {
            if (!page.isNew) {
                IconButton(onClick = { deleting = true }, Modifier.semantics { contentDescription = "Delete event" }) { Icon(Icons.Rounded.Delete, null) }
            }
            Box {
                IconButton(onClick = { moreMenu = true }, Modifier.semantics { contentDescription = "Event options" }) { Icon(Icons.Rounded.MoreVert, null) }
                DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                    DropdownMenuItem(text = { Text("Duplicate") }, onClick = {
                        moreMenu = false
                        val copy = build().copy(id = newId(), skipped = emptyList(), createdAt = System.currentTimeMillis())
                        nav.pop()
                        nav.push(Page.EventPage(copy.id, null, isNew = true, template = copy))
                    })
                    DropdownMenuItem(text = { Text("Share as a file (.ics)") }, onClick = {
                        moreMenu = false
                        if (!Sharing.shareIcs(context, listOf(build()), build().title.ifBlank { "event" })) messages.show("No app to share with")
                    })
                    if (guests.isNotEmpty()) {
                        DropdownMenuItem(text = { Text("Email guests an invitation") }, onClick = {
                            moreMenu = false
                            if (!Sharing.emailInvitation(context, build())) messages.show("No email app found")
                        })
                    }
                }
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
                modifier = Modifier.padding(start = 64.dp, end = 20.dp, top = 8.dp, bottom = 4.dp).focusRequester(titleFocus),
            )
            if (!byHand) {
                Text(
                    if (parsed != null && parsed.title != title.text.trim()) "Understood: ${understood(effDate, effStart, effRule?.label(effDate), clock.today, clock.is24Hour)}"
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
                    reminders = data.settings.defaultReminders
                } else {
                    lastStart = start ?: lastStart
                    start = null
                    end = null
                    reminders = data.settings.defaultAllDayReminders
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
                    Text(Dates.timeFormatter(clock.is24Hour).format(effStart), Modifier.clickable { takeOver(); picking = Picking.START_TIME }, style = MaterialTheme.typography.bodyLarge)
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
                    Text(Dates.timeFormatter(clock.is24Hour).format(effEnd), Modifier.clickable { takeOver(); picking = Picking.END_TIME },
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!allDay) {
                FieldRow(Icons.Rounded.Language, onClick = { takeOver(); zonePicking = true }) {
                    Text((zone ?: ZoneId.systemDefault().id).replace('_', ' ') + if (zone == null) " (phone)" else "", style = MaterialTheme.typography.bodyLarge,
                        color = if (zone == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                }
            }
            Box {
                FieldRow(Icons.Rounded.Repeat, onClick = { takeOver(); repeatMenu = true }) {
                    Text(effRule?.label(effDate) ?: "Does not repeat", style = MaterialTheme.typography.bodyLarge)
                }
                RepeatMenu(repeatMenu, effRule, effDate, { repeatMenu = false }, onCustom = { customRepeat = true }) { rule = it }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Notifications: each with a remove button, then a row to add one.
            for (m in reminders.sorted()) {
                FieldRow(Icons.Rounded.Notifications, description = "Notification " + ReminderChoices.label(m)) {
                    Text(if (allDay) ReminderChoices.allDayLabel(m, data.settings.allDayReminderAt) else ReminderChoices.label(m), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    IconButton(onClick = { reminders = reminders - m }, Modifier.size(32.dp).semantics { contentDescription = "Remove notification" }) { Icon(Icons.Rounded.Close, null, Modifier.size(18.dp)) }
                }
            }
            Box {
                FieldRow(if (reminders.isEmpty()) Icons.Rounded.Notifications else null, onClick = { reminderMenu = true }) {
                    Text("Add notification", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ReminderMenu(reminderMenu, reminders, allDay, data.settings.allDayReminderAt, { reminderMenu = false }) { m -> reminders = reminders + m }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Guests.
            for (g in guests) {
                FieldRow(Icons.Rounded.People, description = "Guest $g") {
                    Text(g, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    IconButton(onClick = { guests = guests - g }, Modifier.size(32.dp).semantics { contentDescription = "Remove $g" }) { Icon(Icons.Rounded.Close, null, Modifier.size(18.dp)) }
                }
            }
            FieldRow(if (guests.isEmpty()) Icons.Rounded.People else null) {
                fun addGuest() {
                    val g = guestDraft.text.trim().trimEnd(',', ';')
                    if (g.contains('@') && g !in guests) guests = guests + g
                    guestDraft = TextFieldValue("")
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = guestDraft,
                    onValueChange = { v -> if (v.text.endsWith(",") || v.text.endsWith(" ")) { guestDraft = v; addGuest() } else guestDraft = v },
                    modifier = Modifier.weight(1f).semantics { contentDescription = "Add guests" },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addGuest() }),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        Box {
                            if (guestDraft.text.isEmpty()) Text("Add guests (email)", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
                            inner()
                        }
                    },
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            FieldRow(Icons.Rounded.Place) {
                PlainField(location, { location = it }, "Add location", "Location", MaterialTheme.typography.bodyLarge, Modifier.weight(1f), singleLine = true)
                if (location.text.isNotBlank()) {
                    IconButton(onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(location.text.trim())))) }
                            .onFailure { messages.show("No maps app found") }
                    }, Modifier.size(36.dp).semantics { contentDescription = "Open in maps" }) { Icon(Icons.Rounded.Directions, null) }
                }
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
            Box {
                val calColor = data.calendar(effCalendar).color
                FieldRow(Icons.Rounded.Palette, onClick = { colorMenu = true }, description = "Colour") {
                    ColorDot(color ?: calColor, 14.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(color?.let { CalendarColors.name(it) ?: "Custom" } ?: "Calendar colour", style = MaterialTheme.typography.bodyLarge)
                }
                EventColorMenu(colorMenu, color, calColor, { colorMenu = false }) { color = it }
            }
            FieldRow(Icons.Rounded.Work, onClick = { availability = if (availability == Availability.BUSY) Availability.FREE else Availability.BUSY }, description = "Show as") {
                Text(if (availability == Availability.BUSY) "Busy" else "Free", style = MaterialTheme.typography.bodyLarge)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            FieldRow(Icons.AutoMirrored.Rounded.Notes) {
                PlainField(notes, { notes = it }, "Add description", "Notes", MaterialTheme.typography.bodyLarge, Modifier.heightIn(min = 72.dp))
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
        null -> {}
    }
    if (customRepeat) RuleDialog(effRule, effDate, onDone = { rule = it }, onDismiss = { customRepeat = false })
    if (zonePicking) ZoneDialog(zone, onPick = { zone = it }, onDismiss = { zonePicking = false })

    askingScope?.let { event ->
        val o = original!!
        ScopeDialog("Save recurring event", onPick = { scope ->
            val split = Store.saveOccurrence(o, day ?: o.date, event, scope)
            messages.offerUndo("Event saved") { Store.restoreEvent(o, split) }
            finish(event)
        }, onDismiss = { askingScope = null })
    }

    if (deleting && original != null) {
        val d = day ?: original.date
        fun deleted(text: String) {
            messages.offerUndo(text) { Store.restoreEvent(original) }
            nav.pop()
        }
        if (original.rule == null) {
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
            ScopeDialog("Delete recurring event", onPick = { scope ->
                when (scope) {
                    Scope.THIS -> { Store.skipOccurrence(original.id, d); deleted("Deleted on ${Dates.dayLabel(d, clock.today)}") }
                    Scope.FOLLOWING -> { Store.endRepeatBefore(original.id, d); deleted("Deleted from ${Dates.dayLabel(d, clock.today)} on") }
                    Scope.ALL -> { Store.deleteEvent(original.id); deleted("Event deleted") }
                }
            }, onDismiss = { deleting = false })
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

/** "Fri 16 Oct, 13:00, weekly on Fri": what was read from the title. */
private fun understood(date: LocalDate, start: LocalTime?, repeat: String?, today: LocalDate, is24Hour: Boolean): String =
    listOfNotNull(
        Dates.dayLabel(date, today),
        start?.let { Dates.timeFormatter(is24Hour).format(it) },
        repeat?.replaceFirstChar { it.lowercase() },
    ).joinToString(", ")
