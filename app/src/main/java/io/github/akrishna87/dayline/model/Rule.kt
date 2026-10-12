package io.github.akrishna87.dayline.model

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class Freq { DAILY, WEEKLY, MONTHLY, YEARLY }

/**
 * How an event repeats, as in Google Calendar (and RFC 5545's RRULE): every N days, weeks (on
 * chosen weekdays), months (on a day, the last day, or the 2nd Tuesday) or years; ending never,
 * on a date, or after a number of times.
 *
 * Like Google Calendar, a monthly event on the 31st skips months without one, and a yearly event
 * on 29 February happens only in leap years.
 */
data class Rule(
    val freq: Freq,
    val interval: Int = 1,
    /** WEEKLY: the days (empty means the first date's day). MONTHLY with [nth]: the one weekday. */
    val weekdays: Set<DayOfWeek> = emptySet(),
    /** MONTHLY: a day of the month, 1 to 31, or -1 for the last day. Null keeps the first date's day. */
    val monthDay: Int? = null,
    /** MONTHLY: which weekday of the month, 1 to 4, or -1 for the last. */
    val nth: Int? = null,
    /** The last day it can start on. */
    val until: LocalDate? = null,
    /** How many times it happens in all. */
    val count: Int? = null,
) {
    /**
     * The days it starts on from [first] (its first date, which always counts) that fall in
     * [from]..[to], in order. Stops at [until], after [count] occurrences, or at [limit] results.
     */
    fun starts(first: LocalDate, from: LocalDate, to: LocalDate, limit: Int = 2000): List<LocalDate> {
        val out = ArrayList<LocalDate>()
        if (to.isBefore(from)) return out
        val last = listOfNotNull(to, until).min()
        if (first.isAfter(last)) return out
        // The first date always counts, whether or not it fits the pattern.
        var made = 1
        if (!first.isBefore(from)) out += first
        if (count != null && count <= 1) return out
        // With a count, every occurrence from the first has to be counted; otherwise skip ahead.
        var k = if (count != null) 0L else skipTo(first, from)
        var guard = 0
        while (guard++ < 100_000) {
            val period = periodStart(first, k)
            if (period.isAfter(last) && k > 0) break
            for (d in datesIn(first, period)) {
                if (!d.isAfter(first)) continue
                if (d.isAfter(last)) return out
                if (count != null) {
                    if (made >= count) return out
                    made++
                }
                if (!d.isBefore(from)) {
                    out += d
                    if (out.size >= limit) return out
                }
            }
            if (count != null && made >= count) return out
            k++
        }
        return out
    }

    /** The first date on or after [from] that fits the rule, for a new event typed with a repeat but no date. */
    fun firstOnOrAfter(from: LocalDate): LocalDate = when {
        freq == Freq.WEEKLY && weekdays.isNotEmpty() -> (0L..6L).map { from.plusDays(it) }.first { it.dayOfWeek in weekdays }
        freq == Freq.MONTHLY && nth != null && weekdays.isNotEmpty() ->
            (0L..2L).mapNotNull { nthWeekday(from.withDayOfMonth(1).plusMonths(it), nth, weekdays.first()) }.first { !it.isBefore(from) }
        freq == Freq.MONTHLY && monthDay != null -> (0L..12L).mapNotNull { k ->
            val m = from.withDayOfMonth(1).plusMonths(k)
            if (monthDay == -1) m.withDayOfMonth(m.lengthOfMonth()) else if (monthDay <= m.lengthOfMonth()) m.withDayOfMonth(monthDay) else null
        }.first { !it.isBefore(from) }
        else -> from
    }

    /** The first day of the [k]th period (day, week, month or year) after the one [first] is in. */
    private fun periodStart(first: LocalDate, k: Long): LocalDate = when (freq) {
        Freq.DAILY -> first.plusDays(k * interval)
        Freq.WEEKLY -> first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(k * interval)
        Freq.MONTHLY -> first.withDayOfMonth(1).plusMonths(k * interval)
        Freq.YEARLY -> first.withDayOfYear(1).plusYears(k * interval)
    }

    /** The occurrences inside one period, in order. */
    private fun datesIn(first: LocalDate, period: LocalDate): List<LocalDate> = when (freq) {
        Freq.DAILY -> listOf(period)
        Freq.WEEKLY -> (weekdays.ifEmpty { setOf(first.dayOfWeek) }).sortedBy { it.value }.map { period.plusDays((it.value - 1).toLong()) }
        Freq.MONTHLY -> when {
            nth != null -> listOfNotNull(nthWeekday(period, nth, weekdays.firstOrNull() ?: first.dayOfWeek))
            else -> {
                val day = monthDay ?: first.dayOfMonth
                val len = period.lengthOfMonth()
                listOfNotNull(if (day == -1) period.withDayOfMonth(len) else if (day <= len) period.withDayOfMonth(day) else null)
            }
        }
        Freq.YEARLY -> {
            val ok = !(first.monthValue == 2 && first.dayOfMonth == 29 && !period.isLeapYear)
            listOfNotNull(if (ok) period.withMonth(first.monthValue).withDayOfMonth(first.dayOfMonth) else null)
        }
    }

    /** A period index safely before [from], to skip years of past occurrences. */
    private fun skipTo(first: LocalDate, from: LocalDate): Long {
        if (!from.isAfter(first)) return 0
        val units = when (freq) {
            Freq.DAILY -> ChronoUnit.DAYS.between(first, from)
            Freq.WEEKLY -> ChronoUnit.WEEKS.between(first, from)
            Freq.MONTHLY -> ChronoUnit.MONTHS.between(first, from)
            Freq.YEARLY -> ChronoUnit.YEARS.between(first, from)
        }
        return (units / interval - 1).coerceAtLeast(0)
    }

    /** "Every 2 weeks on Mon, Wed, 10 times", "Monthly on the last Friday", "Annually until 1 Jan 2030". */
    fun label(first: LocalDate, locale: Locale = Locale.getDefault()): String {
        val n = interval
        val days = weekdays.ifEmpty { setOf(first.dayOfWeek) }.sortedBy { it.value }
        val base = when (freq) {
            Freq.DAILY -> if (n == 1) "Daily" else "Every $n days"
            Freq.WEEKLY -> when {
                days == WORKDAYS.sortedBy { it.value } && n == 1 -> "Every weekday (Mon to Fri)"
                n == 1 -> "Weekly on " + days.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
                else -> "Every $n weeks on " + days.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
            }
            Freq.MONTHLY -> {
                val unit = if (n == 1) "Monthly" else "Every $n months"
                when {
                    nth != null -> "$unit on the ${ordinal(nth)} ${(weekdays.firstOrNull() ?: first.dayOfWeek).getDisplayName(TextStyle.FULL, locale)}"
                    monthDay == -1 -> "$unit on the last day"
                    else -> "$unit on day ${monthDay ?: first.dayOfMonth}"
                }
            }
            Freq.YEARLY -> (if (n == 1) "Annually" else "Every $n years") + " on " + DateTimeFormatter.ofPattern("d MMMM", locale).format(first)
        }
        return base +
            (count?.let { if (it == 1) ", once" else ", $it times" } ?: "") +
            (until?.let { " until " + DateTimeFormatter.ofPattern("d MMM yyyy", locale).format(it) } ?: "")
    }

    /** The RRULE value, without the "RRULE:" prefix: FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE;UNTIL=20261231. */
    fun toRRule(first: LocalDate, untilSuffix: String = ""): String {
        val parts = mutableListOf("FREQ=${freq.name}")
        if (interval > 1) parts += "INTERVAL=$interval"
        when (freq) {
            Freq.WEEKLY -> parts += "BYDAY=" + weekdays.ifEmpty { setOf(first.dayOfWeek) }.sortedBy { it.value }.joinToString(",") { code(it) }
            Freq.MONTHLY -> when {
                nth != null -> parts += "BYDAY=$nth${code(weekdays.firstOrNull() ?: first.dayOfWeek)}"
                monthDay != null -> parts += "BYMONTHDAY=$monthDay"
            }
            else -> {}
        }
        if (count != null) parts += "COUNT=$count"
        else if (until != null) parts += "UNTIL=" + DateTimeFormatter.BASIC_ISO_DATE.format(until) + untilSuffix
        return parts.joinToString(";")
    }

    fun toJson(): JSONObject = JSONObject()
        .put("freq", freq.name)
        .put("interval", interval)
        .put("weekdays", JSONArray(weekdays.sortedBy { it.value }.map { it.name }))
        .put("monthDay", monthDay ?: 0)
        .put("nth", nth ?: 0)
        .put("until", until?.toString() ?: "")
        .put("count", count ?: 0)

    companion object {
        val WORKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

        fun daily() = Rule(Freq.DAILY)
        fun weekly(day: DayOfWeek) = Rule(Freq.WEEKLY, weekdays = setOf(day))
        fun workdays() = Rule(Freq.WEEKLY, weekdays = WORKDAYS)
        fun monthly(day: Int) = Rule(Freq.MONTHLY, monthDay = day)
        fun yearly() = Rule(Freq.YEARLY)

        /** The quick choices for an event that starts on [first], as Google Calendar offers them. */
        fun presets(first: LocalDate): List<Rule> = listOf(
            daily(),
            weekly(first.dayOfWeek),
            Rule(Freq.MONTHLY, weekdays = setOf(first.dayOfWeek), nth = nthOf(first)),
            monthly(first.dayOfMonth),
            yearly(),
            workdays(),
        )

        /** Which of its weekday [day] is in the month: 1 to 4, or -1 when it's the last. */
        fun nthOf(day: LocalDate): Int = if (day.plusWeeks(1).month != day.month) -1 else (day.dayOfMonth - 1) / 7 + 1

        fun ordinal(n: Int): String = when {
            n == -1 -> "last"
            n % 100 in 11..13 -> "${n}th"
            n % 10 == 1 -> "${n}st"
            n % 10 == 2 -> "${n}nd"
            n % 10 == 3 -> "${n}rd"
            else -> "${n}th"
        }

        fun nthWeekday(month: LocalDate, nth: Int, day: DayOfWeek): LocalDate? =
            if (nth == -1) month.with(TemporalAdjusters.lastInMonth(day))
            else month.with(TemporalAdjusters.dayOfWeekInMonth(nth, day)).takeIf { it.month == month.month }

        private val codes = mapOf(
            DayOfWeek.MONDAY to "MO", DayOfWeek.TUESDAY to "TU", DayOfWeek.WEDNESDAY to "WE", DayOfWeek.THURSDAY to "TH",
            DayOfWeek.FRIDAY to "FR", DayOfWeek.SATURDAY to "SA", DayOfWeek.SUNDAY to "SU",
        )

        fun code(d: DayOfWeek): String = codes.getValue(d)

        /**
         * Reads an RRULE (with or without "RRULE:"). Handles what calendars use day to day; returns
         * null for anything it can't represent (several BYMONTHDAYs, BYSETPOS, hourly rules…).
         */
        fun fromRRule(text: String): Rule? {
            val fields = text.trim().removePrefix("RRULE:").split(';').mapNotNull {
                val i = it.indexOf('=')
                if (i <= 0) null else it.substring(0, i).uppercase() to it.substring(i + 1)
            }.toMap()
            val freq = runCatching { Freq.valueOf(fields["FREQ"] ?: return null) }.getOrNull() ?: return null
            val known = setOf("FREQ", "INTERVAL", "BYDAY", "BYMONTHDAY", "UNTIL", "COUNT", "WKST", "BYMONTH")
            if (fields.keys.any { it !in known }) return null
            val interval = fields["INTERVAL"]?.toIntOrNull()?.coerceIn(1, 999) ?: 1
            val until = fields["UNTIL"]?.take(8)?.let { runCatching { LocalDate.parse(it, DateTimeFormatter.BASIC_ISO_DATE) }.getOrNull() }
            val count = fields["COUNT"]?.toIntOrNull()?.takeIf { it > 0 }
            val byDay = fields["BYDAY"]?.split(',')?.map { it.trim() }.orEmpty()
            val monthDay = fields["BYMONTHDAY"]?.let { v -> if (v.contains(',')) return null else v.toIntOrNull() }
            return when (freq) {
                Freq.WEEKLY -> {
                    val days = byDay.map { d -> codes.entries.firstOrNull { it.value == d.takeLast(2).uppercase() }?.key ?: return null }.toSet()
                    Rule(freq, interval, days, until = until, count = count)
                }
                Freq.MONTHLY -> {
                    if (byDay.size > 1) return null
                    val d = byDay.firstOrNull()
                    if (d != null) {
                        val m = Regex("([+-]?\\d)([A-Z]{2})").matchEntire(d.uppercase()) ?: return null
                        val day = codes.entries.firstOrNull { it.value == m.groupValues[2] }?.key ?: return null
                        val n = m.groupValues[1].toInt()
                        Rule(freq, interval, setOf(day), nth = if (n < 0) -1 else n, until = until, count = count)
                    } else {
                        Rule(freq, interval, monthDay = monthDay?.let { if (it < 0) -1 else it }, until = until, count = count)
                    }
                }
                Freq.DAILY -> if (byDay.isNotEmpty() || monthDay != null) null else Rule(freq, interval, until = until, count = count)
                Freq.YEARLY -> if (byDay.isNotEmpty()) null else Rule(freq, interval, until = until, count = count)
            }
        }

        fun fromJson(o: JSONObject?): Rule? {
            if (o == null) return null
            val freq = runCatching { Freq.valueOf(o.optString("freq")) }.getOrNull() ?: return null
            val days = o.optJSONArray("weekdays")?.let { a -> List(a.length()) { a.optString(it) } }.orEmpty()
                .mapNotNull { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }.toSet()
            return Rule(
                freq = freq,
                interval = o.optInt("interval", 1).coerceIn(1, 999),
                weekdays = days,
                monthDay = o.optInt("monthDay", 0).takeIf { it != 0 },
                nth = o.optInt("nth", 0).takeIf { it != 0 },
                until = o.optString("until").takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                count = o.optInt("count", 0).takeIf { it > 0 },
            )
        }

        private val ordinals = mapOf("first" to 1, "1st" to 1, "second" to 2, "2nd" to 2, "third" to 3, "3rd" to 3, "fourth" to 4, "4th" to 4, "last" to -1)
        private val numberWords = mapOf("one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "other" to 2)
        private val dayNames = mapOf(
            "mon" to DayOfWeek.MONDAY, "monday" to DayOfWeek.MONDAY,
            "tue" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY, "tuesday" to DayOfWeek.TUESDAY,
            "wed" to DayOfWeek.WEDNESDAY, "weds" to DayOfWeek.WEDNESDAY, "wednesday" to DayOfWeek.WEDNESDAY,
            "thu" to DayOfWeek.THURSDAY, "thur" to DayOfWeek.THURSDAY, "thurs" to DayOfWeek.THURSDAY, "thursday" to DayOfWeek.THURSDAY,
            "fri" to DayOfWeek.FRIDAY, "friday" to DayOfWeek.FRIDAY,
            "sat" to DayOfWeek.SATURDAY, "saturday" to DayOfWeek.SATURDAY,
            "sun" to DayOfWeek.SUNDAY, "sunday" to DayOfWeek.SUNDAY,
        )

        /** A weekday's name or short name, also in the plural ("mondays"). */
        fun weekday(word: String): DayOfWeek? {
            val w = word.lowercase().trimEnd(',', '.', ';')
            return dayNames[w] ?: dayNames[w.removeSuffix("s")]
        }

        /**
         * Reads the words after "every": "day", "3 days", "other week", "weekday", "mon, wed",
         * "monday and friday", "15th", "last day", "2nd tue", "month", "year". Returns the rule and
         * how many words it used, or null.
         */
        fun parseAfterEvery(words: List<String>): Pair<Rule, Int>? {
            fun w(i: Int) = words.getOrNull(i)?.lowercase()?.trimEnd(',', '.', ';')
            var i = 0
            var n = 1
            val first = w(0) ?: return null
            val count = first.toIntOrNull() ?: numberWords[first]
            if (count != null && count in 1..999) {
                n = count
                i = 1
            }
            val unit = w(i) ?: return null
            when (unit.removeSuffix("s")) {
                "day" -> return Rule(Freq.DAILY, n) to i + 1
                "week" -> return Rule(Freq.WEEKLY, n) to i + 1
                "month" -> return Rule(Freq.MONTHLY, n) to i + 1
                "year" -> return Rule(Freq.YEARLY, n) to i + 1
                "weekday", "workday" -> if (i == 0) return workdays() to 1
                "weekend" -> if (i == 0) return Rule(Freq.WEEKLY, weekdays = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) to 1
            }
            if (i != 0) return null
            if (unit == "last" && w(1) == "day") return Rule(Freq.MONTHLY, monthDay = -1) to 2
            val ord = ordinals[unit]
            val wd = w(1)?.let { weekday(it) }
            if (ord != null && wd != null) return Rule(Freq.MONTHLY, weekdays = setOf(wd), nth = ord) to 2
            Regex("(\\d{1,2})(st|nd|rd|th)").matchEntire(unit)?.let { m ->
                val day = m.groupValues[1].toInt()
                if (day in 1..31) return Rule(Freq.MONTHLY, monthDay = day) to 1
            }
            val days = mutableSetOf<DayOfWeek>()
            var j = 0
            while (true) {
                val word = w(j) ?: break
                val d = weekday(word)
                if (d != null) {
                    days += d
                    j++
                } else if ((word == "and" || word == "&") && days.isNotEmpty() && w(j + 1)?.let { weekday(it) } != null) {
                    j++
                } else {
                    break
                }
            }
            return if (days.isNotEmpty()) Rule(Freq.WEEKLY, weekdays = days) to j else null
        }

        /** Dayline 1's simple repeats, which started on [first]. */
        fun fromLegacy(name: String, until: LocalDate?, first: LocalDate): Rule? = when (name) {
            "DAILY" -> daily()
            "WEEKDAYS" -> workdays()
            "WEEKLY" -> weekly(first.dayOfWeek)
            "MONTHLY" -> monthly(first.dayOfMonth)
            "YEARLY" -> yearly()
            else -> null
        }?.copy(until = until)
    }
}
