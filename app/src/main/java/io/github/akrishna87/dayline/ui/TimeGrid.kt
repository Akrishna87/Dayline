package io.github.akrishna87.dayline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.akrishna87.dayline.Scope
import io.github.akrishna87.dayline.Store
import io.github.akrishna87.dayline.model.Data
import io.github.akrishna87.dayline.model.Dates
import io.github.akrishna87.dayline.model.Occurrence
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private val HOUR = 56.dp
private val GUTTER = 52.dp

/** An event laid out in a day column: its occurrence and which of how many side-by-side lanes it takes. */
private data class Placed(val o: Occurrence, val lane: Int, val lanes: Int, val startMin: Int, val endMin: Int)

/**
 * Side by side when they overlap: events are grouped into clusters that overlap, and each takes
 * the first free lane in its cluster, as calendars do.
 */
private fun layout(day: LocalDate, list: List<Occurrence>): List<Placed> {
    fun minutesOf(o: Occurrence): Pair<Int, Int> {
        val s = if (o.startDay.isBefore(day)) 0 else o.start.toLocalTime().toSecondOfDay() / 60
        val e = if (o.end.toLocalDate().isAfter(day)) 24 * 60 else o.end.toLocalTime().toSecondOfDay() / 60
        return s to maxOf(e, s + 20)
    }
    val items = list.map { it to minutesOf(it) }.sortedWith(compareBy({ it.second.first }, { -it.second.second }))
    val out = mutableListOf<Placed>()
    var cluster = mutableListOf<Triple<Occurrence, Pair<Int, Int>, Int>>()
    var clusterEnd = -1
    fun flush() {
        val lanes = (cluster.maxOfOrNull { it.third } ?: -1) + 1
        for ((o, m, lane) in cluster) out += Placed(o, lane, lanes, m.first, m.second)
        cluster = mutableListOf()
    }
    for ((o, m) in items) {
        if (m.first >= clusterEnd && cluster.isNotEmpty()) flush()
        val used = cluster.filter { it.second.second > m.first }.map { it.third }.toSet()
        val lane = generateSequence(0) { it + 1 }.first { it !in used }
        cluster += Triple(o, m, lane)
        clusterEnd = maxOf(clusterEnd, m.second)
    }
    if (cluster.isNotEmpty()) flush()
    return out
}

/**
 * Day, 3-day and week views: an all-day row, then hours down the side with events placed in
 * time. Tap an empty hour to make an event there; long-press an event and drag it to move it.
 */
@Composable
fun TimeGrid(days: List<LocalDate>, data: Data) {
    val nav = LocalNav.current
    val clock = LocalClock.current
    val messages = LocalMessages.current
    val zone = ZoneId.systemDefault()
    val events = data.visibleEvents
    val occurrences = remember(events, days.first(), days.last()) {
        io.github.akrishna87.dayline.model.Recurrence.between(events, days.first(), days.last(), zone)
    }
    val second = data.settings.secondZone?.let { runCatching { ZoneId.of(it) }.getOrNull() }
    val gutter = if (second != null) GUTTER + 40.dp else GUTTER
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val hourPx = with(density) { HOUR.toPx() }
    // Open scrolled to an hour before now (or 8:00).
    LaunchedEffect(days.first()) {
        val h = if (clock.today in days) (clock.now.hour - 1).coerceAtLeast(0) else 8
        scroll.scrollTo((h * hourPx).roundToInt())
    }
    var moving by remember { mutableStateOf<Pair<Occurrence, Long>?>(null) }

    Column(Modifier.fillMaxSize()) {
        // Day headings.
        Row(Modifier.fillMaxWidth().padding(end = 4.dp)) {
            Box(Modifier.width(gutter)) {
                if (data.settings.showWeekNumbers) {
                    Text("W${Dates.weekNumber(days.first())}", Modifier.align(Alignment.Center), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            for (d in days) {
                val isToday = d == clock.today
                Column(
                    Modifier.weight(1f).clickable { nav.selected = d; if (days.size > 1) nav.view = io.github.akrishna87.dayline.model.CalendarView.DAY }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()), style = MaterialTheme.typography.labelSmall,
                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(d.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.SemiBold,
                            color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        // All-day and multi-day events.
        val allDay = days.map { d -> occurrences.filter { it.spansDays && it.covers(d) } }
        if (allDay.any { it.isNotEmpty() }) {
            Row(Modifier.fillMaxWidth().padding(end = 4.dp, bottom = 4.dp)) {
                Box(Modifier.width(gutter))
                for ((i, list) in allDay.withIndex()) {
                    Column(Modifier.weight(1f).padding(horizontal = 1.dp)) {
                        for (o in list.take(3)) {
                            val (bg, fg) = eventTint(Color(data.colorOf(o.event)))
                            Text(
                                o.event.title.ifBlank { "(No title)" },
                                Modifier.fillMaxWidth().padding(vertical = 1.dp).clip(RoundedCornerShape(8.dp))
                                    .background(bg).clickable { nav.push(Page.EventPage(o.event.id, o.day)) }
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                                color = fg, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (list.size > 3) Text("+${list.size - 3}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { nav.selected = days[i]; nav.view = io.github.akrishna87.dayline.model.CalendarView.DAY })
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        // The hours.
        Box(Modifier.weight(1f).verticalScroll(scroll)) {
            Row(Modifier.fillMaxWidth().height(HOUR * 24).padding(end = 4.dp)) {
                Column(Modifier.width(gutter)) {
                    for (h in 0 until 24) {
                        Row(Modifier.height(HOUR).fillMaxWidth()) {
                            val t = LocalTime.of(h, 0)
                            if (h > 0) Text(
                                Dates.timeFormatter(clock.is24Hour).format(t).replace(":00", "").replace(" ", " "),
                                Modifier.weight(1f).offset(y = (-7).dp).padding(end = 6.dp),
                                style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (second != null && h > 0) {
                                val other = clock.now.toLocalDate().atTime(t).atZone(zone).withZoneSameInstant(second).toLocalTime()
                                Text(
                                    Dates.timeFormatter(clock.is24Hour).format(other).replace(":00", ""),
                                    Modifier.width(40.dp).offset(y = (-7).dp).padding(end = 4.dp),
                                    style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End, color = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                    }
                }
                for (d in days) {
                    val timed = occurrences.filter { !it.spansDays && it.covers(d) }
                    BoxWithConstraints(
                        Modifier.weight(1f).fillMaxSize()
                            .pointerInput(d) {
                                detectTapGestures { pos ->
                                    // Tap an empty slot: a new event at that half hour.
                                    val minutes = ((pos.y / hourPx) * 60).toInt() / 30 * 30
                                    nav.newEventAt(d, LocalTime.of((minutes / 60).coerceIn(0, 23), minutes % 60))
                                }
                            },
                    ) {
                        val colWidth = maxWidth
                        // Hour lines.
                        Column {
                            for (h in 0 until 24) {
                                Box(Modifier.height(HOUR).fillMaxWidth()) { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
                            }
                        }
                        Box(Modifier.width(1.dp).fillMaxSize().background(MaterialTheme.colorScheme.outlineVariant))
                        for (p in layout(d, timed)) {
                            val top = HOUR * (p.startMin / 60f)
                            val height = HOUR * ((p.endMin - p.startMin) / 60f)
                            val w = colWidth / p.lanes
                            var dragY by remember(p.o.event.id, p.o.day) { mutableFloatStateOf(0f) }
                            var dragX by remember(p.o.event.id, p.o.day) { mutableFloatStateOf(0f) }
                            val color = Color(data.colorOf(p.o.event))
                            val (tintBg, tintFg) = eventTint(color)
                            val past = !p.o.end.isAfter(clock.now)
                            Box(
                                Modifier
                                    .offset(x = w * p.lane, y = top)
                                    .offset { IntOffset(dragX.roundToInt(), dragY.roundToInt()) }
                                    .width(w - 2.dp)
                                    .height(height - 2.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(tintBg)
                                    .drawBehind { drawRect(color.copy(alpha = if (past) 0.5f else 1f), size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height)) }
                                    .graphicsLayer { alpha = if (past) 0.6f else 1f }
                                    .pointerInput(p.o.event.id, p.o.day) {
                                        detectTapGestures { nav.push(Page.EventPage(p.o.event.id, p.o.day)) }
                                    }
                                    .pointerInput(p.o.event.id, p.o.day, colWidth) {
                                        val colPx = colWidth.toPx()
                                        detectDragGesturesAfterLongPress(
                                            onDragEnd = {
                                                // 15-minute steps up and down, whole days across.
                                                val minutes = ((dragY / hourPx) * 60 / 15).roundToInt() * 15L
                                                val dayShift = (dragX / colPx).roundToInt().toLong()
                                                dragX = 0f
                                                dragY = 0f
                                                if (minutes != 0L || dayShift != 0L) moving = p.o to (minutes + dayShift * 1440)
                                            },
                                            onDragCancel = { dragX = 0f; dragY = 0f },
                                        ) { change, amount ->
                                            change.consume()
                                            dragX += amount.x
                                            dragY += amount.y
                                        }
                                    }
                                    .semantics { contentDescription = p.o.event.title.ifBlank { "(No title)" } + ", " + Dates.timeLabel(p.o, d, clock.is24Hour) }
                                    .padding(start = 7.dp, end = 4.dp, top = 3.dp, bottom = 2.dp),
                            ) {
                                Column {
                                    Text(p.o.event.title.ifBlank { "(No title)" }, color = tintFg, style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.ExtraBold, maxLines = if (height < 40.dp) 1 else 2, overflow = TextOverflow.Ellipsis)
                                    if (height >= 40.dp) Text(Dates.timeLabel(p.o, d, clock.is24Hour), color = tintFg.copy(alpha = 0.85f),
                                        style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                    if (height >= 60.dp && p.o.event.location.isNotBlank()) Text(p.o.event.location, color = tintFg.copy(alpha = 0.85f),
                                        style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        // Now.
                        if (d == clock.today) {
                            val y = HOUR * (clock.now.toLocalTime().toSecondOfDay() / 3600f)
                            Box(Modifier.offset(y = y - 1.dp).fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.error))
                            Box(Modifier.offset(x = (-5).dp, y = y - 5.dp).size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
                        }
                    }
                }
            }
        }
    }

    // Moving by drag: a repeating event asks which occurrences.
    moving?.let { (o, minutes) ->
        fun apply(scope: Scope) {
            val e = o.event
            val delta = Duration.ofMinutes(minutes)
            val start = java.time.LocalDateTime.of(o.day, e.start ?: LocalTime.MIDNIGHT).plus(delta)
            val end = java.time.LocalDateTime.of(o.day.plusDays(e.spanDays), e.end ?: (e.start ?: LocalTime.MIDNIGHT).plusHours(1)).plus(delta)
            val changed = e.copy(date = start.toLocalDate(), endDate = end.toLocalDate(), start = start.toLocalTime(), end = end.toLocalTime())
            val split = Store.saveOccurrence(e, o.day, changed, scope)
            messages.offerUndo("Event moved") { Store.restoreEvent(e, split) }
        }
        if (o.event.rule == null) {
            LaunchedEffect(o, minutes) {
                apply(Scope.ALL)
                moving = null
            }
        } else {
            ScopeDialog("Move recurring event", onPick = { apply(it) }, onDismiss = { moving = null })
        }
    }
}
