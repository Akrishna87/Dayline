package io.github.akrishna87.dayline.model

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * iCalendar (.ics, RFC 5545): events in and out. Exported files open in Google Calendar, Outlook
 * and Apple Calendar; invitations go out the same way, as an .ics file attached to an email.
 */
object Ics {
    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    /** A calendar file with [events]; [method] is PUBLISH for sharing, REQUEST for an invitation. */
    fun export(events: List<Event>, calendarName: String? = null, method: String = "PUBLISH"): String {
        val out = StringBuilder()
        fun line(s: String) {
            // Lines longer than 75 octets are folded, continuing with a space.
            var rest = s
            var first = true
            while (rest.toByteArray().size > 73) {
                var cut = minOf(rest.length, 73)
                while (rest.substring(0, cut).toByteArray().size > 73) cut--
                out.append(if (first) "" else " ").append(rest, 0, cut).append("\r\n")
                rest = rest.substring(cut)
                first = false
            }
            out.append(if (first) "" else " ").append(rest).append("\r\n")
        }
        line("BEGIN:VCALENDAR")
        line("VERSION:2.0")
        line("PRODID:-//Akrishna87//Dayline//EN")
        line("CALSCALE:GREGORIAN")
        line("METHOD:$method")
        if (calendarName != null) line("X-WR-CALNAME:" + escape(calendarName))
        val now = ZonedDateTime.now(ZoneOffset.UTC).format(stamp) + "Z"
        for (e in events) {
            line("BEGIN:VEVENT")
            line("UID:${e.id}@dayline")
            line("DTSTAMP:$now")
            line("SUMMARY:" + escape(e.title))
            if (e.allDay) {
                line("DTSTART;VALUE=DATE:" + DateTimeFormatter.BASIC_ISO_DATE.format(e.date))
                line("DTEND;VALUE=DATE:" + DateTimeFormatter.BASIC_ISO_DATE.format(e.endDate.plusDays(1)))
            } else {
                val zone = e.zone ?: ZoneId.systemDefault().id
                val end = e.end ?: e.start!!.plusHours(1)
                line("DTSTART;TZID=$zone:" + LocalDateTime.of(e.date, e.start).format(stamp))
                line("DTEND;TZID=$zone:" + LocalDateTime.of(e.endDate, end).format(stamp))
            }
            e.rule?.let { line("RRULE:" + it.toRRule(e.date)) }
            for (d in e.skipped) {
                if (e.allDay) line("EXDATE;VALUE=DATE:" + DateTimeFormatter.BASIC_ISO_DATE.format(d))
                else line("EXDATE;TZID=${e.zone ?: ZoneId.systemDefault().id}:" + LocalDateTime.of(d, e.start).format(stamp))
            }
            if (e.location.isNotBlank()) line("LOCATION:" + escape(e.location))
            if (e.notes.isNotBlank()) line("DESCRIPTION:" + escape(e.notes))
            if (e.availability == Availability.FREE) line("TRANSP:TRANSPARENT")
            for (g in e.guests) line("ATTENDEE;ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;RSVP=TRUE:mailto:$g")
            for (m in e.reminders) {
                line("BEGIN:VALARM")
                line("ACTION:DISPLAY")
                line("DESCRIPTION:" + escape(e.title))
                line("TRIGGER:" + if (m == 0) "PT0M" else "-PT${m}M")
                line("END:VALARM")
            }
            line("END:VEVENT")
        }
        line("END:VCALENDAR")
        return out.toString()
    }

    /** The events in an .ics file, put on [calendarId]. Repeats, exceptions, places, notes and notifications come across. */
    fun import(text: String, calendarId: String, zone: ZoneId = ZoneId.systemDefault()): List<Event> {
        val lines = unfold(text)
        val events = mutableListOf<Event>()
        var inEvent = false
        var inAlarm = false
        var props = mutableListOf<Pair<String, String>>()
        val alarms = mutableListOf<Int>()
        for (raw in lines) {
            val (name, params, value) = split(raw) ?: continue
            when {
                name == "BEGIN" && value == "VEVENT" -> { inEvent = true; props = mutableListOf(); alarms.clear() }
                name == "BEGIN" && value == "VALARM" -> inAlarm = true
                name == "END" && value == "VALARM" -> inAlarm = false
                name == "END" && value == "VEVENT" -> {
                    inEvent = false
                    toEvent(props, alarms.toList(), calendarId, zone)?.let { events += it }
                }
                inAlarm && name == "TRIGGER" -> parseTrigger(value)?.let { alarms += it }
                inEvent && !inAlarm -> props += (name + params) to value
            }
        }
        return events
    }

    private fun toEvent(props: List<Pair<String, String>>, alarms: List<Int>, calendarId: String, zone: ZoneId): Event? {
        fun get(name: String) = props.firstOrNull { it.first == name || it.first.startsWith("$name;") }
        val startP = get("DTSTART") ?: return null
        val start = when (val t = time(startP, zone)) {
            null -> return null
            else -> t
        }
        val endP = get("DTEND")
        val title = unescape(get("SUMMARY")?.second ?: "")
        val rrule = get("RRULE")?.second?.let { Rule.fromRRule(it) }
        val exdates = props.filter { it.first == "EXDATE" || it.first.startsWith("EXDATE;") }.flatMap { p ->
            p.second.split(',').mapNotNull { v -> time(p.first to v, zone)?.first }
        }
        val guests = props.filter { it.first.startsWith("ATTENDEE") }.map { it.second.removePrefix("mailto:").removePrefix("MAILTO:") }.filter { it.contains('@') }
        // Keep the UID as the id, so importing the same file twice doesn't add everything twice.
        val uid = get("UID")?.second?.trim().orEmpty()
        val base = Event(
            id = when {
                uid.endsWith("@dayline") -> uid.removeSuffix("@dayline")
                uid.isNotEmpty() -> "ics:" + uid
                else -> newId()
            },
            title = title,
            date = start.first,
            calendarId = calendarId,
            location = unescape(get("LOCATION")?.second ?: ""),
            notes = unescape(get("DESCRIPTION")?.second ?: ""),
            rule = rrule,
            skipped = exdates,
            reminders = alarms.distinct().sorted(),
            guests = guests,
            availability = if (get("TRANSP")?.second == "TRANSPARENT") Availability.FREE else Availability.BUSY,
        )
        return if (start.second == null) {
            // All day: DTEND is the day after the last day.
            val endDay = endP?.let { time(it, zone)?.first }?.minusDays(1)?.takeIf { !it.isBefore(start.first) } ?: start.first
            base.copy(endDate = endDay)
        } else {
            val end = endP?.let { time(it, zone) }?.takeIf { it.second != null }
                ?: get("DURATION")?.second?.let { d -> runCatching { Duration.parse(d.trim()) }.getOrNull() }
                    ?.let { LocalDateTime.of(start.first, start.second).plus(it).let { t -> t.toLocalDate() to t.toLocalTime() } }
                ?: (start.first to start.second!!.plusHours(1))
            base.copy(start = start.second, endDate = end.first, end = end.second)
        }
    }

    /** A DTSTART-like value as a date and time in [zone]; time is null for a date on its own. */
    private fun time(p: Pair<String, String>, zone: ZoneId): Pair<LocalDate, LocalTime?>? = runCatching {
        val (name, v) = p
        val value = v.trim()
        val upper = name.uppercase()
        if (upper.contains("VALUE=DATE") && !upper.contains("VALUE=DATE-TIME") || value.length == 8) {
            return@runCatching LocalDate.parse(value.take(8), DateTimeFormatter.BASIC_ISO_DATE) to null
        }
        val local = LocalDateTime.parse(value.take(15), stamp)
        val at = when {
            value.endsWith("Z") -> ZonedDateTime.of(local, ZoneOffset.UTC).withZoneSameInstant(zone).toLocalDateTime()
            upper.contains("TZID=") -> {
                val tz = name.substring(upper.indexOf("TZID=") + 5).substringBefore(';').trim('"')
                val z = runCatching { ZoneId.of(tz) }.getOrNull()
                if (z == null) local else ZonedDateTime.of(local, z).withZoneSameInstant(zone).toLocalDateTime()
            }
            else -> local
        }
        at.toLocalDate() to at.toLocalTime()
    }.getOrNull()

    /** "-PT15M", "-P1D", "PT0S", "-P1DT2H" as minutes before. */
    private fun parseTrigger(v: String): Int? {
        val m = Regex("([+-]?)P(?:(\\d+)W)?(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?").matchEntire(v.trim()) ?: return null
        fun g(i: Int) = m.groupValues[i].ifEmpty { "0" }.toInt()
        val minutes = g(2) * 10080 + g(3) * 1440 + g(4) * 60 + g(5)
        return if (m.groupValues[1] == "+" && minutes > 0) null else minutes
    }

    /** Joins folded lines (a line starting with a space or tab continues the one before). */
    private fun unfold(text: String): List<String> {
        val out = mutableListOf<String>()
        for (l in text.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
            if ((l.startsWith(" ") || l.startsWith("\t")) && out.isNotEmpty()) out[out.lastIndex] = out.last() + l.substring(1)
            else if (l.isNotEmpty()) out += l
        }
        return out
    }

    /** "DTSTART;TZID=Europe/London:20261005T090000" as name, ";TZID=Europe/London", value. */
    private fun split(line: String): Triple<String, String, String>? {
        // The value starts after the first colon that isn't inside a quoted parameter.
        var quoted = false
        for ((i, c) in line.withIndex()) {
            if (c == '"') quoted = !quoted
            if (c == ':' && !quoted) {
                val head = line.substring(0, i)
                val semi = head.indexOf(';')
                val name = (if (semi < 0) head else head.substring(0, semi)).uppercase()
                val params = if (semi < 0) "" else head.substring(semi)
                return Triple(name, params, line.substring(i + 1))
            }
        }
        return null
    }

    fun escape(s: String): String = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")

    fun unescape(s: String): String = s.replace("\\n", "\n").replace("\\N", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")
}
