package io.github.akrishna87.dayline

import android.content.Context
import io.github.akrishna87.dayline.model.Data
import io.github.akrishna87.dayline.model.Event
import io.github.akrishna87.dayline.model.EventCalendar
import io.github.akrishna87.dayline.model.Repeat
import io.github.akrishna87.dayline.model.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.concurrent.Executors

/**
 * Dayline's events, calendars and settings, kept in memory and saved to one JSON file. The app
 * and the reminders read the same [data], so a change anywhere shows up everywhere at once.
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
        if (loaded == null) write(state.value)
    }

    fun update(transform: (Data) -> Data) {
        val next = synchronized(this) { transform(state.value).also { state.value = it } }
        write(next)
        Reminders.sync(app, next)
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

    /** Puts an event back exactly as it was (for Undo). */
    fun restoreEvent(e: Event) = saveEvent(e)

    fun deleteEvent(id: String) = update { d -> d.copy(events = d.events.filter { it.id != id }) }

    /** Deletes one occurrence of a repeating event. */
    fun skipOccurrence(id: String, day: LocalDate) = update { d ->
        d.copy(events = d.events.map { if (it.id == id) it.copy(skipped = (it.skipped + day).distinct()) else it })
    }

    /** Deletes an occurrence and every one after it; deleting from the first day deletes the event. */
    fun endRepeatBefore(id: String, day: LocalDate) = update { d ->
        val e = d.event(id) ?: return@update d
        if (e.repeat == Repeat.NONE || !day.isAfter(e.date)) {
            d.copy(events = d.events.filter { it.id != id })
        } else {
            d.copy(events = d.events.map { if (it.id == id) it.copy(repeatUntil = day.minusDays(1)) else it })
        }
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
