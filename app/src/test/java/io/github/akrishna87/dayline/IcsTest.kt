package io.github.akrishna87.dayline

import io.github.akrishna87.dayline.model.Availability
import io.github.akrishna87.dayline.model.Event
import io.github.akrishna87.dayline.model.Freq
import io.github.akrishna87.dayline.model.Ics
import io.github.akrishna87.dayline.model.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

class IcsTest {
    private val utc = ZoneId.of("UTC")

    @Test
    fun rrulesBothWays() {
        val first = LocalDate.of(2026, 10, 13)
        val rules = listOf(
            Rule.daily(),
            Rule(Freq.DAILY, 3, count = 10),
            Rule(Freq.WEEKLY, 2, setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), until = LocalDate.of(2026, 12, 31)),
            Rule(Freq.MONTHLY, weekdays = setOf(DayOfWeek.TUESDAY), nth = 2),
            Rule(Freq.MONTHLY, weekdays = setOf(DayOfWeek.FRIDAY), nth = -1),
            Rule(Freq.MONTHLY, monthDay = -1),
            Rule(Freq.MONTHLY, monthDay = 15),
            Rule(Freq.YEARLY, 2),
        )
        for (r in rules) {
            val text = r.toRRule(first)
            val back = Rule.fromRRule(text)!!
            assertEquals(text, r.starts(first, first, first.plusYears(3)), back.starts(first, first, first.plusYears(3)))
        }
        assertEquals("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE;UNTIL=20261231", rules[2].toRRule(first))
        assertEquals("FREQ=MONTHLY;BYDAY=2TU", rules[3].toRRule(first))
        assertEquals(Rule(Freq.MONTHLY, weekdays = setOf(DayOfWeek.FRIDAY), nth = -1), Rule.fromRRule("RRULE:FREQ=MONTHLY;BYDAY=-1FR"))
        assertEquals(LocalDate.of(2026, 12, 31), Rule.fromRRule("FREQ=WEEKLY;BYDAY=MO;UNTIL=20261231T235959Z")!!.until)
        // Things it can't show are refused rather than misread.
        assertNull(Rule.fromRRule("FREQ=MONTHLY;BYDAY=MO;BYSETPOS=2"))
        assertNull(Rule.fromRRule("FREQ=HOURLY"))
    }

    @Test
    fun labels() {
        val first = LocalDate.of(2026, 10, 13)
        assertEquals("Weekly on Mon, Wed", Rule(Freq.WEEKLY, weekdays = setOf(DayOfWeek.WEDNESDAY, DayOfWeek.MONDAY)).label(first, Locale.ENGLISH))
        assertEquals("Every weekday (Mon to Fri)", Rule.workdays().label(first, Locale.ENGLISH))
        assertEquals("Monthly on the 2nd Tuesday", Rule(Freq.MONTHLY, weekdays = setOf(DayOfWeek.TUESDAY), nth = 2).label(first, Locale.ENGLISH))
        assertEquals("Every 3 days, 10 times", Rule(Freq.DAILY, 3, count = 10).label(first, Locale.ENGLISH))
        assertEquals("Annually on 13 October until 1 Jan 2030", Rule(Freq.YEARLY, until = LocalDate.of(2030, 1, 1)).label(first, Locale.ENGLISH))
    }

    @Test
    fun exportAndImportRoundTrip() {
        val events = listOf(
            Event(
                title = "Team; sync, weekly", date = LocalDate.of(2026, 10, 12), start = LocalTime.of(9, 30), end = LocalTime.of(10, 15), zone = "Europe/London",
                location = "Room 4", notes = "Bring notes\nand coffee", rule = Rule(Freq.WEEKLY, weekdays = setOf(DayOfWeek.MONDAY)),
                skipped = listOf(LocalDate.of(2026, 10, 19)), reminders = listOf(10, 1440), guests = listOf("sam@example.com"), availability = Availability.FREE,
            ),
            Event(title = "Holiday", date = LocalDate.of(2026, 12, 24), endDate = LocalDate.of(2026, 12, 26), reminders = emptyList()),
        )
        val text = Ics.export(events, "Work")
        assertTrue(text.lines().all { it.toByteArray().size <= 75 })
        val back = Ics.import(text, "cal", ZoneId.of("Europe/London"))
        assertEquals(2, back.size)
        val sync = back[0]
        assertEquals("Team; sync, weekly", sync.title)
        assertEquals(LocalTime.of(9, 30), sync.start)
        assertEquals(LocalTime.of(10, 15), sync.end)
        assertEquals("Room 4", sync.location)
        assertEquals("Bring notes\nand coffee", sync.notes)
        assertEquals(events[0].rule, sync.rule)
        assertEquals(listOf(LocalDate.of(2026, 10, 19)), sync.skipped)
        assertEquals(listOf(10, 1440), sync.reminders)
        assertEquals(listOf("sam@example.com"), sync.guests)
        assertEquals(Availability.FREE, sync.availability)
        assertEquals("cal", sync.calendarId)
        // Same ids back, so a second import of the file can skip what's already there.
        assertEquals(events.map { it.id }, back.map { it.id })
        assertEquals(back.map { it.id }, Ics.import(text, "cal").map { it.id })
        val holiday = back[1]
        assertNull(holiday.start)
        assertEquals(LocalDate.of(2026, 12, 26), holiday.endDate)
    }

    @Test
    fun readsWhatOtherCalendarsWrite() {
        val google = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            DTSTART:20261014T130000Z
            DTEND:20261014T140000Z
            SUMMARY:Dentist appointment with a very long title that goes on and on so the line ha
             s to be folded
            DESCRIPTION:Line one\nLine two
            BEGIN:VALARM
            TRIGGER:-P1D
            ACTION:DISPLAY
            END:VALARM
            END:VEVENT
            BEGIN:VEVENT
            DTSTART;TZID=America/New_York:20261015T090000
            DURATION:PT90M
            SUMMARY:Call
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val e = Ics.import(google, "c", utc)
        assertEquals("Dentist appointment with a very long title that goes on and on so the line has to be folded", e[0].title)
        assertEquals(LocalTime.of(13, 0), e[0].start)
        assertEquals("Line one\nLine two", e[0].notes)
        assertEquals(listOf(1440), e[0].reminders)
        // 09:00 in New York is 13:00 UTC; 90 minutes long.
        assertEquals(LocalTime.of(13, 0), e[1].start)
        assertEquals(LocalTime.of(14, 30), e[1].end)
    }
}
