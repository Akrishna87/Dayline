package io.github.akrishna87.dayline

import io.github.akrishna87.dayline.model.Event
import io.github.akrishna87.dayline.model.Freq
import io.github.akrishna87.dayline.model.Occurrence
import io.github.akrishna87.dayline.model.Recurrence
import io.github.akrishna87.dayline.model.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class RecurrenceTest {
    private val utc = ZoneId.of("UTC")
    private fun d(m: Int, day: Int, y: Int = 2026) = LocalDate.of(y, m, day)
    private fun starts(e: Event, from: LocalDate, to: LocalDate) = Recurrence.startsBetween(e, from, to, utc)

    @Test
    fun aOneOffEventHappensOnce() {
        val e = Event(title = "Dentist", date = d(10, 14), start = LocalTime.of(9, 0))
        assertEquals(listOf(d(10, 14)), starts(e, d(10, 1), d(10, 31)))
        assertEquals(emptyList<LocalDate>(), starts(e, d(10, 15), d(10, 31)))
    }

    @Test
    fun weeklyOnChosenDays() {
        // 5 Oct 2026 is a Monday.
        val e = Event(title = "Gym", date = d(10, 5), rule = Rule(Freq.WEEKLY, weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)))
        assertEquals(listOf(d(10, 5), d(10, 8), d(10, 12), d(10, 15)), starts(e, d(10, 1), d(10, 15)))
        val other = e.copy(rule = Rule(Freq.WEEKLY, 2, setOf(DayOfWeek.MONDAY)))
        assertEquals(listOf(d(10, 5), d(10, 19)), starts(other, d(10, 1), d(10, 25)))
    }

    @Test
    fun farFutureQueriesSkipAhead() {
        val e = Event(title = "Pills", date = d(1, 1, 2000), rule = Rule.daily())
        assertEquals(listOf(d(10, 1), d(10, 2), d(10, 3)), starts(e, d(10, 1), d(10, 3)))
    }

    @Test
    fun weekdaysSkipTheWeekendButTheFirstDayAlwaysCounts() {
        // 10 Oct 2026 is a Saturday.
        val e = Event(title = "Standup", date = d(10, 10), rule = Rule.workdays())
        assertEquals(listOf(d(10, 10), d(10, 12), d(10, 13)), starts(e, d(10, 10), d(10, 13)))
    }

    @Test
    fun monthlyLikeGoogleCalendar() {
        // On the 31st: months without one are skipped.
        val e31 = Event(title = "Rent", date = d(1, 31), rule = Rule(Freq.MONTHLY))
        assertEquals(listOf(d(1, 31), d(3, 31), d(5, 31)), starts(e31, d(1, 1), d(6, 30)))
        // The last day of each month.
        val last = Event(title = "Payday", date = d(1, 31), rule = Rule(Freq.MONTHLY, monthDay = -1))
        assertEquals(listOf(d(1, 31), d(2, 28), d(3, 31)), starts(last, d(1, 1), d(3, 31)))
        // The 2nd Tuesday, and the last Friday.
        val second = Event(title = "Club", date = d(10, 13), rule = Rule(Freq.MONTHLY, weekdays = setOf(DayOfWeek.TUESDAY), nth = 2))
        assertEquals(listOf(d(10, 13), d(11, 10), d(12, 8)), starts(second, d(10, 1), d(12, 31)))
        val lastFri = Event(title = "Drinks", date = d(10, 30), rule = Rule(Freq.MONTHLY, weekdays = setOf(DayOfWeek.FRIDAY), nth = -1))
        assertEquals(listOf(d(10, 30), d(11, 27)), starts(lastFri, d(10, 1), d(11, 30)))
    }

    @Test
    fun yearlyAndLeapDays() {
        val e = Event(title = "Birthday", date = d(3, 2, 1990), rule = Rule.yearly())
        assertEquals(listOf(d(3, 2)), starts(e, d(1, 1), d(12, 31)))
        val leap = Event(title = "Leapling", date = d(2, 29, 2024), rule = Rule.yearly())
        assertEquals(listOf(d(2, 29, 2024), d(2, 29, 2028)), starts(leap, d(1, 1, 2024), d(12, 31, 2028)))
    }

    @Test
    fun endingOnADateOrAfterSomeTimes() {
        val until = Event(title = "Class", date = d(10, 1), rule = Rule(Freq.WEEKLY, until = d(10, 22)), skipped = listOf(d(10, 15)))
        assertEquals(listOf(d(10, 1), d(10, 8), d(10, 22)), starts(until, d(9, 1), d(12, 31)))
        val times = Event(title = "Course", date = d(10, 1), rule = Rule(Freq.WEEKLY, count = 3))
        assertEquals(listOf(d(10, 1), d(10, 8), d(10, 15)), starts(times, d(9, 1), d(12, 31)))
        // Counting starts at the first date even when looking later.
        assertEquals(listOf(d(10, 15)), starts(times, d(10, 10), d(12, 31)))
    }

    @Test
    fun multiDayEventsShowOnEveryDayTheyCover() {
        val trip = Event(title = "Trip", date = d(10, 9), endDate = d(10, 12))
        assertEquals(listOf("Trip"), Recurrence.on(listOf(trip), d(10, 11), utc).map { it.event.title })
        assertEquals(emptyList<Occurrence>(), Recurrence.on(listOf(trip), d(10, 13), utc))
        assertEquals(listOf(d(10, 10), d(10, 11), d(10, 12)), Recurrence.agenda(listOf(trip), d(10, 10), 7, utc).map { it.first })
    }

    @Test
    fun timeZonesMoveEventsOntoThePhonesClock() {
        // 09:00 in New York is 13:00 UTC (in October, New York is UTC-4).
        val call = Event(title = "Call", date = d(10, 14), start = LocalTime.of(9, 0), end = LocalTime.of(10, 0), zone = "America/New_York")
        val o = Recurrence.on(listOf(call), d(10, 14), utc).single()
        assertEquals(LocalDateTime.of(d(10, 14), LocalTime.of(13, 0)), o.start)
        assertEquals(LocalDateTime.of(d(10, 14), LocalTime.of(14, 0)), o.end)
        // 22:00 in New York is the next morning in UTC.
        val late = call.copy(start = LocalTime.of(22, 0), end = LocalTime.of(23, 0))
        assertEquals(d(10, 15), Recurrence.on(listOf(late), d(10, 15), utc).single().startDay)
        assertEquals(emptyList<Occurrence>(), Recurrence.on(listOf(late), d(10, 14), utc))
    }

    @Test
    fun aDayListsAllDayEventsFirstThenByTime() {
        val day = d(10, 14)
        val late = Event(title = "Dinner", date = day, start = LocalTime.of(19, 0), createdAt = 1)
        val early = Event(title = "Run", date = day, start = LocalTime.of(7, 0), createdAt = 2)
        val holiday = Event(title = "Holiday", date = day, createdAt = 3)
        assertEquals(listOf("Holiday", "Run", "Dinner"), Recurrence.agenda(listOf(late, early, holiday), day, 1, utc).single().second.map { it.event.title })
    }

    @Test
    fun nextOccurrence() {
        val e = Event(title = "Gym", date = d(10, 5), rule = Rule(Freq.WEEKLY, until = d(10, 19)))
        assertEquals(d(10, 12), Recurrence.next(e, d(10, 6)))
        assertEquals(d(10, 19), Recurrence.next(e, d(10, 19)))
        assertNull(Recurrence.next(e, d(10, 20)))
    }

    @Test
    fun notificationsGoOffBeforeTheNextOccurrence() {
        val e = Event(title = "Gym", date = d(10, 5), start = LocalTime.of(18, 0), rule = Rule(Freq.WEEKLY), reminders = listOf(30, 1440))
        fun next(at: LocalDateTime) = Recurrence.nextReminder(e, at, zone = utc)?.let { it.first to it.second.day }
        // The day before (1440) comes first, then 30 minutes before.
        assertEquals(LocalDateTime.of(d(10, 11), LocalTime.of(18, 0)) to d(10, 12), next(LocalDateTime.of(d(10, 10), LocalTime.of(9, 0))))
        assertEquals(LocalDateTime.of(d(10, 12), LocalTime.of(17, 30)) to d(10, 12), next(LocalDateTime.of(d(10, 12), LocalTime.of(9, 0))))
        assertEquals(LocalDateTime.of(d(10, 18), LocalTime.of(18, 0)) to d(10, 19), next(LocalDateTime.of(d(10, 12), LocalTime.of(17, 45))))
        // All-day events count from the set time on the day.
        val allDay = Event(title = "Holiday", date = d(10, 20), reminders = listOf(1440))
        assertEquals(LocalDateTime.of(d(10, 19), LocalTime.of(9, 0)), Recurrence.nextReminder(allDay, LocalDateTime.of(d(10, 12), LocalTime.of(9, 0)), LocalTime.of(9, 0), utc)?.first)
        assertNull(Recurrence.nextReminder(e.copy(reminders = emptyList()), LocalDateTime.of(d(10, 12), LocalTime.of(9, 0)), zone = utc))
    }
}
