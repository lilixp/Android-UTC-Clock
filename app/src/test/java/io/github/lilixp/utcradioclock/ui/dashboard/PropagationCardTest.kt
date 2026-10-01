package io.github.lilixp.utcradioclock.ui.dashboard

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import io.github.lilixp.utcradioclock.data.propagation.PropagationState
import io.github.lilixp.utcradioclock.domain.model.BandGroup
import io.github.lilixp.utcradioclock.domain.model.ConditionLevel
import io.github.lilixp.utcradioclock.domain.model.ConditionLevel.FAIR
import io.github.lilixp.utcradioclock.domain.model.ConditionLevel.GOOD
import io.github.lilixp.utcradioclock.domain.model.ConditionLevel.POOR
import io.github.lilixp.utcradioclock.ui.theme.ConditionColors
import io.github.lilixp.utcradioclock.ui.theme.LocalConditionColors
import io.github.lilixp.utcradioclock.ui.theme.UTCRadioClockTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/** The PROPAGATION card on the dashboard, drawn for real (colours included), in Romanian and English. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ro-w411dp-h891dp")
class PropagationCardTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var colors: ConditionColors

    /** 1 October 2026 like: SFI 93 (fair), K 0, A 3 (good); now day: 80-40 fair, 30-20 good, 17-15 fair, 12-10 poor. */
    private val data = PropagationUiState(
        status = PropagationState.Status.CURRENT,
        solarFlux = IndexUi("93", FAIR),
        kIndex = IndexUi("0", GOOD),
        aIndex = IndexUi("3", GOOD),
        bands = listOf(
            BandUi(BandGroup.BANDS_80_40, now = FAIR, day = FAIR, night = GOOD),
            BandUi(BandGroup.BANDS_30_20, now = GOOD, day = GOOD, night = GOOD),
            BandUi(BandGroup.BANDS_17_15, now = FAIR, day = FAIR, night = POOR),
            BandUi(BandGroup.BANDS_12_10, now = POOR, day = POOR, night = POOR),
        ),
        isDay = true,
        updated = "05:29",
    )

    private fun show(propagation: PropagationUiState = data, dark: Boolean = false) {
        val state = DashboardUiState(
            utcDate = "1 octombrie 2026",
            utcTime = "05:42:31",
            localTime = "08:42:31",
            localDate = null,
            timeZone = "Europe/Chisinau · UTC+03:00",
            propagation = propagation,
        )
        compose.setContent {
            UTCRadioClockTheme(darkTheme = dark) {
                colors = LocalConditionColors.current
                DashboardScreen(state) {}
            }
        }
        // The card is the last one: scrolling to its title brings all of it on the (tall) test screen
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("PROPAGARE").or(hasText("PROPAGATION")))
    }

    /** The colour of a box, taken from its left edge, half-way down (away from the centred text). */
    private fun boxColor(tag: String): Color {
        val image = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
        return image[3, image.height / 2]
    }

    private fun assertColor(expected: Color, actual: Color) {
        val close = abs(expected.red - actual.red) < 0.02 && abs(expected.green - actual.green) < 0.02 &&
            abs(expected.blue - actual.blue) < 0.02
        assertTrue("expected $expected, was $actual", close)
    }

    private fun assertBand(group: BandGroup, level: ConditionLevel?) =
        assertColor(colors.of(level).container, boxColor(PropagationTags.band(group)))

    @Test
    fun indicesAndTheFourBands() {
        show()
        compose.onNodeWithTag(PropagationTags.SFI).assertIsDisplayed()
        compose.onNodeWithText("SFI 93").assertIsDisplayed()
        compose.onNodeWithText("K 0").assertIsDisplayed()
        compose.onNodeWithText("A 3").assertIsDisplayed()
        for (label in listOf("80-40m", "30-20m", "17-15m", "12-10m")) compose.onNodeWithText(label).assertIsDisplayed()
        compose.onNodeWithText("N0NBH (hamqsl.com) · actualizat 05:29 UTC").assertIsDisplayed()
    }

    @Test
    fun coloursComeFromTheLevels() {
        show()
        assertBand(BandGroup.BANDS_80_40, FAIR)
        assertBand(BandGroup.BANDS_30_20, GOOD)
        assertBand(BandGroup.BANDS_17_15, FAIR)
        assertBand(BandGroup.BANDS_12_10, POOR)
        assertColor(colors.fair.container, boxColor(PropagationTags.SFI))
        assertColor(colors.good.container, boxColor(PropagationTags.K))
    }

    @Test
    fun otherDataOtherColours_nothingFixedInTheCode() {
        show(data.copy(bands = data.bands.map { it.copy(now = GOOD) }, solarFlux = IndexUi("150", GOOD)))
        for (group in BandGroup.entries) assertBand(group, GOOD)
        assertColor(colors.good.container, boxColor(PropagationTags.SFI))
    }

    @Test
    fun missingValues_dashAndNeutralColour() {
        show(data.copy(kIndex = null, bands = data.bands.map { if (it.group == BandGroup.BANDS_12_10) it.copy(now = null) else it }))
        compose.onNodeWithText("K —").assertIsDisplayed()
        assertColor(colors.unknown.container, boxColor(PropagationTags.K))
        assertBand(BandGroup.BANDS_12_10, null)
    }

    @Test
    fun tapOnABand_explainsDayAndNight() {
        show()
        compose.onNodeWithContentDescription("80-40m: Mediu").performClick()
        compose.onNodeWithText("80-40 m").assertIsDisplayed()
        compose.onNodeWithText("Ziua: Mediu • Noaptea: Bun").assertIsDisplayed()
        compose.onNodeWithText("Acum e zi la stație.").assertIsDisplayed()
        compose.onNodeWithText("Condiții calculate de N0NBH (hamqsl.com).").assertIsDisplayed()
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithText("Ziua: Mediu • Noaptea: Bun").assertDoesNotExist()

        compose.onNodeWithContentDescription("30-20m: Bun").performClick() // another band, its own values
        compose.onNodeWithText("30-20 m").assertIsDisplayed()
        compose.onNodeWithText("Ziua: Bun • Noaptea: Bun").assertIsDisplayed()
    }

    @Test
    fun withoutLocator_theDialogSaysDayIsByTheClock() {
        show(data.copy(isDay = false, dayNightByClock = true))
        compose.onNodeWithContentDescription("17-15m: Mediu").performClick()
        compose.onNodeWithText("Acum e noapte la stație.").assertIsDisplayed()
        compose.onNodeWithText("Fără locator, ziua este între 06:00 și 18:00 ora locală.").assertIsDisplayed()
    }

    @Test
    fun staleData_saysSo() {
        show(data.copy(status = PropagationState.Status.STALE, updated = "30 sept. 21:00"))
        compose.onNodeWithText("SFI 93").assertIsDisplayed() // the last valid values stay
        compose.onNodeWithText("Date neactualizate · ultima actualizare 30 sept. 21:00 UTC").assertIsDisplayed()
    }

    @Test
    fun unavailable_noInventedValues() {
        show(PropagationUiState(status = PropagationState.Status.UNAVAILABLE))
        compose.onNodeWithText("Date indisponibile").assertIsDisplayed()
        compose.onNodeWithText("SFI", substring = true).assertDoesNotExist()
        compose.onNodeWithText("80-40m").assertDoesNotExist()
    }

    @Test
    fun loading() {
        show(PropagationUiState(status = PropagationState.Status.LOADING))
        compose.onNodeWithText("Se încarcă datele…").assertIsDisplayed()
    }

    @Test
    fun darkTheme_lighterColours() {
        show(dark = true)
        assertBand(BandGroup.BANDS_30_20, GOOD)
        assertBand(BandGroup.BANDS_12_10, POOR)
        compose.onNodeWithText("SFI 93").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-w411dp-h891dp")
    fun inEnglish() {
        show()
        compose.onNodeWithText("PROPAGATION").assertIsDisplayed()
        compose.onNodeWithText("N0NBH (hamqsl.com) · updated 05:29 UTC").assertIsDisplayed()
        compose.onNodeWithContentDescription("80-40m: Fair").performClick()
        compose.onNodeWithText("Day: Fair • Night: Good").assertIsDisplayed()
        compose.onNodeWithText("It is daytime at the station now.").assertIsDisplayed()
        compose.onNodeWithText("Conditions calculated by N0NBH (hamqsl.com).").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-w411dp-h891dp")
    fun unavailableInEnglish() {
        show(PropagationUiState(status = PropagationState.Status.UNAVAILABLE))
        compose.onNodeWithText("Data unavailable").assertIsDisplayed()
    }

    @Test
    fun valuesAreTheOnesGiven() {
        show(data.copy(solarFlux = IndexUi("152.4", GOOD), kIndex = IndexUi("5", POOR), aIndex = IndexUi("31", POOR)))
        compose.onNodeWithText("SFI 152.4").assertIsDisplayed()
        compose.onNodeWithText("K 5").assertIsDisplayed()
        compose.onNodeWithText("A 31").assertIsDisplayed()
        assertColor(colors.poor.container, boxColor(PropagationTags.K))
        assertColor(colors.poor.container, boxColor(PropagationTags.A))
    }
}
