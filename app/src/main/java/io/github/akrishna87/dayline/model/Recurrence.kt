package io.github.akrishna87.dayline.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** One time an event happens: the event itself, and the day this occurrence starts. */
data class Occurrence(val event: Event, val day: LocalDate) {
    /** The last day this occurrence covers (the same as [day] for a one-day event). */
    val endDay: LocalDate get() = day.plusDays(event.spanDays)

    /** When it starts; all-day events start at midnight. */
    val startsAt: LocalDateTime get() = LocalDateTime.of(day, event.start ?: java.time.LocalTime.MIDNIGHT)

    fun covers(d: LocalDate): Boolean = !d.isBefore(day) && !d.isAfter(endDay)
}

/** Works out when repeating events happen. Plain Kotlin, so it is unit tested. */
object Recurrence {
    /** The most occurrences one query returns for a single event, as a guard against runaway loops. */
    private const val LIMIT = 2000

    /**
     * The days [e] starts on whose occurrence touches [from]..[to] (both included): an
     * occurrence that started before [from] but is still going on counts.
     *
     * A monthly event on the 31st happens on the last day of shorter months, and a yearly one
     * on 29 February happens on the 28th in other years, because each occurrence is counted
     * from the first date rather than from the one before it.
     */
    fun startsBetween(e: Event, from: LocalDate, to: LocalDate): List<LocalDate> {
        if (to.isBefore(from)) return emptyList()
        val earliest = from.minusDays(e.spanDays)
        val last = listOfNotNull(to, e.repeatUntil).min()
        if (e.repeat == Repeat.NONE) {
            return if (!e.date.isBefore(earliest) && !e.date.isAfter(to)) listOf(e.date) else emptyList()
        }
        val out = ArrayList<LocalDate>()
        if (e.repeat == Repeat.WEEKDAYS) {
            // The first day always counts, even on a weekend; after that, Monday to Friday.
            var d = maxOf(e.date, earliest)
            while (!d.isAfter(last) && out.size < LIMIT) {
                if (d == e.date || (d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY)) {
                    if (d !in e.skipped) out += d
                }
                d = d.plusDays(1)
            }
            return out
        }
        var k = firstIndex(e, earliest)
        while (out.size < LIMIT) {
            val d = nth(e, k)
            if (d.isAfter(last)) break
            if (!d.isBefore(earliest) && d !in e.skipped) out += d
            k++
        }
        return out
    }

    /** Every occurrence of [events] that touches [from]..[to], in time order (all-day ones first on a day). */
    fun between(events: List<Event>, from: LocalDate, to: LocalDate): List<Occurrence> =
        events.flatMap { e -> startsBetween(e, from, to).map { Occurrence(e, it) } }.sortedWith(order)

    /** What's on [day], including events over several days that started earlier. */
    fun on(events: List<Event>, day: LocalDate): List<Occurrence> = between(events, day, day)

    /** Day by day from [from] for [days] days, leaving out days with nothing on. */
    fun agenda(events: List<Event>, from: LocalDate, days: Int): List<Pair<LocalDate, List<Occurrence>>> {
        val to = from.plusDays((days - 1).toLong())
        val all = between(events, from, to)
        return (0 until days).mapNotNull { i ->
            val day = from.plusDays(i.toLong())
            val on = all.filter { it.covers(day) }.sortedWith(dayOrder(day))
            if (on.isEmpty()) null else day to on
        }
    }

    /** The next occurrence on or after [from], or null if it has none left. */
    fun next(e: Event, from: LocalDate): LocalDate? {
        if (e.repeat == Repeat.NONE) return e.date.takeIf { !it.isBefore(from) }
        // A year ahead covers every kind of repeat; a series that has ended returns nothing.
        return startsBetween(e.copy(endDate = e.date), from, from.plusYears(1).plusDays(1)).firstOrNull()
    }

    /**
     * When the reminder for [e] should next go off after [now], with the occurrence it's for,
     * or null if it has no reminder or nothing left to remind about.
     */
    fun nextReminder(e: Event, now: LocalDateTime): Pair<LocalDateTime, LocalDate>? {
        val minutes = e.reminder ?: return null
        val startTime = e.start ?: ReminderChoices.AllDayAt
        // A reminder can be up to a day early, so start looking from tomorrow's occurrences back.
        var from = now.toLocalDate()
        repeat(4) {
            val day = next(e, from) ?: return null
            val at = LocalDateTime.of(day, startTime).minusMinutes(minutes.toLong())
            if (at.isAfter(now)) return at to day
            from = day.plusDays(1)
        }
        return null
    }

    /** All-day events first, then by start time, then the order they were added. */
    private val order: Comparator<Occurrence> = compareBy<Occurrence>({ it.day }, { !it.event.allDay }, { it.event.start }, { it.event.createdAt })

    /** For one day: events that last all of it first, then by the time they start that day. */
    fun dayOrder(day: LocalDate): Comparator<Occurrence> = compareBy<Occurrence>(
        { !(it.event.allDay || (it.day.isBefore(day) && it.endDay.isAfter(day))) },
        { if (it.day == day) it.event.start else java.time.LocalTime.MIDNIGHT },
        { it.event.createdAt },
    )

    /** The start of the [k]th occurrence, counted from the first. */
    private fun nth(e: Event, k: Long): LocalDate = when (e.repeat) {
        Repeat.NONE, Repeat.WEEKDAYS -> e.date.plusDays(k)
        Repeat.DAILY -> e.date.plusDays(k)
        Repeat.WEEKLY -> e.date.plusWeeks(k)
        Repeat.MONTHLY -> e.date.plusMonths(k)
        Repeat.YEARLY -> e.date.plusYears(k)
    }

    /** A safe place to start counting so as not to walk through years of past occurrences one by one. */
    private fun firstIndex(e: Event, earliest: LocalDate): Long {
        if (!earliest.isAfter(e.date)) return 0
        val k = when (e.repeat) {
            Repeat.DAILY -> ChronoUnit.DAYS.between(e.date, earliest)
            Repeat.WEEKLY -> ChronoUnit.WEEKS.between(e.date, earliest)
            Repeat.MONTHLY -> ChronoUnit.MONTHS.between(e.date, earliest)
            Repeat.YEARLY -> ChronoUnit.YEARS.between(e.date, earliest)
            else -> 0
        }
        return (k - 1).coerceAtLeast(0)
    }
}
