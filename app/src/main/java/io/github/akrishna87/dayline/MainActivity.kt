package io.github.akrishna87.dayline

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.akrishna87.dayline.ui.DaylineApp
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate

/** What the app was opened to do: from a reminder, a widget, or an .ics file. */
sealed interface LaunchRequest {
    data class OpenEvent(val id: String, val day: LocalDate?) : LaunchRequest
    data class OpenDay(val day: LocalDate) : LaunchRequest
    data class NewEvent(val day: LocalDate) : LaunchRequest
    data class ImportIcs(val uri: Uri) : LaunchRequest
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Store.init(this)
        handle(intent)
        setContent { DaylineApp() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent == null) return
        val day = intent.getStringExtra(EXTRA_OPEN_DAY)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val id = intent.getStringExtra(EXTRA_OPEN_EVENT)
        val ics = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.takeIf { it.scheme == "content" || it.scheme == "file" }
            Intent.ACTION_SEND -> stream(intent)
            else -> null
        }
        launch.value = when {
            ics != null -> LaunchRequest.ImportIcs(ics)
            id != null -> LaunchRequest.OpenEvent(id, day)
            intent.getBooleanExtra(EXTRA_NEW_EVENT, false) -> LaunchRequest.NewEvent(day ?: LocalDate.now())
            day != null -> LaunchRequest.OpenDay(day)
            else -> return
        }
    }

    @Suppress("DEPRECATION")
    private fun stream(intent: Intent): Uri? = intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri

    companion object {
        const val EXTRA_OPEN_EVENT = "open_event"
        const val EXTRA_OPEN_DAY = "open_day"
        const val EXTRA_NEW_EVENT = "new_event"

        /** Read (and cleared) by the app once it has acted on it. */
        val launch = MutableStateFlow<LaunchRequest?>(null)
    }
}
