package io.github.akrishna87.dayline.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Date sums and labels shared by every screen and the reminders. Plain Kotlin, so it is unit tested. */
object Dates {

    fun weekStart(settings: Settings): DayOfWeek = if (settings.weekStartsSunday) DayOfWeek.SUNDAY else DayOfWeek.MONDAY

    /** The first day of the week containing [day]. */
    fun startOfWeek(day: LocalDate, weekStart: DayOfWeek): LocalDate = day.minusDays(((day.dayOfWeek.value - weekStart.value + 7) % 7).toLong())

    /** The month as whole weeks: it starts on the week containing the 1st and ends on the week containing the last day. */
    fun monthGrid(month: YearMonth, weekStart: DayOfWeek): List<List<LocalDate>> {
        val first = startOfWeek(month.atDay(1), weekStart)
        val last = startOfWeek(month.atEndOfMonth(), weekStart).plusDays(6)
        val days = ChronoUnit.DAYS.between(first, last).toInt() + 1
        return List(days) { first.plusDays(it.toLong()) }.chunked(7)
    }

    fun weekdayLetters(weekStart: DayOfWeek, locale: Locale = Locale.getDefault()): List<String> =
        (0 until 7).map { weekStart.plus(it.toLong()).getDisplayName(TextStyle.NARROW, locale) }

    fun timeFormatter(is24Hour: Boolean, locale: Locale = Locale.getDefault()): DateTimeFormatter =
        DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a", locale)

    fun monthTitle(month: YearMonth, today: LocalDate, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern(if (month.year == today.year) "MMMM" else "MMMM yyyy", locale).format(month)

    /** "Today", "Tomorrow", "Yesterday", a weekday within the next week, otherwise "9 Oct" (with the year if it's another year). */
    fun dayLabel(day: LocalDate, today: LocalDate, locale: Locale = Locale.getDefault()): String {
        val days = ChronoUnit.DAYS.between(today, day)
        return when {
            days == 0L -> "Today"
            days == 1L -> "Tomorrow"
            days == -1L -> "Yesterday"
            days in 2..6 -> day.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
            day.year == today.year -> DateTimeFormatter.ofPattern("d MMM", locale).format(day)
            else -> DateTimeFormatter.ofPattern("d MMM yyyy", locale).format(day)
        }
    }

    /** A long heading for a day: "Today · Sat 3 Oct". */
    fun dayHeading(day: LocalDate, today: LocalDate, locale: Locale = Locale.getDefault()): String {
        val date = DateTimeFormatter.ofPattern(if (day.year == today.year) "EEE d MMM" else "EEE d MMM yyyy", locale).format(day)
        return when (ChronoUnit.DAYS.between(today, day)) {
            0L -> "Today · $date"
            1L -> "Tomorrow · $date"
            else -> date
        }
    }

    /** A date for a form field: "Sat 3 Oct", with the year when it isn't this year. */
    fun fieldDate(day: LocalDate, today: LocalDate, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern(if (day.year == today.year) "EEE d MMM" else "EEE d MMM yyyy", locale).format(day)

    /**
     * When an occurrence on [day] happens, for a list row: "All day", "09:00 – 10:00", or for an
     * event over several days, which part of it [day] is ("Until 17:00", "From 09:00", "All day").
     */
    fun timeLabel(o: Occurrence, day: LocalDate, is24Hour: Boolean, locale: Locale = Locale.getDefault()): String {
        val f = timeFormatter(is24Hour, locale)
        if (o.allDay) return "All day"
        val firstDay = day == o.startDay
        val lastDay = day == o.endDay
        return when {
            firstDay && lastDay -> "${f.format(o.start)} – ${f.format(o.end)}"
            firstDay -> "From ${f.format(o.start)}"
            lastDay -> "Until ${f.format(o.end)}"
            else -> "All day"
        }
    }

    /** ISO week number of [day] ("Week 42"). */
    fun weekNumber(day: LocalDate): Int = day.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)

    /** A sensible default start for a new event: the next whole hour. */
    fun nextHour(now: LocalTime): LocalTime = if (now.hour == 23) LocalTime.of(23, 0) else LocalTime.of(now.hour + 1, 0)

    /** An hour after [start], kept on the same day. */
    fun defaultEnd(start: LocalTime): LocalTime = if (start.hour >= 23) LocalTime.of(23, 59) else start.plusHours(1)
}
