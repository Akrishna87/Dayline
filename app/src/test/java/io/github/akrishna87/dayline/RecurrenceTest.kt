package io.github.akrishna87.dayline

import io.github.akrishna87.dayline.model.Event
import io.github.akrishna87.dayline.model.Occurrence
import io.github.akrishna87.dayline.model.Recurrence
import io.github.akrishna87.dayline.model.Repeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class RecurrenceTest {
    private fun d(m: Int, day: Int, y: Int = 2026) = LocalDate.of(y, m, day)

    @Test
    fun aOneOffEventHappensOnce() {
        val e = Event(title = "Dentist", date = d(10, 14), start = LocalTime.of(9, 0))
        assertEquals(listOf(d(10, 14)), Recurrence.startsBetween(e, d(10, 1), d(10, 31)))
        assertEquals(emptyList<LocalDate>(), Recurrence.startsBetween(e, d(10, 15), d(10, 31)))
    }

    @Test
    fun weeklyRepeatsFromTheFirstDay() {
        val e = Event(title = "Gym", date = d(10, 5), repeat = Repeat.WEEKLY)
        assertEquals(listOf(d(10, 5), d(10, 12), d(10, 19), d(10, 26)), Recurrence.startsBetween(e, d(10, 1), d(10, 31)))
        // Nothing before it started.
        assertEquals(emptyList<LocalDate>(), Recurrence.startsBetween(e, d(9, 1), d(9, 30)))
    }

    @Test
    fun farFutureQueriesSkipAhead() {
        val e = Event(title = "Pills", date = d(1, 1, 2000), repeat = Repeat.DAILY)
        val days = Recurrence.startsBetween(e, d(10, 1), d(10, 3))
        assertEquals(listOf(d(10, 1), d(10, 2), d(10, 3)), days)
    }

    @Test
    fun weekdaysSkipTheWeekend() {
        // 9 Oct 2026 is a Friday.
        val e = Event(title = "Standup", date = d(10, 9), repeat = Repeat.WEEKDAYS)
        assertEquals(listOf(d(10, 9), d(10, 12), d(10, 13)), Recurrence.startsBetween(e, d(10, 9), d(10, 13)))
    }

    @Test
    fun monthlyOnThe31stUsesTheLastDayOfShortMonths() {
        val e = Event(title = "Rent", date = d(1, 31), repeat = Repeat.MONTHLY)
        assertEquals(listOf(d(1, 31), d(2, 28), d(3, 31), d(4, 30)), Recurrence.startsBetween(e, d(1, 1), d(4, 30)))
    }

    @Test
    fun yearlyBirthdays() {
        val e = Event(title = "Birthday", date = d(3, 2, 1990), repeat = Repeat.YEARLY)
        assertEquals(listOf(d(3, 2)), Recurrence.startsBetween(e, d(1, 1), d(12, 31)))
    }

    @Test
    fun repeatsStopAtTheEndDateAndSkipDeletedDays() {
        val e = Event(title = "Class", date = d(10, 1), repeat = Repeat.WEEKLY, repeatUntil = d(10, 22), skipped = listOf(d(10, 15)))
        assertEquals(listOf(d(10, 1), d(10, 8), d(10, 22)), Recurrence.startsBetween(e, d(9, 1), d(12, 31)))
    }

    @Test
    fun multiDayEventsShowOnEveryDayTheyCover() {
        val trip = Event(title = "Trip", date = d(10, 9), endDate = d(10, 12))
        assertEquals(listOf("Trip"), Recurrence.on(listOf(trip), d(10, 11)).map { it.event.title })
        assertEquals(emptyList<Occurrence>(), Recurrence.on(listOf(trip), d(10, 13)))
        val agenda = Recurrence.agenda(listOf(trip), d(10, 10), 7)
        assertEquals(listOf(d(10, 10), d(10, 11), d(10, 12)), agenda.map { it.first })
    }

    @Test
    fun aDayListsAllDayEventsFirstThenByTime() {
        val day = d(10, 14)
        val late = Event(title = "Dinner", date = day, start = LocalTime.of(19, 0), createdAt = 1)
        val early = Event(title = "Run", date = day, start = LocalTime.of(7, 0), createdAt = 2)
        val holiday = Event(title = "Holiday", date = day, createdAt = 3)
        assertEquals(listOf("Holiday", "Run", "Dinner"), Recurrence.agenda(listOf(late, early, holiday), day, 1).single().second.map { it.event.title })
    }

    @Test
    fun nextOccurrence() {
        val e = Event(title = "Gym", date = d(10, 5), repeat = Repeat.WEEKLY, repeatUntil = d(10, 19))
        assertEquals(d(10, 12), Recurrence.next(e, d(10, 6)))
        assertEquals(d(10, 19), Recurrence.next(e, d(10, 19)))
        assertNull(Recurrence.next(e, d(10, 20)))
    }

    @Test
    fun remindersGoOffBeforeTheNextOccurrence() {
        val e = Event(title = "Gym", date = d(10, 5), start = LocalTime.of(18, 0), repeat = Repeat.WEEKLY, reminder = 30)
        // Before this week's reminder: this week's.
        assertEquals(LocalDateTime.of(d(10, 12), LocalTime.of(17, 30)) to d(10, 12), Recurrence.nextReminder(e, LocalDateTime.of(d(10, 12), LocalTime.of(9, 0))))
        // After it: next week's.
        assertEquals(LocalDateTime.of(d(10, 19), LocalTime.of(17, 30)) to d(10, 19), Recurrence.nextReminder(e, LocalDateTime.of(d(10, 12), LocalTime.of(17, 45))))
        // All-day events count from 9:00; "1 day before" lands the day before.
        val allDay = Event(title = "Holiday", date = d(10, 20), reminder = 1440)
        assertEquals(LocalDateTime.of(d(10, 19), LocalTime.of(9, 0)) to d(10, 20), Recurrence.nextReminder(allDay, LocalDateTime.of(d(10, 12), LocalTime.of(9, 0))))
        assertNull(Recurrence.nextReminder(e.copy(reminder = null), LocalDateTime.of(d(10, 12), LocalTime.of(9, 0))))
        assertTrue(Recurrence.nextReminder(Event(title = "Past", date = d(1, 1), start = LocalTime.NOON), LocalDateTime.of(d(10, 12), LocalTime.NOON)) == null)
    }
}
