package io.github.akrishna87.dayline.model

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

const val DEFAULT_CALENDAR_ID = "personal"

/** Colours for calendars and events: Google Calendar's eleven, plus a few more. */
object CalendarColors {
    /** Name and colour, in Google Calendar's order. */
    val named = listOf(
        "Tomato" to 0xFFD50000.toInt(),
        "Flamingo" to 0xFFE67C73.toInt(),
        "Tangerine" to 0xFFF4511E.toInt(),
        "Banana" to 0xFFF6BF26.toInt(),
        "Sage" to 0xFF33B679.toInt(),
        "Basil" to 0xFF0B8043.toInt(),
        "Peacock" to 0xFF039BE5.toInt(),
        "Blueberry" to 0xFF3F51B5.toInt(),
        "Lavender" to 0xFF7986CB.toInt(),
        "Grape" to 0xFF8E24AA.toInt(),
        "Graphite" to 0xFF616161.toInt(),
    )
    val choices = named.map { it.second } + listOf(0xFF1A73E8.toInt(), 0xFF00897B.toInt(), 0xFFAD1457.toInt())
    val Personal = 0xFF1A73E8.toInt()
    val Work = 0xFF0B8043.toInt()

    fun name(color: Int): String? = named.firstOrNull { it.second == color }?.first
}

/** How long before an event starts its notifications go off. All-day events count from a set time on the day. */
object ReminderChoices {
    val minutes: List<Int> = listOf(0, 5, 10, 15, 30, 60, 120, 1440, 2880, 10080)

    fun label(m: Int?): String = when {
        m == null -> "No notification"
        m == 0 -> "At the start"
        m % 10080 == 0 -> plural(m / 10080, "week") + " before"
        m % 1440 == 0 -> plural(m / 1440, "day") + " before"
        m % 60 == 0 -> plural(m / 60, "hour") + " before"
        else -> plural(m, "minute") + " before"
    }

    /** For an all-day event: "On the day at 09:00", "1 day before at 09:00". */
    fun allDayLabel(m: Int, at: LocalTime): String {
        val days = (m + 1439) / 1440
        val time = at.toString()
        return if (days == 0) "On the day at $time" else plural(days, "day") + " before at $time"
    }

    private fun plural(n: Int, one: String) = if (n == 1) "1 $one" else "$n ${one}s"
}

/** Whether an event shows you as busy (the default) or free. */
enum class Availability { BUSY, FREE }

/** A list of events with a name and colour, like "Personal" or "Work". Hidden calendars' events aren't shown. */
data class EventCalendar(
    val id: String = newId(),
    val name: String,
    val color: Int = CalendarColors.Personal,
    val visible: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("color", color)
        .put("visible", visible)
        .put("createdAt", createdAt)

    companion object {
        val Personal = EventCalendar(DEFAULT_CALENDAR_ID, "Personal", CalendarColors.Personal, createdAt = 0L)

        fun fromJson(o: JSONObject) = EventCalendar(
            id = o.optString("id").ifEmpty { newId() },
            name = o.optString("name"),
            color = o.optInt("color", CalendarColors.Personal),
            visible = o.optBoolean("visible", true),
            createdAt = o.optLong("createdAt"),
        )
    }
}

data class Event(
    val id: String = newId(),
    val title: String,
    /** The day it starts (the first day, for a repeating event), in its [zone]. */
    val date: LocalDate,
    /** The day it ends, for events over several days. Never before [date]. */
    val endDate: LocalDate = date,
    /** Null for an all-day event. */
    val start: LocalTime? = null,
    val end: LocalTime? = null,
    /** The time zone its times are in; null means wherever the phone is. */
    val zone: String? = null,
    val location: String = "",
    val notes: String = "",
    val calendarId: String = DEFAULT_CALENDAR_ID,
    /** Its own colour, instead of its calendar's. */
    val color: Int? = null,
    val rule: Rule? = null,
    /** Minutes before the start for each notification (for all-day events, before [Settings.allDayReminderAt] on the day). */
    val reminders: List<Int> = listOf(10),
    /** Days a repeating event was deleted (or moved out as an event of its own) on. */
    val skipped: List<LocalDate> = emptyList(),
    /** Email addresses of people invited. Invitations go out as .ics files by email. */
    val guests: List<String> = emptyList(),
    val availability: Availability = Availability.BUSY,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val allDay: Boolean get() = start == null

    /** Extra days after the first that each occurrence lasts: 0 for a one-day event. */
    val spanDays: Long get() = java.time.temporal.ChronoUnit.DAYS.between(date, endDate).coerceAtLeast(0)

    val zoneId: ZoneId? get() = zone?.let { runCatching { ZoneId.of(it) }.getOrNull() }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("date", date.toString())
        .put("endDate", endDate.toString())
        .put("start", start?.toString() ?: "")
        .put("end", end?.toString() ?: "")
        .put("zone", zone ?: "")
        .put("location", location)
        .put("notes", notes)
        .put("calendarId", calendarId)
        .put("color", color ?: 0)
        .put("rule", rule?.toJson() ?: JSONObject.NULL)
        .put("reminders", JSONArray(reminders))
        .put("skipped", JSONArray(skipped.map { it.toString() }))
        .put("guests", JSONArray(guests))
        .put("availability", availability.name)
        .put("createdAt", createdAt)

    companion object {
        fun fromJson(o: JSONObject): Event? {
            val date = o.optString("date").takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
            val endDate = o.optString("endDate").takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val start = o.optString("start").takeIf { it.isNotEmpty() }?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
            val until = o.optString("repeatUntil").takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            // Dayline 1 had a simple repeat and one reminder (-1 for none).
            val reminders = when {
                o.has("reminders") -> o.optJSONArray("reminders")?.let { a -> List(a.length()) { a.optInt(it, -1) } }.orEmpty().filter { it >= 0 }.distinct().sorted()
                o.has("reminder") -> listOf(o.optInt("reminder", 10)).filter { it >= 0 }
                else -> listOf(10)
            }
            return Event(
                id = o.optString("id").ifEmpty { newId() },
                title = o.optString("title"),
                date = date,
                endDate = endDate?.takeIf { !it.isBefore(date) } ?: date,
                start = start,
                end = if (start == null) null else o.optString("end").takeIf { it.isNotEmpty() }?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
                zone = o.optString("zone").takeIf { it.isNotEmpty() && runCatching { ZoneId.of(it) }.isSuccess },
                location = o.optString("location"),
                notes = o.optString("notes"),
                calendarId = o.optString("calendarId").ifEmpty { DEFAULT_CALENDAR_ID },
                color = o.optInt("color", 0).takeIf { it != 0 },
                rule = Rule.fromJson(o.optJSONObject("rule")) ?: Rule.fromLegacy(o.optString("repeat"), until, date),
                reminders = reminders,
                skipped = o.optJSONArray("skipped").strings().mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() },
                guests = o.optJSONArray("guests").strings(),
                availability = runCatching { Availability.valueOf(o.optString("availability")) }.getOrDefault(Availability.BUSY),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            )
        }
    }
}

enum class ThemeMode(val label: String) { SYSTEM("System default"), LIGHT("Light"), DARK("Dark") }

/** The views, as in Google Calendar. */
enum class CalendarView(val label: String, val days: Int) {
    SCHEDULE("Schedule", 0), DAY("Day", 1), THREE_DAYS("3 days", 3), WEEK("Week", 7), MONTH("Month", 0),
}

data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val weekStartsSunday: Boolean = false,
    val reminders: Boolean = true,
    /** Notifications new timed events start with, in minutes before. */
    val defaultReminders: List<Int> = listOf(10),
    /** Notifications new all-day events start with, in minutes before [allDayReminderAt] on the day. */
    val defaultAllDayReminders: List<Int> = listOf(0),
    /** The time of day all-day events' notifications are counted from. */
    val allDayReminderAt: LocalTime = LocalTime.of(9, 0),
    /** How long new events last, in minutes. */
    val defaultDuration: Int = 60,
    val view: CalendarView = CalendarView.MONTH,
    val showWeekNumbers: Boolean = false,
    val showWeekends: Boolean = true,
    /** A second time zone shown beside the hours in day and week views. */
    val secondZone: String? = null,
    /** Dayline has asked once for permission to post notifications. */
    val askedNotifications: Boolean = false,
) {
    /** Kept for Dayline 1's single default reminder (first of [defaultReminders]). */
    val defaultReminder: Int? get() = defaultReminders.firstOrNull()

    fun toJson(): JSONObject = JSONObject()
        .put("theme", theme.name)
        .put("weekStartsSunday", weekStartsSunday)
        .put("reminders", reminders)
        .put("defaultReminders", JSONArray(defaultReminders))
        .put("defaultAllDayReminders", JSONArray(defaultAllDayReminders))
        .put("allDayReminderAt", allDayReminderAt.toString())
        .put("defaultDuration", defaultDuration)
        .put("view", view.name)
        .put("showWeekNumbers", showWeekNumbers)
        .put("showWeekends", showWeekends)
        .put("secondZone", secondZone ?: "")
        .put("askedNotifications", askedNotifications)

    companion object {
        fun fromJson(o: JSONObject?): Settings {
            val d = Settings()
            if (o == null) return d
            fun ints(key: String) = o.optJSONArray(key)?.let { a -> List(a.length()) { a.optInt(it, -1) } }?.filter { it >= 0 }
            return Settings(
                theme = runCatching { ThemeMode.valueOf(o.optString("theme")) }.getOrDefault(d.theme),
                weekStartsSunday = o.optBoolean("weekStartsSunday", d.weekStartsSunday),
                reminders = o.optBoolean("reminders", d.reminders),
                defaultReminders = ints("defaultReminders") ?: if (o.has("defaultReminder")) listOf(o.optInt("defaultReminder", 10)).filter { it >= 0 } else d.defaultReminders,
                defaultAllDayReminders = ints("defaultAllDayReminders") ?: d.defaultAllDayReminders,
                allDayReminderAt = o.optString("allDayReminderAt").takeIf { it.isNotEmpty() }?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: d.allDayReminderAt,
                defaultDuration = o.optInt("defaultDuration", d.defaultDuration).coerceIn(5, 24 * 60),
                view = runCatching { CalendarView.valueOf(o.optString("view")) }.getOrDefault(d.view),
                showWeekNumbers = o.optBoolean("showWeekNumbers", d.showWeekNumbers),
                showWeekends = o.optBoolean("showWeekends", d.showWeekends),
                secondZone = o.optString("secondZone").takeIf { it.isNotEmpty() && runCatching { ZoneId.of(it) }.isSuccess },
                askedNotifications = o.optBoolean("askedNotifications", d.askedNotifications),
            )
        }
    }
}

/** Everything Dayline keeps, saved as one JSON file (and exported as the same JSON for backups). */
data class Data(
    val events: List<Event> = emptyList(),
    val calendars: List<EventCalendar> = listOf(EventCalendar.Personal),
    val settings: Settings = Settings(),
) {
    /** Calendars in the order they were made. There's always at least one. */
    val allCalendars: List<EventCalendar> get() = calendars.sortedBy { it.createdAt }.ifEmpty { listOf(EventCalendar.Personal) }

    fun calendar(id: String?): EventCalendar = calendars.firstOrNull { it.id == id } ?: allCalendars.first()

    fun event(id: String?): Event? = events.firstOrNull { it.id == id }

    /** The colour an event shows in: its own, or its calendar's. */
    fun colorOf(e: Event): Int = e.color ?: calendar(e.calendarId).color

    /** Events on calendars that are switched on. */
    val visibleEvents: List<Event>
        get() {
            val hidden = calendars.filter { !it.visible }.map { it.id }.toSet()
            return if (hidden.isEmpty()) events else events.filter { it.calendarId !in hidden }
        }

    fun toJson(): JSONObject = JSONObject()
        .put("version", 2)
        .put("events", JSONArray(events.map { it.toJson() }))
        .put("calendars", JSONArray(calendars.map { it.toJson() }))
        .put("settings", settings.toJson())

    companion object {
        fun fromJson(o: JSONObject): Data {
            val calendars = o.optJSONArray("calendars").objects().map(EventCalendar::fromJson).ifEmpty { listOf(EventCalendar.Personal) }
            val first = calendars.minBy { it.createdAt }.id
            val events = o.optJSONArray("events").objects().mapNotNull(Event::fromJson).map { e ->
                // An event whose calendar is gone moves to the first calendar.
                if (calendars.any { it.id == e.calendarId }) e else e.copy(calendarId = first)
            }
            return Data(events = events, calendars = calendars, settings = Settings.fromJson(o.optJSONObject("settings")))
        }

        /** What a new install starts with: two calendars and no events. */
        fun firstRun(): Data = Data(
            calendars = listOf(EventCalendar.Personal, EventCalendar(id = "work", name = "Work", color = CalendarColors.Work, createdAt = 1L)),
        )
    }
}

internal fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else List(length()) { opt(it) }.filterIsInstance<JSONObject>()

internal fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else List(length()) { optString(it) }.filter { it.isNotBlank() }
