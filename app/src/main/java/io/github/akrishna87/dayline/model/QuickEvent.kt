package io.github.akrishna87.dayline.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** What was understood in an event's title. Fields are null when the text didn't mention them. */
data class ParsedEvent(
    val title: String,
    val date: LocalDate? = null,
    val start: LocalTime? = null,
    /** From a range like "2-3pm" or "9am to 11am". */
    val end: LocalTime? = null,
    val calendarId: String? = null,
    val repeat: Rule? = null,
    /** The character ranges that were recognised, so the text field can highlight them. */
    val matches: List<IntRange> = emptyList(),
)

/**
 * Reads an event typed in plain words: "Lunch with Sam friday 1pm", "Standup every weekday 9:30am #Work",
 * "Dentist dec 3 2-3pm".
 *
 * Dates: today, tomorrow (tmr), weekdays (monday or mon), next week, next friday, this weekend,
 * in 3 days, in 2 weeks, 5 jan, jan 5th, 2026-10-05. Times: 9am, 9:30pm, 21:00, at 5, noon, tonight,
 * and ranges: 2-3pm, 9am-11am, 9am to 11am. Calendar: #Name (an existing calendar; spaces in its
 * name are left out). Repeats: daily, every day, every 3 days, every weekday, every other week, every mon, wed,
 * every 15th, every last day, every 2nd tue, every month, every year.
 */
object QuickEvent {
    private val months = mapOf(
        "jan" to 1, "january" to 1, "feb" to 2, "february" to 2, "mar" to 3, "march" to 3,
        "apr" to 4, "april" to 4, "may" to 5, "jun" to 6, "june" to 6, "jul" to 7, "july" to 7,
        "aug" to 8, "august" to 8, "sep" to 9, "sept" to 9, "september" to 9, "oct" to 10, "october" to 10,
        "nov" to 11, "november" to 11, "dec" to 12, "december" to 12,
    )

    // "sat", "sun" and "tom" are left out on purpose: they're everyday words and names.
    private val weekdays = mapOf(
        "mon" to DayOfWeek.MONDAY, "monday" to DayOfWeek.MONDAY,
        "tue" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY, "tuesday" to DayOfWeek.TUESDAY,
        "wed" to DayOfWeek.WEDNESDAY, "weds" to DayOfWeek.WEDNESDAY, "wednesday" to DayOfWeek.WEDNESDAY,
        "thu" to DayOfWeek.THURSDAY, "thur" to DayOfWeek.THURSDAY, "thurs" to DayOfWeek.THURSDAY, "thursday" to DayOfWeek.THURSDAY,
        "fri" to DayOfWeek.FRIDAY, "friday" to DayOfWeek.FRIDAY,
        "saturday" to DayOfWeek.SATURDAY, "sunday" to DayOfWeek.SUNDAY,
    )

    private val clock = Regex("(\\d{1,2})(?::(\\d{2}))?(am|pm)?")
    private val dayNumber = Regex("(\\d{1,2})(st|nd|rd|th)?")

    private class Tok(val raw: String, val word: String, val range: IntRange)

    fun parse(
        input: String,
        today: LocalDate,
        now: LocalTime? = null,
        calendars: List<EventCalendar> = emptyList(),
        weekStart: DayOfWeek = DayOfWeek.MONDAY,
    ): ParsedEvent {
        val toks = Regex("\\S+").findAll(input).map {
            val raw = it.value.trimEnd(',', '.', ';', '!')
            Tok(raw, raw.lowercase(), it.range)
        }.toList()
        val used = BooleanArray(toks.size)
        val words = toks.map { it.word }
        fun word(i: Int): String? = toks.getOrNull(i)?.word
        fun take(from: Int, count: Int) {
            for (k in from until from + count) used[k] = true
        }

        var due: LocalDate? = null
        var time: LocalTime? = null
        var end: LocalTime? = null
        var calendarId: String? = null
        var repeat: Rule? = null

        var i = 0
        while (i < toks.size) {
            val w = toks[i].word
            val raw = toks[i].raw
            var n = 0

            if (calendarId == null && raw.length > 1 && raw.startsWith("#")) {
                val name = raw.substring(1)
                val c = calendars.firstOrNull { it.name.replace(" ", "").equals(name, true) }
                if (c != null) {
                    calendarId = c.id
                    n = 1
                }
            }

            if (n == 0 && repeat == null) {
                val single = when (w) {
                    "daily", "everyday" -> Rule.daily()
                    "weekly" -> Rule(Freq.WEEKLY)
                    "biweekly", "fortnightly" -> Rule(Freq.WEEKLY, 2)
                    "monthly" -> Rule(Freq.MONTHLY)
                    "yearly", "annually" -> Rule.yearly()
                    else -> null
                }
                if (single != null) {
                    repeat = single
                    n = 1
                } else if (w == "every" || w == "each") {
                    val r = Rule.parseAfterEvery(words.drop(i + 1))
                    if (r != null) {
                        repeat = r.first
                        n = 1 + r.second
                    }
                }
            }

            if (n == 0 && due == null) {
                val next = word(i + 1)
                when {
                    w == "today" || w == "tod" -> { due = today; n = 1 }
                    w == "tomorrow" || w == "tmr" || w == "tmrw" -> { due = today.plusDays(1); n = 1 }
                    w == "tonight" -> {
                        due = today
                        if (time == null) time = LocalTime.of(19, 0)
                        n = 1
                    }
                    weekdays.containsKey(w) -> { due = onOrAfter(today, weekdays.getValue(w)); n = 1 }
                    w == "next" && next == "week" -> { due = Dates.startOfWeek(today, weekStart).plusWeeks(1); n = 2 }
                    w == "next" && next == "month" -> { due = today.plusMonths(1).withDayOfMonth(1); n = 2 }
                    w == "next" && next != null && weekdays.containsKey(next) -> {
                        due = onOrAfter(Dates.startOfWeek(today, weekStart).plusWeeks(1), weekdays.getValue(next))
                        n = 2
                    }
                    w == "weekend" -> { due = onOrAfter(today, DayOfWeek.SATURDAY); n = 1 }
                    w == "this" && next == "weekend" -> { due = onOrAfter(today, DayOfWeek.SATURDAY); n = 2 }
                    w == "in" && next != null -> {
                        val amount = if (next == "a" || next == "an") 1 else next.toIntOrNull()
                        val unit = word(i + 2)?.removeSuffix("s")
                        if (amount != null && amount in 1..999) {
                            val d = when (unit) {
                                "day" -> today.plusDays(amount.toLong())
                                "week" -> today.plusWeeks(amount.toLong())
                                "month" -> today.plusMonths(amount.toLong())
                                "year" -> today.plusYears(amount.toLong())
                                else -> null
                            }
                            if (d != null) {
                                due = d
                                n = 3
                            }
                        }
                    }
                    Regex("\\d{4}-\\d{2}-\\d{2}").matches(w) -> {
                        runCatching { LocalDate.parse(w) }.getOrNull()?.let { due = it; n = 1 }
                    }
                    months.containsKey(w) && next != null && dayNumber.matches(next) -> {
                        val d = monthDay(months.getValue(w), dayNumber.matchEntire(next)!!.groupValues[1].toInt(), word(i + 2), today)
                        if (d != null) {
                            due = d.first
                            n = 2 + d.second
                        }
                    }
                    dayNumber.matches(w) && next != null && months.containsKey(next) -> {
                        val d = monthDay(months.getValue(next), dayNumber.matchEntire(w)!!.groupValues[1].toInt(), word(i + 2), today)
                        if (d != null) {
                            due = d.first
                            n = 2 + d.second
                        }
                    }
                }
            }

            if (n == 0 && time == null) {
                val next = word(i + 1)
                val range = timeRange(w, next, word(i + 2), word(i + 3))
                if (range != null) {
                    time = range.first
                    end = range.second
                    n = range.third
                }
            }

            if (n == 0 && time == null) {
                val next = word(i + 1)
                when {
                    w == "noon" || w == "midday" -> { time = LocalTime.NOON; n = 1 }
                    w == "at" && next != null -> {
                        val t = clockTime(next, word(i + 2), bare = true)
                        if (t != null) {
                            time = t.first
                            n = 1 + t.second
                        }
                    }
                    else -> {
                        val t = clockTime(w, next, bare = false)
                        if (t != null) {
                            time = t.first
                            n = t.second
                        }
                    }
                }
            }

            if (n > 0) {
                take(i, n)
                i += n
            } else {
                i++
            }
        }

        // A repeat decides the first date (the first that fits, from tomorrow if the time has passed today).
        val r = repeat
        if (r != null && due == null) {
            val t = time
            due = r.firstOnOrAfter(if (t != null && now != null && !t.isAfter(now)) today.plusDays(1) else today)
        }
        if (time != null && due == null) {
            due = if (now != null && !time.isAfter(now)) today.plusDays(1) else today
        }


        return ParsedEvent(
            title = toks.filterIndexed { k, _ -> !used[k] }.joinToString(" ") { input.substring(it.range) }.trim(),
            date = due,
            start = time,
            end = end,
            calendarId = calendarId,
            repeat = repeat,
            matches = toks.filterIndexed { k, _ -> used[k] }.map { it.range },
        )
    }

    /**
     * A time range: "2-3pm", "9am-11am", "9:30-10:15", or over words: "9am to 11am", "2 - 3pm".
     * A missing am/pm on the start is taken from the end ("2-3pm" is 14:00 to 15:00).
     * Returns the start, the end and how many words it used.
     */
    private fun timeRange(w: String, w1: String?, w2: String?, w3: String?): Triple<LocalTime, LocalTime, Int>? {
        val dash = w.indexOf('-')
        if (dash > 0 && dash < w.length - 1) {
            val (x, y) = w.substring(0, dash) to w.substring(dash + 1)
            // "chapters 2-3" isn't a time; one side needs am/pm or minutes.
            if (!looksLikeTime(x) && !looksLikeTime(y)) return null
            return range(x, y)?.let { Triple(it.first, it.second, 1) }
        }
        if ((w1 == "-" || w1 == "to" || w1 == "until") && w2 != null) {
            val endWords = if (w3 == "am" || w3 == "pm") 2 else 1
            val endText = if (endWords == 2) w2 + w3 else w2
            // "9 to 5" alone is too vague; one side needs am/pm or minutes.
            if (!looksLikeTime(w) && !looksLikeTime(endText)) return null
            return range(w, endText)?.let { Triple(it.first, it.second, 2 + endWords) }
        }
        return null
    }

    private fun looksLikeTime(w: String) = w.contains(':') || w.endsWith("am") || w.endsWith("pm")

    private fun range(a: String, b: String): Pair<LocalTime, LocalTime>? {
        val mb = clock.matchEntire(b) ?: return null
        val ma = clock.matchEntire(a) ?: return null
        val endSuffix = mb.groupValues[3]
        val startText = if (ma.groupValues[3].isEmpty() && endSuffix.isNotEmpty()) a + endSuffix else a
        val end = clockTime(b, null, bare = true)?.first ?: return null
        var start = clockTime(startText, null, bare = true)?.first ?: return null
        // "11-1pm": 11 is in the morning, not 23:00.
        if (start.isAfter(end) && ma.groupValues[3].isEmpty() && start.hour >= 12) start = start.minusHours(12)
        if (!end.isAfter(start)) return null
        return start to end
    }

    /** The first [day] on or after [from]. */
    fun onOrAfter(from: LocalDate, day: DayOfWeek): LocalDate = from.plusDays(((day.value - from.dayOfWeek.value + 7) % 7).toLong())

    /** A month and day, with an optional year after them. Without a year, a date already past means next year. */
    private fun monthDay(month: Int, day: Int, maybeYear: String?, today: LocalDate): Pair<LocalDate, Int>? {
        val year = maybeYear?.takeIf { it.length == 4 }?.toIntOrNull()
        val date = runCatching { LocalDate.of(year ?: today.year, month, day) }.getOrNull() ?: return null
        if (year != null) return date to 1
        return (if (date.isBefore(today)) date.plusYears(1) else date) to 0
    }

    /**
     * A time: "9am", "9:30pm", "21:00", or "9 am" over two words. With [bare] (after "at") a plain
     * hour counts too; 1 to 7 are taken as the afternoon. Returns the time and how many words it used.
     */
    private fun clockTime(w: String, next: String?, bare: Boolean): Pair<LocalTime, Int>? {
        val m = clock.matchEntire(w) ?: return null
        var hour = m.groupValues[1].toInt()
        val minute = m.groupValues[2].ifEmpty { "0" }.toInt()
        var suffix = m.groupValues[3]
        var words = 1
        if (suffix.isEmpty() && (next == "am" || next == "pm")) {
            suffix = next
            words = 2
        }
        val hasMinutes = m.groupValues[2].isNotEmpty()
        when {
            suffix.isNotEmpty() -> {
                if (hour !in 1..12) return null
                hour = when {
                    suffix == "am" && hour == 12 -> 0
                    suffix == "pm" && hour != 12 -> hour + 12
                    else -> hour
                }
            }
            hasMinutes -> if (hour > 23) return null
            bare -> {
                if (hour > 23) return null
                if (hour in 1..7) hour += 12
            }
            else -> return null
        }
        if (minute > 59) return null
        return LocalTime.of(hour, minute) to words
    }
}
