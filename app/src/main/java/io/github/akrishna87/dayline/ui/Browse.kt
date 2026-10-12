package io.github.akrishna87.dayline.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.github.akrishna87.dayline.Backup
import io.github.akrishna87.dayline.Reminders
import io.github.akrishna87.dayline.Store
import io.github.akrishna87.dayline.model.CalendarColors
import io.github.akrishna87.dayline.model.Data
import io.github.akrishna87.dayline.model.Dates
import io.github.akrishna87.dayline.model.EventCalendar
import io.github.akrishna87.dayline.model.Occurrence
import io.github.akrishna87.dayline.model.Recurrence
import io.github.akrishna87.dayline.model.ReminderChoices
import io.github.akrishna87.dayline.model.ThemeMode
import java.time.LocalDate

/** Calendars to show or hide, add, rename and delete; and the way to Settings. */
@Composable
fun CalendarsScreen(data: Data, padding: PaddingValues) {
    val nav = LocalNav.current
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<EventCalendar?>(null) }
    var removing by remember { mutableStateOf<EventCalendar?>(null) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
    ) {
        item {
            ScreenHeader("Calendars", subtitle = "Tick the ones to show") {
                SearchAction { nav.push(Page.Search) }
                IconButton(onClick = { nav.push(Page.SettingsPage) }, Modifier.semantics { contentDescription = "Settings" }) { Icon(Icons.Rounded.Settings, null) }
            }
        }
        item {
            SectionHeader("My calendars") {
                IconButton(onClick = { adding = true }, Modifier.semantics { contentDescription = "Add calendar" }) { Icon(Icons.Rounded.Add, null) }
            }
        }
        items(data.allCalendars, key = { it.id }) { c ->
            var menu by remember { mutableStateOf(false) }
            val count = data.events.count { it.calendarId == c.id }
            ListRow(
                c.name,
                leading = { ColorDot(c.color, 16.dp) },
                detail = if (count == 1) "1 event" else "$count events",
                trailing = {
                    Checkbox(
                        checked = c.visible,
                        onCheckedChange = { on -> Store.saveCalendar(c.copy(visible = on)) },
                        modifier = Modifier.semantics { contentDescription = "Show ${c.name}" },
                    )
                    Box {
                        IconButton(onClick = { menu = true }, Modifier.semantics { contentDescription = "${c.name} options" }) { Icon(Icons.Rounded.MoreVert, null) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Rename or recolour") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; editing = c })
                            if (data.calendars.size > 1) {
                                DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { menu = false; removing = c })
                            }
                        }
                    }
                },
                onClick = { Store.saveCalendar(c.copy(visible = !c.visible)) },
            )
        }
        item { SectionHeader("More") }
        item {
            ListRow(
                "Settings",
                leading = { Icon(Icons.Rounded.Settings, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                onClick = { nav.push(Page.SettingsPage) },
            )
        }
    }
    if (adding) {
        NameDialog(
            title = "New calendar",
            confirm = "Add",
            colors = CalendarColors.choices,
            initialColor = CalendarColors.choices[(data.calendars.size + 3) % CalendarColors.choices.size],
            onSave = { name, color -> Store.saveCalendar(EventCalendar(name = name, color = color)) },
            onDismiss = { adding = false },
        )
    }
    editing?.let { c ->
        NameDialog(
            title = "Edit calendar",
            initial = c.name,
            colors = CalendarColors.choices,
            initialColor = c.color,
            onSave = { name, color -> Store.saveCalendar(c.copy(name = name, color = color)) },
            onDismiss = { editing = null },
        )
    }
    removing?.let { c ->
        val n = data.events.count { it.calendarId == c.id }
        ConfirmDialog(
            "Delete “${c.name}”?",
            if (n == 0) "It has no events." else "Its $n event${if (n == 1) " is" else "s are"} deleted too.",
            "Delete",
            onConfirm = { Store.deleteCalendar(c.id) },
            onDismiss = { removing = null },
        )
    }
}

/** Finds events by title, place or notes, showing each one's next date (or its last, if it's over). */
@Composable
fun SearchPage(data: Data) {
    val nav = LocalNav.current
    val today = LocalClock.current.today
    var query by remember { mutableStateOf(TextFieldValue("")) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val q = query.text.trim()
    val results = if (q.isEmpty()) emptyList() else data.events
        .filter { it.title.contains(q, true) || it.location.contains(q, true) || it.notes.contains(q, true) }
        .map { e -> Occurrence(e, Recurrence.next(e, today) ?: e.date) }
        .sortedWith(compareBy<Occurrence>({ it.day.isBefore(today) }, { if (it.day.isBefore(today)) -it.day.toEpochDay() else it.day.toEpochDay() }))
    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        PageBar(onBack = { nav.pop() }, leading = {
            Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            PlainField(
                value = query,
                onChange = { query = it },
                placeholder = "Search events",
                description = "Search",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(10f).padding(start = 12.dp).focusRequester(focus),
                singleLine = true,
            )
        }) {
            if (query.text.isNotEmpty()) {
                IconButton(onClick = { query = TextFieldValue("") }, Modifier.semantics { contentDescription = "Clear search" }) { Icon(Icons.Rounded.Close, null) }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 32.dp)) {
            if (q.isEmpty()) {
                item { EmptyState(Icons.Rounded.Search, "Search your events", "Find events by title, place or notes.") }
            } else if (results.isEmpty()) {
                item { EmptyState(Icons.Rounded.Search, "No results", "Nothing matches “$q”.") }
            }
            items(results, key = { it.event.id }) { o ->
                Column {
                    Text(
                        Dates.dayHeading(o.day, today),
                        Modifier.padding(start = 32.dp, top = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    EventRow(o, o.day, data) { nav.push(Page.EventPage(o.event.id, o.day)) }
                }
            }
        }
    }
}

/** Theme, week start, reminders, backups and the version. */
@Composable
fun SettingsPage(data: Data) {
    val nav = LocalNav.current
    val messages = LocalMessages.current
    val context = LocalContext.current
    val s = data.settings
    var refresh by remember { mutableIntStateOf(0) }
    var restoring by remember { mutableStateOf<Data?>(null) }
    var reminderMenu by remember { mutableStateOf(false) }
    val canNotify = remember(refresh) { Reminders.canNotify(context) }
    val canExact = remember(refresh) { Reminders.canScheduleExact(context) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) messages.show(if (Backup.export(context, uri)) "Backup saved" else "Couldn't save the backup")
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val d = Backup.read(context, uri)
            if (d == null) messages.show("That file isn't a Dayline backup") else restoring = d
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        PageBar("Settings", onBack = { nav.pop() })
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 32.dp)) {
            item { SectionHeader("Theme") }
            items(ThemeMode.entries) { mode ->
                ListRow(
                    mode.label,
                    leading = { RadioButton(selected = s.theme == mode, onClick = null) },
                    onClick = { Store.updateSettings { it.copy(theme = mode) } },
                    modifier = Modifier.semantics { contentDescription = "Theme: ${mode.label}" },
                )
            }
            item { SectionHeader("Calendar") }
            item {
                ListRow(
                    "Week starts on Sunday",
                    trailing = { Switch(checked = s.weekStartsSunday, onCheckedChange = { on -> Store.updateSettings { it.copy(weekStartsSunday = on) } }) },
                    onClick = { Store.updateSettings { it.copy(weekStartsSunday = !it.weekStartsSunday) } },
                )
            }
            item { SectionHeader("Reminders") }
            item {
                ListRow(
                    "Remind me before events",
                    detail = "Each event has its own reminder time",
                    trailing = { Switch(checked = s.reminders, onCheckedChange = { on -> Store.updateSettings { it.copy(reminders = on) } }) },
                    onClick = { Store.updateSettings { it.copy(reminders = !it.reminders) } },
                )
            }
            item {
                Box {
                    ListRow(
                        "Default reminder",
                        detail = ReminderChoices.label(s.defaultReminder) + " · for new events",
                        onClick = { reminderMenu = true },
                    )
                    ReminderMenu(reminderMenu, s.defaultReminder, { reminderMenu = false }) { m -> Store.updateSettings { it.copy(defaultReminder = m) } }
                }
            }
            if (s.reminders && !canNotify) {
                item {
                    ListRow(
                        "Notifications are off",
                        detail = "Tap to allow them, or reminders can't appear",
                        trailing = { Text("Allow", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge) },
                        onClick = {
                            if (Build.VERSION.SDK_INT >= 33 && !s.askedNotifications) {
                                Store.updateSettings { it.copy(askedNotifications = true) }
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                context.startActivity(
                                    Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName),
                                )
                            }
                        },
                    )
                }
            }
            if (s.reminders && !canExact && Build.VERSION.SDK_INT >= 31) {
                item {
                    ListRow(
                        "Reminders may be a few minutes late",
                        detail = "Tap to allow exact alarms for on-time reminders",
                        trailing = { Text("Allow", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge) },
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                            }
                        },
                    )
                }
            }
            item { SectionHeader("Backup") }
            item {
                ListRow(
                    "Export backup",
                    detail = "Save every event and calendar to a file",
                    onClick = { exporter.launch("dayline-backup-${LocalDate.now()}.json") },
                )
            }
            item {
                ListRow(
                    "Restore from backup",
                    detail = "Replace everything with a backup file",
                    onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                )
            }
            item { SectionHeader("About") }
            item {
                val version = remember {
                    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
                }
                ListRow("Dayline", detail = "Version $version · Your calendar, on your phone only")
            }
        }
    }
    restoring?.let { backup ->
        ConfirmDialog(
            "Restore this backup?",
            "It has ${backup.events.size} events in ${backup.calendars.size} calendars. Everything in Dayline now is replaced.",
            "Restore",
            onConfirm = {
                Store.replaceAll(backup)
                messages.show("Backup restored")
            },
            onDismiss = { restoring = null },
        )
    }
}
