package io.github.akrishna87.dayline

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import android.widget.RemoteViews
import io.github.akrishna87.dayline.model.Dates
import io.github.akrishna87.dayline.model.Recurrence
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Google Calendar's two home screen widgets: the schedule ahead, and the month. */
object Widgets {
    private const val ROWS = 8

    fun refresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val schedule = manager.getAppWidgetIds(ComponentName(context, ScheduleWidget::class.java))
        if (schedule.isNotEmpty()) renderSchedule(context, manager, schedule)
        val month = manager.getAppWidgetIds(ComponentName(context, MonthWidget::class.java))
        if (month.isNotEmpty()) renderMonth(context, manager, month)
    }

    fun renderSchedule(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Store.init(context)
        val d = Store.data.value
        val today = LocalDate.now()
        val is24 = DateFormat.is24HourFormat(context)
        val views = RemoteViews(context.packageName, R.layout.widget_schedule)
        views.setTextViewText(R.id.widget_title, DateTimeFormatter.ofPattern("EEEE").format(today))
        views.setTextViewText(R.id.widget_date, DateTimeFormatter.ofPattern("d MMMM").format(today))
        views.setOnClickPendingIntent(R.id.widget_header, open(context, null, newEvent = false))
        views.setOnClickPendingIntent(R.id.widget_add, open(context, today, newEvent = true))
        views.removeAllViews(R.id.widget_list)
        val days = Recurrence.agenda(d.visibleEvents, today, 30)
        if (days.isEmpty()) views.addView(R.id.widget_list, RemoteViews(context.packageName, R.layout.widget_empty))
        var rows = 0
        for ((day, list) in days) {
            if (rows >= ROWS) break
            val header = RemoteViews(context.packageName, R.layout.widget_day)
            header.setTextViewText(R.id.day_label, Dates.dayHeading(day, today))
            views.addView(R.id.widget_list, header)
            for (o in list) {
                if (rows >= ROWS) break
                val row = RemoteViews(context.packageName, R.layout.widget_event)
                row.setTextViewText(R.id.event_title, o.event.title.ifBlank { "(No title)" })
                val time = Dates.timeLabel(o, day, is24) + if (o.event.location.isNotBlank()) " · ${o.event.location}" else ""
                row.setTextViewText(R.id.event_time, time)
                row.setInt(R.id.event_bar, "setColorFilter", d.colorOf(o.event))
                row.setOnClickPendingIntent(R.id.event_row, openEvent(context, o.event.id, o.day))
                views.addView(R.id.widget_list, row)
                rows++
            }
        }
        manager.updateAppWidget(ids, views)
        scheduleMidnight(context)
    }

    fun renderMonth(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Store.init(context)
        val d = Store.data.value
        val today = LocalDate.now()
        val month = YearMonth.from(today)
        val ws = Dates.weekStart(d.settings)
        val grid = Dates.monthGrid(month, ws)
        val busy = Recurrence.between(d.visibleEvents, grid.first().first(), grid.last().last()).flatMap { o ->
            generateSequence(o.startDay) { it.plusDays(1) }.takeWhile { !it.isAfter(o.endDay) }.toList()
        }.toSet()
        val views = RemoteViews(context.packageName, R.layout.widget_month)
        views.setTextViewText(R.id.widget_title, DateTimeFormatter.ofPattern("MMMM yyyy").format(month))
        views.setOnClickPendingIntent(R.id.widget_title, open(context, null, newEvent = false))
        views.setOnClickPendingIntent(R.id.widget_add, open(context, today, newEvent = true))
        views.removeAllViews(R.id.widget_grid)
        val head = RemoteViews(context.packageName, R.layout.widget_month_row)
        for (l in Dates.weekdayLetters(ws)) {
            val c = RemoteViews(context.packageName, R.layout.widget_month_cell)
            c.setTextViewText(R.id.cell, l)
            c.setTextColor(R.id.cell, 0xFF8A8C99.toInt())
            head.addView(R.id.month_row, c)
        }
        views.addView(R.id.widget_grid, head)
        for (week in grid) {
            val row = RemoteViews(context.packageName, R.layout.widget_month_row)
            for (day in week) {
                val c = RemoteViews(context.packageName, if (day == today) R.layout.widget_month_today else R.layout.widget_month_cell)
                // Days with events are bold and underlined with a dot.
                c.setTextViewText(R.id.cell, day.dayOfMonth.toString() + if (day in busy && day != today) "•" else "")
                if (day != today) {
                    c.setTextColor(R.id.cell, if (YearMonth.from(day) == month) context.getColor(R.color.widget_text) else 0x808A8C99.toInt())
                }
                c.setOnClickPendingIntent(if (day == today) R.id.cell_frame else R.id.cell, open(context, day, newEvent = false))
                row.addView(R.id.month_row, c)
            }
            views.addView(R.id.widget_grid, row)
        }
        manager.updateAppWidget(ids, views)
        scheduleMidnight(context)
    }

    private fun open(context: Context, day: LocalDate?, newEvent: Boolean): PendingIntent = PendingIntent.getActivity(
        context, (day?.toEpochDay()?.toInt() ?: 0) * 2 + if (newEvent) 1 else 0,
        Intent(context, MainActivity::class.java)
            .setData(Uri.parse("dayline://widget/${day ?: "today"}/${if (newEvent) "new" else "open"}"))
            .putExtra(MainActivity.EXTRA_OPEN_DAY, day?.toString())
            .putExtra(MainActivity.EXTRA_NEW_EVENT, newEvent)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun openEvent(context: Context, id: String, day: LocalDate): PendingIntent = PendingIntent.getActivity(
        context, (id + day).hashCode(),
        Intent(context, MainActivity::class.java)
            .setData(Uri.parse("dayline://widget-event/$id/$day"))
            .putExtra(MainActivity.EXTRA_OPEN_EVENT, id)
            .putExtra(MainActivity.EXTRA_OPEN_DAY, day.toString())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Moves the widgets on to the new day soon after midnight. */
    private fun scheduleMidnight(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val at = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 60_000L
        val intent = PendingIntent.getBroadcast(
            context, 3,
            Intent(context, ScheduleWidget::class.java).setAction(ScheduleWidget.ACTION_MIDNIGHT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        alarms.set(AlarmManager.RTC, at, intent)
    }
}

class ScheduleWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Widgets.renderSchedule(context, manager, ids)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_MIDNIGHT) Widgets.refresh(context) else super.onReceive(context, intent)
    }

    companion object {
        const val ACTION_MIDNIGHT = "io.github.akrishna87.dayline.WIDGET_MIDNIGHT"
    }
}

class MonthWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Widgets.renderMonth(context, manager, ids)
}
