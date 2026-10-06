package io.github.lilixp.utcradioclock.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.lilixp.utcradioclock.domain.model.*
import io.github.lilixp.utcradioclock.domain.solar.SolarCalculator
import io.github.lilixp.utcradioclock.ui.theme.UTCRadioClockTheme
import io.github.lilixp.utcradioclock.util.TimeFormatter
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.*
import java.util.Locale
import android.graphics.Bitmap
import androidx.compose.ui.graphics.toPixelMap

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ro-w384dp-h832dp-600dpi")
class SunLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val zone = ZoneId.of("Europe/Chisinau")
    private val date = LocalDate.parse("2026-10-06")
    private fun sample(locale: Locale, now: Instant): SunUiState {
        val station = StationPosition(GeoPosition(46.9375, 28.291667), PositionOrigin.GPS, "KN46dw",
            PositionSource.AUTOMATIC, GpsStatus.OK, LocationFix(GeoPosition(46.9375, 28.291667), 15f, now))
        val cache = SunSolarDays(SolarCalculator::calculate)
        val today = cache.day(date, zone, station.position!!)
        val format = TimeFormatter(locale)
        return SunUiState(SunStatus.NORMAL, sunrise = format.eventTime(today.sunrise, zone),
            sunset = format.eventTime(today.sunset, zone), solarNoon = format.eventTime(today.solarNoon, zone),
            dayLength = format.duration(today.dayLength), civilDawn = format.eventTime(today.civilDawn, zone),
            civilDusk = format.eventTime(today.civilDusk, zone),
            presentation = sunPresentation(now, zone, station, today, cache.window(date, zone, station.position), format))
    }
    private fun check(dark: Boolean, scale: Float = 1.3f, english: Boolean = false) {
        val locale = if (english) Locale.UK else Locale.forLanguageTag("ro")
        var input by mutableStateOf(sample(locale, date.atTime(18, 19).atZone(zone).toInstant()))
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, scale)) {
                UTCRadioClockTheme(dark) {
                    // Full unchanged header/navigation, reserving 24 dp status + 48 dp system navigation.
                    Box(Modifier.requiredSize(if (scale > 1.3f) 320.dp else 384.dp, 760.dp)) {
                        DashboardScreen(DashboardUiState("6 octombrie 2026", "15:19:00", "18:19:00", null,
                            ZoneUi(zone.id, "EEST", "UTC+03:00"), callsign = "ER1PL", locator = "KN46dw", sun = input),
                            selectedTab = AppTab.SUN) {}
                    }
                }
            }
        }
        val sunrise = compose.onNodeWithTag(SunTags.SUNRISE).fetchSemanticsNode().size
        val sunset = compose.onNodeWithTag(SunTags.SUNSET).fetchSemanticsNode().size
        assertEquals("Event cards must have identical sizes", sunrise, sunset)
        if (scale == 1.3f) {
            assertFits()
            checkAxisAndMarker()
            save("${if (english) "en" else "ro"}-${if (dark) "dark" else "light"}-1.3")
            for (time in listOf(date.atStartOfDay(zone).toInstant(), date.plusDays(1).atStartOfDay(zone).toInstant().minusSeconds(1))) {
                compose.runOnIdle { input = sample(locale, time) }
                assertFits()
                checkAxisAndMarker()
            }
            compose.runOnIdle { input = input.copy(presentation = input.presentation!!.copy(
                countdown = SunCountdown(SunEvent.SUNSET, Duration.ofSeconds(20)))) }
            compose.onNodeWithTag(SunTags.COUNTDOWN).assertTextEquals(if (english) "Until sunset: Less than 1 min"
                else "Până la apus: Mai puțin de 1 min")
            assertFits()
        } else {
            for (tag in listOf(SunTags.SUNRISE, SunTags.SUNSET, SunTags.BAND, SunTags.COUNTDOWN, SunTags.DETAILS, SunTags.ZONE)) {
                compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
                checkText()
            }
            val scroll = compose.onNode(hasScrollAction()).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
            assertTrue("Large text must be accessible by scrolling", scroll.maxValue() > 0)
            save("${if (english) "en" else "ro"}-${if (dark) "dark" else "light"}-2.0")
        }
        // Extra dates may legitimately require scrolling; no value may disappear or be truncated.
        val extraDate = if (english) "7 Oct 2026" else "7 oct. 2026"
        compose.runOnIdle { input = input.copy(presentation = input.presentation!!.copy(
            sunriseDate = extraDate, noon = "00:00 ($extraDate)",
            morningTwilight = "23:41 ($extraDate)–00:12 (8 Oct 2026)",
            eveningTwilight = "23:37 ($extraDate)–00:07 (8 Oct 2026)",
        )) }
        assertEquals("Cards remain equal when only one event has an extra date",
            compose.onNodeWithTag(SunTags.SUNRISE).fetchSemanticsNode().size,
            compose.onNodeWithTag(SunTags.SUNSET).fetchSemanticsNode().size)
        for (tag in listOf(SunTags.SUNRISE, SunTags.DETAILS, SunTags.ZONE)) {
            compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
            checkText()
        }
    }
    private fun assertFits() {
        val content = compose.onNodeWithTag(SunTags.CONTENT).getUnclippedBoundsInRoot()
        val navigation = compose.onNodeWithTag(AppTab.CLOCK.testTag).getUnclippedBoundsInRoot()
        assertTrue("Whole Sun screen must fit: $content vs $navigation", content.bottom <= navigation.top)
        val scroll = compose.onNode(hasScrollAction()).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertEquals("No scroll required at font 1.3", 0f, scroll.maxValue(), 0f)
        compose.onNodeWithTag(SunTags.ZONE).assertIsDisplayed()
        checkText()
    }
    private fun checkAxisAndMarker() {
        val band = compose.onNodeWithTag(SunTags.BAND).fetchSemanticsNode().boundsInRoot
        val label = compose.onNodeWithTag(SunTags.MARKER_LABEL).fetchSemanticsNode().boundsInRoot
        assertTrue("Marker label stays within band edges", label.left >= band.left && label.right <= band.right)
        val labels = listOf(SunTags.AXIS_START, SunTags.AXIS_MIDDAY, SunTags.AXIS_END)
            .map { compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot }
        labels.zipWithNext().forEach { (a, b) -> assertTrue("Axis labels must not overlap: $a vs $b", a.right <= b.left) }
        compose.onNodeWithTag(SunTags.BAND).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))
    }
    private fun checkText() {
        // The shared header is outside this redesign; its existing maxLines=1 truncates the app
        // name at font 2.0 on narrow screens. Validate every text belonging to the Sun screen.
        val nodes = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult) and
            hasAnyAncestor(hasTestTag(SunTags.CONTENT)), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("Sun text must be present", nodes.isNotEmpty())
        for (node in nodes) {
            val layouts = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            for (layout in layouts) {
                val text = layout.layoutInput.text.text
                assertFalse("Text height: $text", layout.didOverflowHeight)
                if (layout.lineCount == 1) assertTrue("Single line must fit completely: $text",
                    layout.multiParagraph.maxIntrinsicWidth <= node.size.width + 1)
                for (line in 0 until layout.lineCount) {
                    assertFalse("Text must be complete: $text", layout.isLineEllipsized(line))
                    assertTrue("Text width: $text", layout.getLineRight(line) - layout.getLineLeft(line) <= node.size.width + 1)
                    if (line < layout.lineCount - 1) {
                        val end = layout.getLineEnd(line)
                        assertFalse("Words must not split: $text", end in 1 until text.length && text[end - 1].isLetter() && text[end].isLetter())
                    }
                }
            }
        }
    }
    private fun save(name: String) {
        val directory = File("build/sun-review").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    /** By day now is a small sun inside the band; at twilight and at night the white line stays. */
    @Test fun marker_sunByDay_whiteLineAtTwilightAndAtNight() {
        val ro = Locale.forLanguageTag("ro")
        var input by mutableStateOf(sample(ro, date.atTime(13, 0).atZone(zone).toInstant()))
        compose.setContent {
            UTCRadioClockTheme(false) {
                Box(Modifier.requiredSize(384.dp, 760.dp)) {
                    DashboardScreen(DashboardUiState("6 octombrie 2026", "10:00:00", "13:00:00", null,
                        ZoneUi(zone.id, "EEST", "UTC+03:00"), callsign = "ER1PL", locator = "KN46dw", sun = input),
                        selectedTab = AppTab.SUN) {}
                }
            }
        }
        fun atMarker(): androidx.compose.ui.graphics.Color {
            val image = compose.onNodeWithTag(SunTags.BAND).captureToImage().toPixelMap()
            val x = (input.presentation!!.marker * image.width).toInt().coerceIn(0, image.width - 1)
            return image[x, image.height / 2]
        }
        fun close(expected: androidx.compose.ui.graphics.Color, actual: androidx.compose.ui.graphics.Color) =
            kotlin.math.abs(expected.red - actual.red) < 0.03 && kotlin.math.abs(expected.green - actual.green) < 0.03 &&
                kotlin.math.abs(expected.blue - actual.blue) < 0.03
        assertEquals(DayPhase.DAY, input.presentation!!.markerPhase)
        assertTrue("A sun by day", close(androidx.compose.ui.graphics.Color(0xFFFFC22E), atMarker()))
        for ((hour, minute, phase) in listOf(Triple(18, 50, DayPhase.TWILIGHT), Triple(22, 40, DayPhase.NIGHT))) {
            compose.runOnIdle { input = sample(ro, date.atTime(hour, minute).atZone(zone).toInstant()) }
            assertEquals(phase, input.presentation!!.markerPhase)
            assertTrue("The white line at $hour:$minute", close(androidx.compose.ui.graphics.Color.White, atMarker()))
        }
    }
    @Test fun romanianLight13() = check(false)
    @Test fun romanianDark13() = check(true)
    @Test @Config(qualifiers = "en-w384dp-h832dp-600dpi") fun englishLight13() = check(false, english = true)
    @Test @Config(qualifiers = "en-w384dp-h832dp-600dpi") fun englishDark13() = check(true, english = true)
    @Test @Config(qualifiers = "ro-w320dp-h832dp-600dpi") fun romanianLight20() = check(false, 2f)
    @Test @Config(qualifiers = "ro-w320dp-h832dp-600dpi") fun romanianDark20() = check(true, 2f)
    @Test @Config(qualifiers = "en-w320dp-h832dp-600dpi") fun englishLight20() = check(false, 2f, true)
    @Test @Config(qualifiers = "en-w320dp-h832dp-600dpi") fun englishDark20() = check(true, 2f, true)
}
