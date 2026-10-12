package io.github.akrishna87.dayline

import android.content.Context
import io.github.akrishna87.dayline.model.Data
import io.github.akrishna87.dayline.model.Event
import io.github.akrishna87.dayline.model.EventCalendar
import io.github.akrishna87.dayline.model.Settings
import io.github.akrishna87.dayline.model.newId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executors

/** Which occurrences of a repeating event a change is for, as Google Calendar asks. */
enum class Scope(val label: String) { THIS("This event"), FOLLOWING("This and following events"), ALL("All events") }

/**
 * Dayline's events, calendars and settings, kept in memory and saved to one JSON file. The app,
 * the widgets and the reminders read the same [data], so a change anywhere shows up everywhere at once.
 */
object Store {
    private val state = MutableStateFlow(Data())
    val data: StateFlow<Data> = state

    private val writer = Executors.newSingleThreadExecutor()
    private var file: File? = null
    private lateinit var app: Context

    @Synchronized
    fun init(context: Context) {
        if (file != null) return
        app = context.applicationContext
        val f = File(app.filesDir, "dayline.json")
        file = f
        val loaded = runCatching { if (f.exists()) Data.fromJson(JSONObject(f.readText())) else null }.getOrNull()
        state.value = loaded ?: Data.firstRun()
        if (loaded == null || !runCatching { f.readText() }.getOrDefault("").contains("\"version\":2")) write(state.value)
    }

    fun update(transform: (Data) -> Data) {
        val next = synchronized(this) { transform(state.value).also { state.value = it } }
        write(next)
        Reminders.sync(app, next)
        Widgets.refresh(app)
    }

    /** Replaces everything, for restoring a backup. */
    fun replaceAll(d: Data) = update { d }

    private fun write(d: Data) {
        val f = file ?: return
        val json = d.toJson().toString()
        writer.execute {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(json)
            if (!tmp.renameTo(f)) {
                f.writeText(json)
                tmp.delete()
            }
        }
    }

    // Events

    fun saveEvent(e: Event) = update { d ->
        val i = d.events.indexOfFirst { it.id == e.id }
        d.copy(events = if (i < 0) d.events + e else d.events.toMutableList().also { it[i] = e })
    }

    fun addEvents(events: List<Event>) = update { d -> d.copy(events = d.events + events) }

    /** Puts an event back exactly as it was (for Undo), and takes away any split off it since. */
    fun restoreEvent(e: Event, splitOff: List<String> = emptyList()) = update { d ->
        val rest = d.events.filter { it.id != e.id && it.id !in splitOff }
        d.copy(events = rest + e)
    }

    fun deleteEvent(id: String) = update { d -> d.copy(events = d.events.filter { it.id != id }) }

    /** Deletes one occurrence of a repeating event. */
    fun skipOccurrence(id: String, day: LocalDate) = update { d ->
        d.copy(events = d.events.map { if (it.id == id) it.copy(skipped = (it.skipped + day).distinct()) else it })
    }

    /** Deletes an occurrence and every one after it; deleting from the first day deletes the event. */
    fun endRepeatBefore(id: String, day: LocalDate) = update { d ->
        val e = d.event(id) ?: return@update d
        val r = e.rule
        if (r == null || !day.isAfter(e.date)) {
            d.copy(events = d.events.filter { it.id != id })
        } else {
            d.copy(events = d.events.map { if (it.id == id) it.copy(rule = r.copy(until = day.minusDays(1), count = null)) else it })
        }
    }

    /**
     * Saves [changed], an edit of [original]'s occurrence on [day], for [scope]:
     * - THIS: that day is taken out of the series and [changed] becomes an event of its own.
     * - FOLLOWING: the series stops the day before, and [changed] starts a new series from there.
     * - ALL: the whole series changes; moving the date moves every occurrence by the same amount.
     * Returns the ids of any events split off, for Undo.
     */
    fun saveOccurrence(original: Event, day: LocalDate, changed: Event, scope: Scope): List<String> {
        val made = mutableListOf<String>()
        update { d ->
            when {
                original.rule == null || scope == Scope.ALL -> {
                    // A date moved within the edit (day → changed.date) moves the series' first date the same amount.
                    val shift = ChronoUnit.DAYS.between(day, changed.date)
                    val first = original.date.plusDays(shift)
                    val ev = if (original.rule == null) changed else changed.copy(
                        id = original.id,
                        date = first,
                        endDate = first.plusDays(ChronoUnit.DAYS.between(changed.date, changed.endDate)),
                        skipped = original.skipped.map { it.plusDays(shift) },
                    )
                    d.copy(events = d.events.map { if (it.id == original.id) ev else it })
                }
                scope == Scope.THIS -> {
                    val single = changed.copy(id = newId(), rule = null, skipped = emptyList(), createdAt = System.currentTimeMillis())
                    made += single.id
                    d.copy(events = d.events.map { if (it.id == original.id) it.copy(skipped = (it.skipped + day).distinct()) else it } + single)
                }
                else -> {
                    val r = original.rule!!
                    if (!day.isAfter(original.date)) {
                        d.copy(events = d.events.map { if (it.id == original.id) changed.copy(id = original.id) else it })
                    } else {
                        // Occurrences already used up by the old series come off a count.
                        val used = r.count?.let { r.starts(original.date, original.date, day.minusDays(1)).size }
                        val rest = changed.copy(
                            id = newId(),
                            rule = changed.rule?.let { nr -> if (used != null && nr.count != null) nr.copy(count = (nr.count - used).coerceAtLeast(1)) else nr },
                            skipped = original.skipped.filter { it.isAfter(day) },
                            createdAt = System.currentTimeMillis(),
                        )
                        made += rest.id
                        d.copy(events = d.events.map { if (it.id == original.id) it.copy(rule = r.copy(until = day.minusDays(1), count = null)) else it } + rest)
                    }
                }
            }
        }
        return made
    }

    // Calendars

    fun saveCalendar(c: EventCalendar) = update { d ->
        val i = d.calendars.indexOfFirst { it.id == c.id }
        d.copy(calendars = if (i < 0) d.calendars + c else d.calendars.toMutableList().also { it[i] = c })
    }

    /** Deletes a calendar and its events. The last calendar can't be deleted. */
    fun deleteCalendar(id: String) = update { d ->
        if (d.calendars.size <= 1) d else d.copy(calendars = d.calendars.filter { it.id != id }, events = d.events.filter { it.calendarId != id })
    }

    fun updateSettings(transform: (Settings) -> Settings) = update { d -> d.copy(settings = transform(d.settings)) }
}
