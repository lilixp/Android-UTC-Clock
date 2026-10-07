package io.github.lilixp.utcradioclock.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import io.github.lilixp.utcradioclock.domain.model.ConditionLevel
import io.github.lilixp.utcradioclock.domain.model.ThemeMode

// A fixed palette (no wallpaper-based dynamic color): the clock looks the same on every phone.
// Deep teal like a transceiver panel, with amber section labels, readable in daylight and at night.
private val LightColors = lightColorScheme(
    primary = Color(0xFF2F7FC1),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEAF4FC),
    onPrimaryContainer = Color(0xFF0D2B45),
    secondary = Color(0xFF5F7690),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEEF4F8),
    onSecondaryContainer = Color(0xFF1C2A36),
    background = Color(0xFFF8FAFD),
    onBackground = Color(0xFF10233A),
    surface = Color(0xFFF8FAFD),
    onSurface = Color(0xFF10233A),
    surfaceContainer = Color(0xFFF6F8FB),
    surfaceContainerHigh = Color(0xFFF0F4F8),
    surfaceContainerHighest = Color(0xFFFFFFFF),
    outline = Color(0xFF9AAABC),
    outlineVariant = Color(0xFFDDE5EC),
    onSurfaceVariant = Color(0xFF5B718A),
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

/** A colour and the text colour that reads well on it. */
@Immutable
data class LevelColor(val container: Color, val content: Color)

/**
 * Green / yellow / red for good / fair / poor (N0NBH band conditions, SFI, K, A), plus a neutral one
 * for values that are not known. Darker shades with white text in the light theme, lighter shades
 * with dark text in the dark theme; yellow always has dark text.
 */
@Immutable
data class ConditionColors(val good: LevelColor, val fair: LevelColor, val poor: LevelColor, val unknown: LevelColor) {
    fun of(level: ConditionLevel?): LevelColor = when (level) {
        ConditionLevel.GOOD -> good
        ConditionLevel.FAIR -> fair
        ConditionLevel.POOR -> poor
        null -> unknown
    }
}

private val LightConditionColors = ConditionColors(
    good = LevelColor(Color(0xFF2E7D32), Color.White),
    fair = LevelColor(Color(0xFFF9C80E), Color(0xFF1F1A00)),
    poor = LevelColor(Color(0xFFC62828), Color.White),
    unknown = LevelColor(Color(0xFFD5DCDD), Color(0xFF3F484A)),
)

private val DarkConditionColors = ConditionColors(
    good = LevelColor(Color(0xFF81C784), Color(0xFF0B2E0F)),
    fair = LevelColor(Color(0xFFFFD54F), Color(0xFF2B2000)),
    poor = LevelColor(Color(0xFFEF7A7A), Color(0xFF3B0A0A)),
    unknown = LevelColor(Color(0xFF3F484A), Color(0xFFBFC8CA)),
)

/** The condition colours of the current theme. */
val LocalConditionColors = staticCompositionLocalOf { LightConditionColors }

@Composable
fun UTCRadioClockTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalConditionColors provides if (darkTheme) DarkConditionColors else LightConditionColors) {
        MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
    }
}
