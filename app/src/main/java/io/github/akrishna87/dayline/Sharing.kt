package io.github.akrishna87.dayline

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import io.github.akrishna87.dayline.model.Dates
import io.github.akrishna87.dayline.model.Event
import io.github.akrishna87.dayline.model.Ics
import io.github.akrishna87.dayline.model.Recurrence
import java.io.File
import java.time.LocalDate

/** Sending events out of Dayline as .ics files: to any app, or as an email invitation to the guests. */
object Sharing {
    private fun file(context: Context, name: String, text: String): android.net.Uri {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val safe = name.replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().take(60).ifBlank { "event" }
        val f = File(dir, "$safe.ics")
        f.writeText(text)
        return FileProvider.getUriForFile(context, context.packageName + ".files", f)
    }

    fun shareIcs(context: Context, events: List<Event>, name: String): Boolean {
        val uri = file(context, name, Ics.export(events, name))
        return start(
            context,
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/calendar").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                "Share $name",
            ),
        )
    }

    /** An email to the guests with the event written out and attached as an invitation they can add to their calendar. */
    fun emailInvitation(context: Context, e: Event): Boolean {
        val uri = file(context, "invite", Ics.export(listOf(e), method = "REQUEST"))
        val o = Recurrence.occurrence(e, e.date)
        val is24 = android.text.format.DateFormat.is24HourFormat(context)
        val whenText = Dates.fieldDate(e.date, LocalDate.now()) + ", " + Dates.timeLabel(o, o.startDay, is24) +
            (e.rule?.let { "\n" + it.label(e.date) } ?: "")
        val body = listOfNotNull(
            e.title.ifBlank { "(No title)" },
            whenText,
            e.location.takeIf { it.isNotBlank() }?.let { "Where: $it" },
            e.notes.takeIf { it.isNotBlank() },
            "\nThe attached invitation (invite.ics) adds this to your calendar.",
        ).joinToString("\n")
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/calendar")
            .putExtra(Intent.EXTRA_EMAIL, e.guests.toTypedArray())
            .putExtra(Intent.EXTRA_SUBJECT, "Invitation: ${e.title.ifBlank { "(No title)" }} @ $whenText".lines().first())
            .putExtra(Intent.EXTRA_TEXT, body)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return start(context, Intent.createChooser(send, "Send invitation"))
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
