package io.github.lilixp.utcradioclock.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.github.lilixp.utcradioclock.domain.model.ThemeMode

// A fixed palette (no wallpaper-based dynamic color): the clock looks the same on every phone.
// Deep teal like a transceiver panel, with amber section labels, readable in daylight and at night.
private val LightColors = lightColorScheme(
    primary = Color(0xFF0B5563),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCDEBF1),
    onPrimaryContainer = Color(0xFF002B33),
    secondary = Color(0xFF8A5A00),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFE0A8),
    onSecondaryContainer = Color(0xFF2B1A00),
    background = Color(0xFFF8FAFA),
    onBackground = Color(0xFF171D1E),
    surface = Color(0xFFF8FAFA),
    onSurface = Color(0xFF171D1E),
    surfaceContainer = Color(0xFFECF1F2),
    surfaceContainerHigh = Color(0xFFE6EBEC),
    surfaceContainerHighest = Color(0xFFE0E6E7), // cards
    outline = Color(0xFF6F797A),
    outlineVariant = Color(0xFFBFC8CA),
    onSurfaceVariant = Color(0xFF3F484A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF86D2E0),
    onPrimary = Color(0xFF00363F),
    primaryContainer = Color(0xFF0B3D4A),
    onPrimaryContainer = Color(0xFFCDEBF1),
    secondary = Color(0xFFFFC857),
    onSecondary = Color(0xFF452B00),
    secondaryContainer = Color(0xFF3A2A08),
    onSecondaryContainer = Color(0xFFFFE0A8),
    background = Color(0xFF0E1415),
    onBackground = Color(0xFFDEE3E4),
    surface = Color(0xFF0E1415),
    onSurface = Color(0xFFDEE3E4),
    surfaceContainer = Color(0xFF1A2122),
    surfaceContainerHigh = Color(0xFF242B2C),
    surfaceContainerHighest = Color(0xFF2F3637), // cards
    outline = Color(0xFF899294),
    outlineVariant = Color(0xFF3F484A),
    onSurfaceVariant = Color(0xFFBFC8CA),
)

/** Whether the app is dark for the chosen [ThemeMode]; SYSTEM follows the phone ([systemIsDark]). */
fun ThemeMode.isDark(systemIsDark: Boolean): Boolean = when (this) {
    ThemeMode.SYSTEM -> systemIsDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun UTCRadioClockTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
