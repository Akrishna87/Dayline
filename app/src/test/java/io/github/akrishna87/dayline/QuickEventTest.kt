package io.github.akrishna87.dayline

import io.github.akrishna87.dayline.model.EventCalendar
import io.github.akrishna87.dayline.model.QuickEvent
import io.github.akrishna87.dayline.model.Repeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class QuickEventTest {
    // A Monday.
    private val today = LocalDate.of(2026, 10, 12)
    private val work = EventCalendar(id = "work", name = "Work")

    private fun parse(s: String) = QuickEvent.parse(s, today, LocalTime.of(10, 0), listOf(work))

    @Test
    fun dayAndTime() {
        val p = parse("Lunch with Sam friday 1pm")
        assertEquals("Lunch with Sam", p.title)
        assertEquals(LocalDate.of(2026, 10, 16), p.date)
        assertEquals(LocalTime.of(13, 0), p.start)
        assertNull(p.end)
    }

    @Test
    fun timeRanges() {
        val a = parse("Dentist dec 3 2-3pm")
        assertEquals("Dentist", a.title)
        assertEquals(LocalDate.of(2026, 12, 3), a.date)
        assertEquals(LocalTime.of(14, 0), a.start)
        assertEquals(LocalTime.of(15, 0), a.end)

        val b = parse("Workshop tomorrow 9am to 11:30am")
        assertEquals("Workshop", b.title)
        assertEquals(LocalTime.of(9, 0), b.start)
        assertEquals(LocalTime.of(11, 30), b.end)

        val c = parse("Brunch 11-1pm")
        assertEquals(LocalTime.of(11, 0), c.start)
        assertEquals(LocalTime.of(13, 0), c.end)
    }

    @Test
    fun numbersThatArentTimesStayInTheTitle() {
        val p = parse("Read chapters 2-3")
        assertEquals("Read chapters 2-3", p.title)
        assertNull(p.start)
    }

    @Test
    fun repeatsAndCalendars() {
        val p = parse("Standup every weekday 9:30am #work")
        assertEquals("Standup", p.title)
        assertEquals(Repeat.WEEKDAYS, p.repeat)
        assertEquals(LocalTime.of(9, 30), p.start)
        assertEquals("work", p.calendarId)
        // 9:30 has passed at 10:00, so the first one is tomorrow.
        assertEquals(today.plusDays(1), p.date)
    }

    @Test
    fun aTimeAlreadyPastMeansTomorrow() {
        val p = parse("Call mum 8am")
        assertEquals(today.plusDays(1), p.date)
    }

    @Test
    fun plainTitlesAreLeftAlone() {
        val p = parse("Holiday")
        assertEquals("Holiday", p.title)
        assertNull(p.date)
        assertNull(p.start)
        assertNull(p.repeat)
    }
}
