package io.github.akrishna87.dayline.ui

import android.Manifest
import android.graphics.Color as AndroidColor
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.akrishna87.dayline.LaunchRequest
import io.github.akrishna87.dayline.MainActivity
import io.github.akrishna87.dayline.Reminders
import io.github.akrishna87.dayline.Store
import io.github.akrishna87.dayline.model.Data
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

enum class Tab(val label: String, val icon: ImageVector) {
    CALENDAR("Calendar", Icons.Rounded.CalendarMonth),
    SCHEDULE("Schedule", Icons.Rounded.ViewAgenda),
    CALENDARS("Calendars", Icons.Rounded.Layers),
}

/** Screens that open on top of the tabs. */
sealed interface Page {
    /** An event, opened at one of its occurrences ([day]); or a new one when [isNew]. */
    data class EventPage(
        val id: String,
        val day: LocalDate?,
        val isNew: Boolean = false,
        val startDay: LocalDate? = null,
        val startTime: java.time.LocalTime? = null,
        /** An event to start from (a duplicate, or one read from a file). */
        val template: io.github.akrishna87.dayline.model.Event? = null,
    ) : Page
    data object SettingsPage : Page
    data object Search : Page
}

class Nav(view: io.github.akrishna87.dayline.model.CalendarView) {
    var tab by mutableStateOf(Tab.CALENDAR)
    val pages = mutableStateListOf<Page>()

    /** Day, 3 days, week or month on the Calendar tab. */
    var view by mutableStateOf(if (view in GRID_VIEWS) view else io.github.akrishna87.dayline.model.CalendarView.MONTH)

    /** The day picked in the calendar; new events start on it. */
    var selected by mutableStateOf(LocalDate.now())
    var month by mutableStateOf(YearMonth.now())

    /** Shows [day] (and its month). */
    fun select(day: LocalDate) {
        selected = day
        month = YearMonth.from(day)
    }

    /** A new event at a tapped time in the day or week view. */
    fun newEventAt(day: LocalDate, time: java.time.LocalTime) = push(Page.EventPage(io.github.akrishna87.dayline.model.newId(), null, isNew = true, startDay = day, startTime = time))

    fun push(page: Page) {
        pages.add(page)
    }

    fun pop() {
        if (pages.isNotEmpty()) pages.removeAt(pages.lastIndex)
    }

    fun newEvent(day: LocalDate) = push(Page.EventPage(io.github.akrishna87.dayline.model.newId(), null, isNew = true, startDay = day))
}

val LocalNav = staticCompositionLocalOf<Nav> { error("No Nav") }
val LocalMessages = staticCompositionLocalOf<Messages> { error("No Messages") }

/** Asks once for permission to post notifications, the first time an event with a reminder is saved. */
val LocalAskNotifications = staticCompositionLocalOf<() -> Unit> { {} }

/** Short messages at the bottom of the screen, some with Undo. */
class Messages(private val scope: CoroutineScope, private val snackbar: SnackbarHostState) {
    fun show(text: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(text, duration = SnackbarDuration.Short)
        }
    }

    fun offerUndo(text: String, undo: () -> Unit) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            if (snackbar.showSnackbar(text, actionLabel = "Undo", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) undo()
        }
    }
}

@Composable
fun DaylineApp() {
    val data by Store.data.collectAsStateWithLifecycle()
    val dark = isDark(data.settings.theme)
    val activity = LocalContext.current as? ComponentActivity
    LaunchedEffect(dark) {
        // Keep the status and navigation bar icons readable when the app's theme differs from the phone's.
        val style = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT) else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        activity?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
    DaylineTheme(dark) { AppContent(data) }
}

@Composable
private fun AppContent(data: Data) {
    val context = LocalContext.current
    val nav = remember { Nav(Store.data.value.settings.view) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val messages = remember { Messages(scope, snackbar) }
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            val n = LocalDateTime.now()
            value = n
            delay(60_000L - n.second * 1000L - n.nano / 1_000_000L)
        }
    }
    val clock = Clock(now, DateFormat.is24HourFormat(context))

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val askNotifications: () -> Unit = {
        if (Build.VERSION.SDK_INT >= 33 && !Reminders.canNotify(context) && !Store.data.value.settings.askedNotifications) {
            Store.updateSettings { it.copy(askedNotifications = true) }
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    var importing by remember { mutableStateOf<List<io.github.akrishna87.dayline.model.Event>?>(null) }
    LaunchedEffect(Unit) {
        MainActivity.launch.collect { request ->
            when (request) {
                is LaunchRequest.OpenEvent -> if (Store.data.value.event(request.id) != null) nav.push(Page.EventPage(request.id, request.day))
                is LaunchRequest.OpenDay -> { nav.pages.clear(); nav.tab = Tab.CALENDAR; nav.select(request.day) }
                is LaunchRequest.NewEvent -> nav.newEvent(request.day)
                is LaunchRequest.ImportIcs -> {
                    val events = runCatching {
                        val text = context.contentResolver.openInputStream(request.uri)!!.use { it.readBytes().decodeToString() }
                        io.github.akrishna87.dayline.model.Ics.import(text, Store.data.value.allCalendars.first().id)
                    }.getOrNull()
                    if (events.isNullOrEmpty()) messages.show("No events found in that file") else importing = events
                }
                null -> {}
            }
            if (request != null) MainActivity.launch.value = null
        }
    }

    // Pages with something to save (the event editor) handle back themselves; that handler is
    // composed later, so it wins over these.
    BackHandler(enabled = nav.pages.isNotEmpty() || nav.tab != Tab.CALENDAR) {
        if (nav.pages.isNotEmpty()) nav.pop() else nav.tab = Tab.CALENDAR
    }

    CompositionLocalProvider(
        LocalNav provides nav,
        LocalMessages provides messages,
        LocalClock provides clock,
        LocalAskNotifications provides askNotifications,
    ) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.surface,
                bottomBar = { BottomBar(nav.tab) { nav.tab = it } },
                floatingActionButton = {
                    if (nav.tab != Tab.CALENDARS) {
                        Fab("New event") { nav.newEvent(if (nav.tab == Tab.CALENDAR) nav.selected else clock.today) }
                    }
                },
            ) { padding ->
                when (nav.tab) {
                    Tab.CALENDAR -> CalendarScreen(data, padding)
                    Tab.SCHEDULE -> ScheduleScreen(data, padding)
                    Tab.CALENDARS -> CalendarsScreen(data, padding)
                }
            }

            AnimatedContent(
                targetState = nav.pages.lastOrNull(),
                transitionSpec = { (slideInHorizontally { it / 8 } + fadeIn()) togetherWith fadeOut() },
                label = "page",
            ) { page ->
                if (page != null) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) { PageContent(page, data) }
                } else {
                    Box(Modifier)
                }
            }

            importing?.let { events ->
                ImportDialog(events, data, onDismiss = { importing = null })
            }

            SnackbarHost(
                snackbar,
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(bottom = 72.dp),
            )
        }
    }
}

@Composable
private fun PageContent(page: Page, data: Data) {
    when (page) {
        is Page.EventPage -> EventPage(page, data)
        Page.SettingsPage -> SettingsPage(data)
        Page.Search -> SearchPage(data)
    }
}

@Composable
private fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
            for (t in Tab.entries) {
                NavigationBarItem(
                    selected = t == selected,
                    onClick = { onSelect(t) },
                    icon = { Icon(t.icon, null) },
                    label = { Text(t.label) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                )
            }
        }
    }
}

@Composable
fun Fab(label: String, onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        // Label the button itself so screen readers (and the UI tests) find it.
        modifier = Modifier.semantics { contentDescription = label },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) { Icon(Icons.Rounded.Add, null) }
}
