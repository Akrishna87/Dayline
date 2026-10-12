package io.github.akrishna87.dayline

import io.github.akrishna87.dayline.model.DEFAULT_CALENDAR_ID
import io.github.akrishna87.dayline.model.Data
import io.github.akrishna87.dayline.model.Dates
import io.github.akrishna87.dayline.model.Event
import io.github.akrishna87.dayline.model.EventCalendar
import io.github.akrishna87.dayline.model.Availability
import io.github.akrishna87.dayline.model.CalendarView
import io.github.akrishna87.dayline.model.Freq
import io.github.akrishna87.dayline.model.Recurrence
import io.github.akrishna87.dayline.model.Rule
import io.github.akrishna87.dayline.model.Settings
import io.github.akrishna87.dayline.model.ThemeMode
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.util.Locale

class ModelTest {
    @Test
    fun everythingSurvivesSavingAndLoading() {
        val gym = EventCalendar(name = "Gym", color = 0xFF299438.toInt(), visible = false)
        val data = Data(
            events = listOf(
                Event(
                    title = "Swim", date = LocalDate.of(2026, 10, 5), endDate = LocalDate.of(2026, 10, 5),
                    start = LocalTime.of(7, 0), end = LocalTime.of(8, 0), location = "Pool", notes = "Bring goggles",
                    calendarId = gym.id, rule = Rule(Freq.WEEKLY, 2, setOf(DayOfWeek.MONDAY), until = LocalDate.of(2026, 12, 31)), reminders = listOf(10, 30),
                    color = 0xFF8E24AA.toInt(), zone = "Europe/London", guests = listOf("sam@example.com"), availability = Availability.FREE,
                    skipped = listOf(LocalDate.of(2026, 10, 12)),
                ),
                Event(title = "Trip", date = LocalDate.of(2026, 11, 1), endDate = LocalDate.of(2026, 11, 4), reminders = emptyList(), rule = Rule(Freq.MONTHLY, weekdays = setOf(DayOfWeek.SUNDAY), nth = 1, count = 5)),
            ),
            calendars = listOf(EventCalendar.Personal, gym),
            settings = Settings(theme = ThemeMode.DARK, weekStartsSunday = true, reminders = false, defaultReminders = listOf(5, 60), defaultAllDayReminders = emptyList(),
                allDayReminderAt = LocalTime.of(8, 0), defaultDuration = 30, view = CalendarView.WEEK, showWeekNumbers = true, showWeekends = false, secondZone = "Asia/Kolkata", askedNotifications = true),
        )
        assertEquals(data, Data.fromJson(JSONObject(data.toJson().toString())))
    }

    @Test
    fun orphansAndBadDatesAreHandled() {
        val json = """{"events":[{"id":"a","title":"x","date":"2026-10-05","calendarId":"gone"},{"id":"b","title":"no date"},
            {"id":"c","title":"backwards","date":"2026-10-05","endDate":"2026-10-01"}]}"""
        val d = Data.fromJson(JSONObject(json))
        assertEquals(listOf("a", "c"), d.events.map { it.id })
        assertEquals(DEFAULT_CALENDAR_ID, d.events.first().calendarId)
        assertEquals(LocalDate.of(2026, 10, 5), d.events.last().endDate)
    }

    @Test
    fun hiddenCalendarsHideTheirEvents() {
        val hidden = EventCalendar(id = "h", name = "Hidden", visible = false)
        val d = Data(
            events = listOf(Event(title = "a", date = LocalDate.of(2026, 1, 1)), Event(title = "b", date = LocalDate.of(2026, 1, 1), calendarId = "h")),
            calendars = listOf(EventCalendar.Personal, hidden),
        )
        assertEquals(listOf("a"), d.visibleEvents.map { it.title })
    }

    @Test
    fun monthGridIsWholeWeeks() {
        // October 2026 starts on a Thursday and ends on a Saturday.
        val grid = Dates.monthGrid(YearMonth.of(2026, 10), DayOfWeek.MONDAY)
        assertEquals(5, grid.size)
        assertEquals(LocalDate.of(2026, 9, 28), grid.first().first())
        assertEquals(LocalDate.of(2026, 11, 1), grid.last().last())
        val sunday = Dates.monthGrid(YearMonth.of(2026, 10), DayOfWeek.SUNDAY)
        assertEquals(LocalDate.of(2026, 9, 27), sunday.first().first())
    }

    @Test
    fun timeLabels() {
        val day = LocalDate.of(2026, 10, 9)
        val meeting = Event(title = "m", date = day, start = LocalTime.of(9, 0), end = LocalTime.of(10, 30))
        assertEquals("09:00 – 10:30", Dates.timeLabel(Recurrence.occurrence(meeting, day, java.time.ZoneOffset.UTC), day, true, Locale.UK))
        val overnight = Event(title = "o", date = day, endDate = day.plusDays(2), start = LocalTime.of(20, 0), end = LocalTime.of(8, 0))
        val o = Recurrence.occurrence(overnight, day, java.time.ZoneOffset.UTC)
        assertEquals("From 20:00", Dates.timeLabel(o, day, true, Locale.UK))
        assertEquals("All day", Dates.timeLabel(o, day.plusDays(1), true, Locale.UK))
        assertEquals("Until 08:00", Dates.timeLabel(o, day.plusDays(2), true, Locale.UK))
        assertEquals("All day", Dates.timeLabel(Recurrence.occurrence(Event(title = "h", date = day), day), day, true, Locale.UK))
    }

    @Test
    fun firstRunHasTwoCalendarsAndNoEvents() {
        val d = Data.firstRun()
        assertEquals(listOf("Personal", "Work"), d.allCalendars.map { it.name })
        assertEquals(0, d.events.size)
        assertNull(d.event("anything"))
    }

    @Test
    fun daylineOneFilesCarryOver() {
        val v1 = """{"events":[{"id":"a","title":"Gym","date":"2026-10-05","repeat":"WEEKLY","repeatUntil":"2026-12-31","reminder":30},
            {"id":"b","title":"Quiet","date":"2026-10-05","reminder":-1}],"settings":{"defaultReminder":15}}"""
        val d = Data.fromJson(JSONObject(v1))
        assertEquals(Rule(Freq.WEEKLY, weekdays = setOf(DayOfWeek.MONDAY), until = LocalDate.of(2026, 12, 31)), d.event("a")!!.rule)
        assertEquals(listOf(30), d.event("a")!!.reminders)
        assertEquals(emptyList<Int>(), d.event("b")!!.reminders)
        assertEquals(listOf(15), d.settings.defaultReminders)
    }
}
