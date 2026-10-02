package io.github.lilixp.utcradioclock.ui.dashboard

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import io.github.lilixp.utcradioclock.R
import io.github.lilixp.utcradioclock.data.propagation.PropagationState
import io.github.lilixp.utcradioclock.domain.model.BandEstimate
import io.github.lilixp.utcradioclock.domain.model.BandGroup
import io.github.lilixp.utcradioclock.domain.model.ConditionLevel
import io.github.lilixp.utcradioclock.domain.model.ConditionLevel.FAIR
import io.github.lilixp.utcradioclock.domain.model.ConditionLevel.GOOD
import io.github.lilixp.utcradioclock.domain.model.ConditionLevel.POOR
import io.github.lilixp.utcradioclock.domain.model.DayPhase
import io.github.lilixp.utcradioclock.domain.model.HfBand
import io.github.lilixp.utcradioclock.ui.theme.ConditionColors
import io.github.lilixp.utcradioclock.ui.theme.LocalConditionColors
import io.github.lilixp.utcradioclock.ui.theme.UTCRadioClockTheme
import org.junit.Assert.assertEquals
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
            zone = ZoneUi(name = "Europe/Chisinau", abbreviation = "EEST", offset = "UTC+03:00"),
            propagation = propagation,
        )
        compose.setContent {
            UTCRadioClockTheme(darkTheme = dark) {
                colors = LocalConditionColors.current
                DashboardScreen(state, selectedTab = AppTab.PROPAGATION) {}
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
        compose.onNodeWithText("Actualizat 05:29 UTC").assertIsDisplayed()
        // Under the bands only the update time: the source is in the band dialog and in Settings
        compose.onNodeWithText("hamqsl.com", substring = true).assertDoesNotExist()
    }

    @Test
    fun withoutN0nbhTime_nothingUnderTheBands() {
        show(data.copy(updated = null))
        compose.onNodeWithText("12-10m").assertIsDisplayed()
        compose.onNodeWithText("Actualizat", substring = true).assertDoesNotExist()
        compose.onNodeWithText("hamqsl.com", substring = true).assertDoesNotExist()
    }

    @Test
    fun staleWithoutN0nbhTime_stillSaysNotUpdated() {
        show(data.copy(status = PropagationState.Status.STALE, updated = null))
        compose.onNodeWithText("Date neactualizate").assertIsDisplayed()
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
        compose.onNodeWithText("Updated 05:29 UTC").assertIsDisplayed()
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

    // ---- The offline estimate for the ten HF bands, and the explanations ----

    //                       160m  80m   60m   40m   30m   20m   17m   15m   12m   10m
    private val dayLevels = listOf(POOR, POOR, FAIR, FAIR, GOOD, GOOD, GOOD, GOOD, FAIR, POOR)
    private val withEstimate = data.copy(
        estimate = HfBand.entries.zip(dayLevels) { band, level -> BandEstimate(band, level) },
        phase = DayPhase.DAY,
    )

    private fun scrollTo(tag: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag(tag))
    }

    @Test
    fun estimate_tenBands_underTheirOwnTitle_afterTheN0nbhData() {
        show(withEstimate)
        scrollTo(PropagationTags.estimate(HfBand.BAND_10M))
        compose.onNodeWithTag(PropagationTags.ESTIMATE_TITLE).assertTextEquals("Estimare offline · 10 benzi")
        for (band in HfBand.entries) compose.onNodeWithTag(PropagationTags.estimate(band)).assertIsDisplayed()
        compose.onNodeWithText("160m").assertIsDisplayed()
        // Below N0NBH's own data, which ends with the update time
        val updated = compose.onNodeWithText("Actualizat 05:29 UTC").fetchSemanticsNode().positionInRoot.y
        val title = compose.onNodeWithTag(PropagationTags.ESTIMATE_TITLE).fetchSemanticsNode().positionInRoot.y
        assertTrue(title > updated)
    }

    @Test
    fun estimate_coloursFromTheEstimatedLevels() {
        show(withEstimate)
        scrollTo(PropagationTags.estimate(HfBand.BAND_10M))
        assertColor(colors.poor.container, boxColor(PropagationTags.estimate(HfBand.BAND_160M)))
        assertColor(colors.good.container, boxColor(PropagationTags.estimate(HfBand.BAND_20M)))
        assertColor(colors.fair.container, boxColor(PropagationTags.estimate(HfBand.BAND_12M)))
    }

    @Test
    fun estimate_unknownBands_neutralColourAndSaidSo() {
        show(withEstimate.copy(estimate = HfBand.entries.map { BandEstimate(it, null) }))
        scrollTo(PropagationTags.estimate(HfBand.BAND_10M))
        assertColor(colors.unknown.container, boxColor(PropagationTags.estimate(HfBand.BAND_20M)))
        compose.onNodeWithContentDescription("20m: Necunoscut").performClick()
        compose.onNodeWithText("Estimare: Necunoscut").assertIsDisplayed()
        compose.onNodeWithText("N0NBH nu a dat SFI sau indicele K, deci nu există estimare.").assertIsDisplayed()
    }

    @Test
    fun estimate_withoutAPosition_theDialogSaysWhy() {
        show(withEstimate.copy(estimate = HfBand.entries.map { BandEstimate(it, null) }, phase = null))
        scrollTo(PropagationTags.estimate(HfBand.BAND_10M))
        compose.onNodeWithContentDescription("40m: Necunoscut").performClick()
        compose.onNodeWithText(
            "Fără poziție (locator sau GPS), ziua și noaptea la stație nu se cunosc, deci nu există estimare.",
        ).assertIsDisplayed()
    }

    @Test
    fun noEstimate_noTitle() {
        show() // N0NBH data only (as before this phase)
        compose.onNodeWithTag(PropagationTags.ESTIMATE_TITLE).assertDoesNotExist()
    }

    @Test
    fun tapOnAnEstimatedBand_explainsIt_andSaysItIsNotN0nbh() {
        show(withEstimate)
        scrollTo(PropagationTags.estimate(HfBand.BAND_10M))
        compose.onNodeWithContentDescription("20m: Bun").performClick()
        compose.onNodeWithText("20 m · 14.0–14.35 MHz").assertIsDisplayed()
        compose.onNodeWithText("Estimare: Bun").assertIsDisplayed()
        compose.onNodeWithText("Acum e zi la stație.").assertIsDisplayed()
        compose.onNodeWithText("Banda principală HF. Deschisă ziua și la crepuscul; noaptea depinde de SFI.").assertIsDisplayed()
        compose.onNodeWithText("Nu este o valoare publicată de N0NBH", substring = true).assertIsDisplayed()
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithText("20 m · 14.0–14.35 MHz").assertDoesNotExist()
    }

    @Test
    fun everyEstimatedBand_hasItsOwnExplanation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val explanation = mapOf(
            HfBand.BAND_160M to R.string.band_160m_dialog, HfBand.BAND_80M to R.string.band_80m_dialog,
            HfBand.BAND_60M to R.string.band_60m_dialog, HfBand.BAND_40M to R.string.band_40m_dialog,
            HfBand.BAND_30M to R.string.band_30m_dialog, HfBand.BAND_20M to R.string.band_20m_dialog,
            HfBand.BAND_17M to R.string.band_17m_dialog, HfBand.BAND_15M to R.string.band_15m_dialog,
            HfBand.BAND_12M to R.string.band_12m_dialog, HfBand.BAND_10M to R.string.band_10m_dialog,
        ).mapValues { context.getString(it.value) }
        assertEquals(10, explanation.values.toSet().size) // ten different texts
        show(withEstimate.copy(phase = DayPhase.TWILIGHT))
        for (band in HfBand.entries) {
            scrollTo(PropagationTags.estimate(band))
            compose.onNodeWithTag(PropagationTags.estimate(band)).performClick()
            compose.onNodeWithText("${band.meters} m · ${band.frequencies}").assertIsDisplayed()
            compose.onNodeWithText("Acum e crepuscul la stație.").assertIsDisplayed()
            compose.onNodeWithText(explanation.getValue(band)).assertIsDisplayed()
            compose.onNodeWithText("OK").performClick()
        }
    }

    @Test
    fun tapOnSfiKA_explainsThem() {
        show()
        compose.onNodeWithTag(PropagationTags.SFI).performClick()
        compose.onNodeWithText("Solar Flux Index (SFI)").assertIsDisplayed()
        compose.onNodeWithText("Măsoară emisia radio solară la 10.7 cm", substring = true).assertIsDisplayed()
        compose.onNodeWithText("OK").performClick()

        compose.onNodeWithTag(PropagationTags.K).performClick()
        compose.onNodeWithText("Indicele K (Geomagnetic)").assertIsDisplayed()
        // The same scale as the colour of K (IndexScales): 4 active, 5 and above storm
        compose.onNodeWithText("K 0–3: liniștit; K 4: activ; K 5 sau mai mult: furtună geomagnetică", substring = true).assertIsDisplayed()
        compose.onNodeWithText("OK").performClick()

        compose.onNodeWithTag(PropagationTags.A).performClick()
        compose.onNodeWithText("Indicele A").assertIsDisplayed()
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithText("Indicele A").assertDoesNotExist()
    }

    @Test
    fun theN0nbhBandDialogStillWorks_besideTheNewOnes() {
        show(withEstimate)
        compose.onNodeWithContentDescription("80-40m: Mediu").performClick()
        compose.onNodeWithText("Ziua: Mediu • Noaptea: Bun").assertIsDisplayed()
        compose.onNodeWithText("Condiții calculate de N0NBH (hamqsl.com).").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-w411dp-h891dp")
    fun estimateAndExplanationsInEnglish() {
        show(withEstimate.copy(phase = DayPhase.NIGHT))
        scrollTo(PropagationTags.estimate(HfBand.BAND_10M))
        compose.onNodeWithTag(PropagationTags.ESTIMATE_TITLE).assertTextEquals("Offline estimate · 10 bands")
        compose.onNodeWithContentDescription("160m: Poor").performClick()
        compose.onNodeWithText("Estimate: Poor").assertIsDisplayed()
        compose.onNodeWithText("It is night at the station now.").assertIsDisplayed()
        compose.onNodeWithText("It is not a value published by N0NBH", substring = true).assertIsDisplayed()
        compose.onNodeWithText("OK").performClick()
        scrollTo(PropagationTags.K)
        compose.onNodeWithTag(PropagationTags.K).performClick()
        compose.onNodeWithText("Planetary K-Index").assertIsDisplayed()
        compose.onNodeWithText("K 4: active; K 5 and above: geomagnetic storm", substring = true).assertIsDisplayed()
    }
}
