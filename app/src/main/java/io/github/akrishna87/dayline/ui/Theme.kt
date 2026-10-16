package io.github.akrishna87.dayline.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.akrishna87.dayline.model.ThemeMode

private val Accent = Color(0xFF2F6BFF)
private val AccentDark = Color(0xFF8FB0FF)

private val LightColors = LightKit.let { k ->
    lightColorScheme(
        primary = Accent,
        onPrimary = Color.White,
        primaryContainer = lerp(Color.White, Accent, 0.13f),
        onPrimaryContainer = lerp(Accent, Color.Black, 0.4f),
        secondary = k.sub,
        onSecondary = Color.White,
        secondaryContainer = k.chip,
        onSecondaryContainer = k.text,
        tertiary = Color(0xFF0B8463),
        background = k.bg,
        onBackground = k.text,
        surface = k.bg,
        onSurface = k.text,
        surfaceVariant = k.chip,
        onSurfaceVariant = k.sub,
        surfaceContainerLowest = k.card,
        surfaceContainerLow = k.card,
        surfaceContainer = k.card,
        surfaceContainerHigh = k.card,
        surfaceContainerHighest = k.chip,
        outline = Color(0xFFC5CBD8),
        outlineVariant = k.line,
        error = Color(0xFFD93036),
        onError = Color.White,
        inverseSurface = Color(0xFF232A38),
        inverseOnSurface = Color(0xFFF2F4F8),
        inversePrimary = AccentDark,
        scrim = Color(0xFF0A0F1C),
    )
}

private val DarkColors = DarkKit.let { k ->
    darkColorScheme(
        primary = AccentDark,
        onPrimary = Color(0xFF0A1F4D),
        primaryContainer = lerp(k.card, Accent, 0.35f),
        onPrimaryContainer = Color(0xFFDCE6FF),
        secondary = k.sub,
        onSecondary = k.bg,
        secondaryContainer = k.chip,
        onSecondaryContainer = k.text,
        tertiary = Color(0xFF3DD6A6),
        background = k.bg,
        onBackground = k.text,
        surface = k.bg,
        onSurface = k.text,
        surfaceVariant = k.chip,
        onSurfaceVariant = k.sub,
        surfaceContainerLowest = k.card,
        surfaceContainerLow = k.card,
        surfaceContainer = k.card,
        surfaceContainerHigh = Color(0xFF22232B),
        surfaceContainerHighest = k.chip,
        outline = Color(0xFF4A4B57),
        outlineVariant = k.line,
        error = Color(0xFFFF6B6F),
        onError = Color(0xFF3A0905),
        inverseSurface = Color(0xFFE8E8EE),
        inverseOnSurface = Color(0xFF1C1C21),
        inversePrimary = Accent,
        scrim = Color.Black,
    )
}

private val AppTypography = kitTypography()

@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun DaylineTheme(dark: Boolean, content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalKit provides if (dark) DarkKit else LightKit) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = AppTypography, shapes = KitShapes, content = content)
    }
}
