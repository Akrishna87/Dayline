package io.github.akrishna87.dayline.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * One time an event happens. [day] is the day it starts in the event's own time zone (the date a
 * repeat produced; used to skip or split one occurrence). [start] and [end] are where it falls on
 * the phone's clock; [end] is exclusive. All-day events run from midnight to midnight.
 */
data class Occurrence(val event: Event, val day: LocalDate, val start: LocalDateTime, val end: LocalDateTime) {
    val allDay: Boolean get() = event.allDay

    /** The first and last days it shows on. */
    val startDay: LocalDate get() = start.toLocalDate()
    val endDay: LocalDate
        get() = if (allDay) end.toLocalDate().minusDays(1) else maxOf(startDay, end.minusNanos(1).toLocalDate())

    /** Minutes it lasts, for a timed event. */
    val minutes: Long get() = java.time.Duration.between(start, end).toMinutes()

    fun covers(d: LocalDate): Boolean = !d.isBefore(startDay) && !d.isAfter(endDay)

    /** Over several days, or all day: drawn in the all-day row of day and week views. */
    val spansDays: Boolean get() = allDay || endDay.isAfter(startDay)
}

/** Works out when events happen, in the phone's time zone. Plain Kotlin, so it is unit tested. */
object Recurrence {
    /** The occurrence of [e] that starts on [day] (in the event's own time zone). */
    fun occurrence(e: Event, day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Occurrence {
        if (e.allDay) {
            return Occurrence(e, day, day.atStartOfDay(), day.plusDays(e.spanDays + 1).atStartOfDay())
        }
        val startLocal = LocalDateTime.of(day, e.start!!)
        var endLocal = LocalDateTime.of(day.plusDays(e.spanDays), e.end ?: e.start.plusHours(1))
        if (!endLocal.isAfter(startLocal)) endLocal = startLocal.plusMinutes(30)
        val ez = e.zoneId
        if (ez == null || ez == zone) return Occurrence(e, day, startLocal, endLocal)
        fun shift(t: LocalDateTime) = ZonedDateTime.of(t, ez).withZoneSameInstant(zone).toLocalDateTime()
        return Occurrence(e, day, shift(startLocal), shift(endLocal))
    }

    /** The days (in the event's zone) [e] starts on, from a little before [from] to a little after [to]. */
    private fun startDays(e: Event, from: LocalDate, to: LocalDate): List<LocalDate> {
        // A day either side covers time zone shifts; the span covers events still going on.
        val a = from.minusDays(e.spanDays + 1)
        val b = to.plusDays(1)
        val r = e.rule
        val days = if (r == null) listOf(e.date).filter { !it.isBefore(a) && !it.isAfter(b) } else r.starts(e.date, a, b)
        return if (e.skipped.isEmpty()) days else days.filter { it !in e.skipped }
    }

    /** Every occurrence of [e] that shows on a day in [from]..[to]. */
    fun occurrences(e: Event, from: LocalDate, to: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<Occurrence> =
        startDays(e, from, to).map { occurrence(e, it, zone) }.filter { !it.endDay.isBefore(from) && !it.startDay.isAfter(to) }

    /** The days [e] starts on whose occurrence touches [from]..[to]. */
    fun startsBetween(e: Event, from: LocalDate, to: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<LocalDate> =
        occurrences(e, from, to, zone).map { it.day }

    /** Every occurrence of [events] that touches [from]..[to], in time order (all-day ones first on a day). */
    fun between(events: List<Event>, from: LocalDate, to: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<Occurrence> =
        events.flatMap { occurrences(it, from, to, zone) }.sortedWith(order)

    /** What's on [day], including events over several days that started earlier. */
    fun on(events: List<Event>, day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<Occurrence> =
        between(events, day, day, zone).sortedWith(dayOrder(day))

    /** Day by day from [from] for [days] days, leaving out days with nothing on. */
    fun agenda(events: List<Event>, from: LocalDate, days: Int, zone: ZoneId = ZoneId.systemDefault()): List<Pair<LocalDate, List<Occurrence>>> {
        val to = from.plusDays((days - 1).toLong())
        val all = between(events, from, to, zone)
        return (0 until days).mapNotNull { i ->
            val day = from.plusDays(i.toLong())
            val on = all.filter { it.covers(day) }.sortedWith(dayOrder(day))
            if (on.isEmpty()) null else day to on
        }
    }

    /** The next day (in the event's zone) it starts on, on or after [from], or null if it has none left. */
    fun next(e: Event, from: LocalDate): LocalDate? {
        val r = e.rule ?: return e.date.takeIf { !it.isBefore(from) && it !in e.skipped }
        return r.starts(e.date, from, from.plusYears(2), limit = 400).firstOrNull { it !in e.skipped }
    }

    /**
     * When the next notification for [e] should go off after [now], with the occurrence it's for,
     * or null if it has none (or nothing left to notify about). All-day events count from [allDayAt].
     */
    fun nextReminder(e: Event, now: LocalDateTime, allDayAt: LocalTime = LocalTime.of(9, 0), zone: ZoneId = ZoneId.systemDefault()): Pair<LocalDateTime, Occurrence>? {
        if (e.reminders.isEmpty()) return null
        val longest = e.reminders.max().toLong()
        // Occurrences up to the longest notification ahead (and a little), in time order.
        val from = now.toLocalDate()
        val upcoming = occurrences(e, from.minusDays(1), from.plusDays(longest / 1440 + 400), zone).sortedBy { it.start }.take(60)
        var best: Pair<LocalDateTime, Occurrence>? = null
        for (o in upcoming) {
            val base = if (o.allDay) LocalDateTime.of(o.startDay, allDayAt) else o.start
            for (m in e.reminders) {
                val at = base.minusMinutes(m.toLong())
                if (at.isAfter(now) && (best == null || at.isBefore(best.first))) best = at to o
            }
            // Later occurrences can only be later, once one gave an answer past the longest notice.
            if (best != null && base.minusMinutes(longest).isAfter(best.first)) break
        }
        return best
    }

    /** All-day events first, then by start time, then the order they were added. */
    private val order: Comparator<Occurrence> = compareBy<Occurrence>({ it.startDay }, { !it.allDay }, { it.start }, { it.event.createdAt })

    /** For one day: events that last all of it first, then by the time they start that day. */
    fun dayOrder(day: LocalDate): Comparator<Occurrence> = compareBy<Occurrence>(
        { !(it.allDay || (it.startDay.isBefore(day) && it.endDay.isAfter(day))) },
        { if (it.startDay == day) it.start.toLocalTime() else LocalTime.MIN },
        { it.event.createdAt },
    )
}
