package io.github.akrishna87.dayline.model

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

const val DEFAULT_CALENDAR_ID = "personal"

enum class Repeat(val label: String) {
    NONE("Doesn't repeat"),
    DAILY("Every day"),
    WEEKDAYS("Every weekday"),
    WEEKLY("Every week"),
    MONTHLY("Every month"),
    YEARLY("Every year"),
}

/** Colours for calendars. */
object CalendarColors {
    val choices = listOf(
        0xFFD1453B.toInt(), // red
        0xFFEB8909.toInt(), // orange
        0xFFE0B000.toInt(), // yellow
        0xFF299438.toInt(), // green
        0xFF158FAD.toInt(), // teal
        0xFF1A73E8.toInt(), // blue
        0xFF5B5BD6.toInt(), // indigo
        0xFFAF38EB.toInt(), // violet
        0xFFE05194.toInt(), // pink
        0xFF808080.toInt(), // grey
    )
    val Personal = 0xFF1A73E8.toInt()
    val Work = 0xFF299438.toInt()
}

/** How long before an event starts its reminder goes off. All-day events count from 9:00 on the day. */
object ReminderChoices {
    val minutes: List<Int?> = listOf(null, 0, 5, 10, 15, 30, 60, 120, 1440)

    fun label(m: Int?): String = when (m) {
        null -> "No reminder"
        0 -> "At the start"
        1440 -> "1 day before"
        else -> if (m % 60 == 0) "${m / 60} hour${if (m == 60) "" else "s"} before" else "$m minutes before"
    }

    /** The time of day all-day events "start" for reminders. */
    val AllDayAt: LocalTime = LocalTime.of(9, 0)
}

/** A list of events with a name and colour, like "Personal" or "Work". Hidden calendars' events aren't shown. */
data class EventCalendar(
    val id: String = newId(),
    val name: String,
    val color: Int = CalendarColors.choices[5],
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
            color = o.optInt("color", CalendarColors.choices[5]),
            visible = o.optBoolean("visible", true),
            createdAt = o.optLong("createdAt"),
        )
    }
}

data class Event(
    val id: String = newId(),
    val title: String,
    /** The day it starts (the first day, for a repeating event). */
    val date: LocalDate,
    /** The day it ends, for events over several days. Never before [date]. */
    val endDate: LocalDate = date,
    /** Null for an all-day event. */
    val start: LocalTime? = null,
    val end: LocalTime? = null,
    val location: String = "",
    val notes: String = "",
    val calendarId: String = DEFAULT_CALENDAR_ID,
    val repeat: Repeat = Repeat.NONE,
    /** The last day a repeating event can start on; null repeats forever. */
    val repeatUntil: LocalDate? = null,
    /** Minutes before the start; null for no reminder. */
    val reminder: Int? = 10,
    /** Days a repeating event was deleted on, one at a time. */
    val skipped: List<LocalDate> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
) {
    val allDay: Boolean get() = start == null

    /** Extra days after the first that each occurrence lasts: 0 for a one-day event. */
    val spanDays: Long get() = java.time.temporal.ChronoUnit.DAYS.between(date, endDate).coerceAtLeast(0)

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("date", date.toString())
        .put("endDate", endDate.toString())
        .put("start", start?.toString() ?: "")
        .put("end", end?.toString() ?: "")
        .put("location", location)
        .put("notes", notes)
        .put("calendarId", calendarId)
        .put("repeat", repeat.name)
        .put("repeatUntil", repeatUntil?.toString() ?: "")
        .put("reminder", reminder ?: -1)
        .put("skipped", JSONArray(skipped.map { it.toString() }))
        .put("createdAt", createdAt)

    companion object {
        fun fromJson(o: JSONObject): Event? {
            val date = o.optString("date").takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
            val endDate = o.optString("endDate").takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val start = o.optString("start").takeIf { it.isNotEmpty() }?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
            return Event(
                id = o.optString("id").ifEmpty { newId() },
                title = o.optString("title"),
                date = date,
                endDate = endDate?.takeIf { !it.isBefore(date) } ?: date,
                start = start,
                end = if (start == null) null else o.optString("end").takeIf { it.isNotEmpty() }?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
                location = o.optString("location"),
                notes = o.optString("notes"),
                calendarId = o.optString("calendarId").ifEmpty { DEFAULT_CALENDAR_ID },
                repeat = runCatching { Repeat.valueOf(o.optString("repeat")) }.getOrDefault(Repeat.NONE),
                repeatUntil = o.optString("repeatUntil").takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                reminder = o.optInt("reminder", 10).takeIf { it >= 0 },
                skipped = o.optJSONArray("skipped").strings().mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() },
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            )
        }
    }
}

enum class ThemeMode(val label: String) { SYSTEM("System default"), LIGHT("Light"), DARK("Dark") }

data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val weekStartsSunday: Boolean = false,
    val reminders: Boolean = true,
    /** The reminder new events start with. */
    val defaultReminder: Int? = 10,
    /** Dayline has asked once for permission to post notifications. */
    val askedNotifications: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("theme", theme.name)
        .put("weekStartsSunday", weekStartsSunday)
        .put("reminders", reminders)
        .put("defaultReminder", defaultReminder ?: -1)
        .put("askedNotifications", askedNotifications)

    companion object {
        fun fromJson(o: JSONObject?): Settings {
            val d = Settings()
            if (o == null) return d
            return Settings(
                theme = runCatching { ThemeMode.valueOf(o.optString("theme")) }.getOrDefault(d.theme),
                weekStartsSunday = o.optBoolean("weekStartsSunday", d.weekStartsSunday),
                reminders = o.optBoolean("reminders", d.reminders),
                defaultReminder = o.optInt("defaultReminder", 10).takeIf { it >= 0 },
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

    /** Events on calendars that are switched on. */
    val visibleEvents: List<Event>
        get() {
            val hidden = calendars.filter { !it.visible }.map { it.id }.toSet()
            return if (hidden.isEmpty()) events else events.filter { it.calendarId !in hidden }
        }

    fun toJson(): JSONObject = JSONObject()
        .put("version", 1)
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

private fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else List(length()) { getJSONObject(it) }

private fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else List(length()) { getString(it) }.filter { it.isNotBlank() }
