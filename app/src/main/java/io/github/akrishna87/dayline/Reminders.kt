package io.github.akrishna87.dayline

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.akrishna87.dayline.model.Data
import io.github.akrishna87.dayline.model.Dates
import io.github.akrishna87.dayline.model.Event
import io.github.akrishna87.dayline.model.Occurrence
import io.github.akrishna87.dayline.model.Recurrence
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Notifications before events. Each event has one alarm at a time, for its next reminder; when it
 * goes off, the alarm moves on to the following occurrence. Alarms are kept in step with the
 * events every time they change.
 */
object Reminders {
    private const val CHANNEL = "events"
    private const val PREFS = "reminders"
    private const val KEY_SCHEDULED = "scheduled"
    const val ACTION_REMIND = "io.github.akrishna87.dayline.REMIND"
    const val EXTRA_EVENT = "event"
    const val EXTRA_DAY = "day"

    fun sync(context: Context, data: Data) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val before = prefs.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty()
        val now = LocalDateTime.now()
        val zone = ZoneId.systemDefault()
        val wanted: Map<String, Pair<Long, LocalDate>> = if (!data.settings.reminders) {
            emptyMap()
        } else {
            data.visibleEvents.mapNotNull { e ->
                val (at, day) = Recurrence.nextReminder(e, now)?.takeIf { it.first.isBefore(now.plusDays(90)) } ?: return@mapNotNull null
                e.id to (at.atZone(zone).toInstant().toEpochMilli() to day)
            }.toMap()
        }
        for (id in before - wanted.keys) alarms.cancel(alarm(context, id, null))
        for ((id, v) in wanted) {
            val (at, day) = v
            runCatching {
                if (canScheduleExact(context)) {
                    alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarm(context, id, day))
                } else {
                    alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarm(context, id, day))
                }
            }
        }
        prefs.edit().putStringSet(KEY_SCHEDULED, wanted.keys).apply()
    }

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true

    fun canNotify(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** The alarm for an event. The data URI makes each event's alarm its own, whatever day it carries. */
    private fun alarm(context: Context, id: String, day: LocalDate?): PendingIntent = PendingIntent.getBroadcast(
        context, id.hashCode(),
        Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_REMIND)
            .setData(Uri.parse("dayline://event/$id"))
            .putExtra(EXTRA_EVENT, id)
            .putExtra(EXTRA_DAY, day?.toString()),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun show(context: Context, event: Event, day: LocalDate, data: Data) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Event reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "A notification before an event starts"
                },
            )
        }
        val today = LocalDate.now()
        val is24 = android.text.format.DateFormat.is24HourFormat(context)
        val whenText = "${Dates.dayLabel(day, today)} · ${Dates.timeLabel(Occurrence(event, day), day, is24)}"
        val text = listOf(whenText, event.location, data.calendar(event.calendarId).name).filter { it.isNotBlank() }.joinToString(" · ")
        val open = PendingIntent.getActivity(
            context, event.id.hashCode(),
            Intent(context, MainActivity::class.java)
                .setData(Uri.parse("dayline://event/${event.id}"))
                .putExtra(MainActivity.EXTRA_OPEN_EVENT, event.id)
                .putExtra(MainActivity.EXTRA_OPEN_DAY, day.toString())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_dayline)
            .setColor(ContextCompat.getColor(context, R.color.brand))
            .setContentTitle(event.title.ifBlank { "Event" })
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (event.notes.isBlank()) text else "$text\n${event.notes}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(event.id.hashCode(), notification) }
    }
}

/** Shows due reminders and puts alarms back after a restart or a change of time. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        val d = Store.data.value
        if (intent.action == Reminders.ACTION_REMIND) {
            val event = d.event(intent.getStringExtra(Reminders.EXTRA_EVENT))
            val day = intent.getStringExtra(Reminders.EXTRA_DAY)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            if (event != null && day != null && d.settings.reminders && day !in event.skipped) Reminders.show(context, event, day, d)
        }
        // Either way, line up the next reminder of every event.
        Reminders.sync(context, d)
    }
}
