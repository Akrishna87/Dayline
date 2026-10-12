package io.github.akrishna87.dayline.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import io.github.akrishna87.dayline.model.CalendarColors
import io.github.akrishna87.dayline.model.Freq
import io.github.akrishna87.dayline.model.Rule
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import io.github.akrishna87.dayline.model.EventCalendar
import io.github.akrishna87.dayline.model.ReminderChoices
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickDialog(initial: LocalDate?, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = (initial ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state = state) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickDialog(initial: LocalTime, is24Hour: Boolean, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = is24Hour)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onPick(LocalTime.of(state.hour, state.minute))
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) },
    )
}

/** Google Calendar's repeat choices for an event starting on [first], "Custom…", and "Does not repeat". */
@Composable
fun RepeatMenu(expanded: Boolean, current: Rule?, first: LocalDate, onDismiss: () -> Unit, onCustom: () -> Unit, onPick: (Rule?) -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("Does not repeat") }, onClick = { onPick(null); onDismiss() }, trailingIcon = { if (current == null) Icon(Icons.Rounded.Check, null) })
        for (r in Rule.presets(first)) {
            DropdownMenuItem(
                text = { Text(r.label(first)) },
                onClick = { onPick(r); onDismiss() },
                trailingIcon = { if (r == current) Icon(Icons.Rounded.Check, null) },
            )
        }
        DropdownMenuItem(text = { Text("Custom…") }, onClick = { onDismiss(); onCustom() })
    }
}

/** A repeat made by hand: every N days/weeks/months/years, on chosen days, ending never, on a date, or after N times. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RuleDialog(initial: Rule?, first: LocalDate, onDone: (Rule) -> Unit, onDismiss: () -> Unit) {
    var freq by remember { mutableStateOf(initial?.freq ?: Freq.WEEKLY) }
    var interval by remember { mutableStateOf((initial?.interval ?: 1).toString()) }
    var days by remember { mutableStateOf(initial?.takeIf { it.freq == Freq.WEEKLY }?.weekdays?.takeIf { it.isNotEmpty() } ?: setOf(first.dayOfWeek)) }
    // Monthly: 0 = on this day, 1 = on the nth weekday, 2 = on the last day.
    var monthMode by remember { mutableIntStateOf(when { initial?.nth != null -> 1; initial?.monthDay == -1 -> 2; else -> 0 }) }
    // Ends: 0 = never, 1 = on a date, 2 = after a number of times.
    var ends by remember { mutableIntStateOf(when { initial?.until != null -> 1; initial?.count != null -> 2; else -> 0 }) }
    var until by remember { mutableStateOf(initial?.until ?: first.plusMonths(3)) }
    var count by remember { mutableStateOf((initial?.count ?: 10).toString()) }
    var pickingUntil by remember { mutableStateOf(false) }
    val nth = Rule.nthOf(first)

    fun build(): Rule {
        val n = interval.toIntOrNull()?.coerceIn(1, 999) ?: 1
        val base = when (freq) {
            Freq.WEEKLY -> Rule(Freq.WEEKLY, n, weekdays = days.ifEmpty { setOf(first.dayOfWeek) })
            Freq.MONTHLY -> when (monthMode) {
                1 -> Rule(Freq.MONTHLY, n, weekdays = setOf(first.dayOfWeek), nth = nth)
                2 -> Rule(Freq.MONTHLY, n, monthDay = -1)
                else -> Rule(Freq.MONTHLY, n, monthDay = first.dayOfMonth)
            }
            else -> Rule(freq, n)
        }
        return when (ends) {
            1 -> base.copy(until = if (until.isBefore(first)) first else until)
            2 -> base.copy(count = count.toIntOrNull()?.coerceIn(1, 999) ?: 10)
            else -> base
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom recurrence") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Repeats every")
                    OutlinedTextField(
                        value = interval,
                        onValueChange = { v -> interval = v.filter { it.isDigit() }.take(3) },
                        modifier = Modifier.width(76.dp).padding(horizontal = 10.dp).semantics { contentDescription = "Repeat every how many" },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val n = interval.toIntOrNull() ?: 1
                    for (f in Freq.entries) {
                        val name = when (f) { Freq.DAILY -> "day"; Freq.WEEKLY -> "week"; Freq.MONTHLY -> "month"; Freq.YEARLY -> "year" } + if (n == 1) "" else "s"
                        Pill(name, f == freq) { freq = f }
                    }
                }
                if (freq == Freq.WEEKLY) {
                    Text("Repeats on", Modifier.padding(top = 16.dp, bottom = 6.dp), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (d in DayOfWeek.entries) {
                            val on = d in days
                            Box(
                                Modifier.size(38.dp).clip(CircleShape)
                                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { days = if (on) days - d else days + d }
                                    .semantics { contentDescription = d.getDisplayName(TextStyle.FULL, Locale.getDefault()) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(d.getDisplayName(TextStyle.NARROW, Locale.getDefault()), color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (freq == Freq.MONTHLY) {
                    val dayName = first.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
                    for ((mode, label) in listOf(0 to "Monthly on day ${first.dayOfMonth}", 1 to "Monthly on the ${Rule.ordinal(nth)} $dayName", 2 to "Monthly on the last day")) {
                        Row(Modifier.fillMaxWidth().clickable { monthMode = mode }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = monthMode == mode, onClick = null)
                            Text(label, Modifier.padding(start = 8.dp))
                        }
                    }
                }
                Text("Ends", Modifier.padding(top = 16.dp, bottom = 2.dp), style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth().clickable { ends = 0 }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = ends == 0, onClick = null)
                    Text("Never", Modifier.padding(start = 8.dp))
                }
                Row(Modifier.fillMaxWidth().clickable { ends = 1 }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = ends == 1, onClick = null)
                    Text("On", Modifier.padding(start = 8.dp))
                    TextButton(onClick = { ends = 1; pickingUntil = true }) { Text(DateTimeFormatter.ofPattern("d MMM yyyy").format(until)) }
                }
                Row(Modifier.fillMaxWidth().clickable { ends = 2 }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = ends == 2, onClick = null)
                    Text("After", Modifier.padding(start = 8.dp))
                    OutlinedTextField(
                        value = count,
                        onValueChange = { v -> count = v.filter { it.isDigit() }.take(3); ends = 2 },
                        modifier = Modifier.width(76.dp).padding(horizontal = 10.dp).semantics { contentDescription = "Number of times" },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Text("times")
                }
                Text(build().label(first), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = { TextButton(onClick = { onDone(build()); onDismiss() }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (pickingUntil) DatePickDialog(until, onPick = { until = it }, onDismiss = { pickingUntil = false })
}

@Composable
private fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        label,
        Modifier
            .clip(shape)
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.primaryContainer, shape) else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelLarge,
    )
}

/** Adding a notification: the usual choices, for a timed event or an all-day one. */
@Composable
fun ReminderMenu(expanded: Boolean, taken: List<Int>, allDay: Boolean, allDayAt: java.time.LocalTime, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        val choices = if (allDay) listOf(0, 1440, 2880, 10080) else ReminderChoices.minutes
        for (m in choices) {
            DropdownMenuItem(
                text = { Text(if (allDay) ReminderChoices.allDayLabel(m, allDayAt) else ReminderChoices.label(m)) },
                onClick = { onPick(m); onDismiss() },
                enabled = m !in taken,
                trailingIcon = { if (m in taken) Icon(Icons.Rounded.Check, null) },
            )
        }
    }
}

@Composable
fun CalendarMenu(expanded: Boolean, calendars: List<EventCalendar>, current: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        for (c in calendars) {
            DropdownMenuItem(
                text = { Text(c.name) },
                onClick = { onPick(c.id); onDismiss() },
                leadingIcon = { ColorDot(c.color, 14.dp) },
                trailingIcon = { if (c.id == current) Icon(Icons.Rounded.Check, null) },
            )
        }
    }
}

/** An event's own colour (Google Calendar's eleven), or its calendar's. */
@Composable
fun EventColorMenu(expanded: Boolean, current: Int?, calendarColor: Int, onDismiss: () -> Unit, onPick: (Int?) -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Calendar colour") },
            onClick = { onPick(null); onDismiss() },
            leadingIcon = { ColorDot(calendarColor, 16.dp) },
            trailingIcon = { if (current == null) Icon(Icons.Rounded.Check, null) },
        )
        for ((name, c) in CalendarColors.named) {
            DropdownMenuItem(
                text = { Text(name) },
                onClick = { onPick(c); onDismiss() },
                leadingIcon = { ColorDot(c, 16.dp) },
                trailingIcon = { if (c == current) Icon(Icons.Rounded.Check, null) },
            )
        }
    }
}

/** Picking a time zone: the phone's, recent common ones, or any by searching. */
@Composable
fun ZoneDialog(current: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val all = remember { java.time.ZoneId.getAvailableZoneIds().filter { it.contains('/') && !it.startsWith("Etc/") && !it.startsWith("SystemV/") }.sorted() }
    val now = java.time.Instant.now()
    fun offset(id: String) = runCatching { java.time.ZoneId.of(id).rules.getOffset(now).id.replace("Z", "+00:00") }.getOrDefault("")
    val shown = if (query.isBlank()) all.take(400) else all.filter { it.replace('_', ' ').contains(query.trim(), true) }.take(200)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Time zone") },
        text = {
            Column {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search time zones" },
                    placeholder = { Text("Search, e.g. London") }, singleLine = true,
                )
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
                    Row(Modifier.fillMaxWidth().clickable { onPick(null); onDismiss() }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Phone's time zone (" + java.time.ZoneId.systemDefault().id.replace('_', ' ') + ")", Modifier.weight(1f))
                        if (current == null) Icon(Icons.Rounded.Check, null)
                    }
                    for (id in shown) {
                        Row(Modifier.fillMaxWidth().clickable { onPick(id); onDismiss() }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(id.replace('_', ' '), Modifier.weight(1f))
                            Text("GMT" + offset(id), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (id == current) Icon(Icons.Rounded.Check, null, Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Google Calendar's question when changing or deleting a repeating event. */
@Composable
fun ScopeDialog(title: String, onPick: (io.github.akrishna87.dayline.Scope) -> Unit, onDismiss: () -> Unit) {
    var scope by remember { mutableStateOf(io.github.akrishna87.dayline.Scope.THIS) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                for (s in io.github.akrishna87.dayline.Scope.entries) {
                    Row(Modifier.fillMaxWidth().clickable { scope = s }.semantics { contentDescription = s.label }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = scope == s, onClick = null)
                        Text(s.label, Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(scope); onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A name in a box with colour choices, for new and renamed calendars. */
@Composable
fun NameDialog(
    title: String,
    initial: String = "",
    label: String = "Name",
    confirm: String = "Save",
    colors: List<Int>? = null,
    initialColor: Int = 0,
    onSave: (String, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    var color by remember { mutableIntStateOf(if (initialColor != 0) initialColor else colors?.getOrNull(5) ?: 0) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    label = { Text(label) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) { onSave(name.trim(), color); onDismiss() } }),
                )
                if (colors != null) {
                    ColorChoices(colors, color, { color = it }, Modifier.padding(top = 16.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), color); onDismiss() }, enabled = name.isNotBlank()) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorChoices(colors: List<Int>, selected: Int, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (c in colors) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.RadioButton, onClickLabel = "Choose colour") { onPick(c) },
                contentAlignment = Alignment.Center,
            ) {
                ColorDot(c, 32.dp)
                if (c == selected) Icon(Icons.Rounded.Check, "Selected", Modifier.size(18.dp), tint = Color.White)
            }
        }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
