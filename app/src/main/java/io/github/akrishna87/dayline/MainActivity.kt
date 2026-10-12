package io.github.akrishna87.dayline

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.akrishna87.dayline.ui.DaylineApp
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate

/** What the app was opened to do: show an event from its reminder. */
data class OpenEvent(val id: String, val day: LocalDate?)

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
        val id = intent?.getStringExtra(EXTRA_OPEN_EVENT) ?: return
        val day = intent.getStringExtra(EXTRA_OPEN_DAY)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        launch.value = OpenEvent(id, day)
    }

    companion object {
        const val EXTRA_OPEN_EVENT = "open_event"
        const val EXTRA_OPEN_DAY = "open_day"

        /** Read (and cleared) by the app once it has acted on it. */
        val launch = MutableStateFlow<OpenEvent?>(null)
    }
}
