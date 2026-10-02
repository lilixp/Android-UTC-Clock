package io.github.lilixp.utcradioclock.ui.dashboard

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import io.github.lilixp.utcradioclock.domain.propagation.IndexScales
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
        // Under the bands only the update time: the source is in Settings → About the app
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
    fun noEstimate_noTitle() {
        show() // N0NBH data only (as before this phase)
        compose.onNodeWithTag(PropagationTags.ESTIMATE_TITLE).assertDoesNotExist()
    }

    // ---- The details panel under the card (no dialog: the panel explains the box last tapped) ----

    private fun panelValue() = compose.onNodeWithTag(PropagationTags.PANEL_VALUE)
    private fun panelTitle() = compose.onNodeWithTag(PropagationTags.PANEL_TITLE)
    private fun panelSubtitle() = compose.onNodeWithTag(PropagationTags.PANEL_SUBTITLE)

    /** Taps a box and brings the panel into view (the test screen is tall, the phone's may scroll). */
    private fun select(tag: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performClick()
        compose.onNodeWithTag(PropagationTags.PANEL).performScrollTo()
    }

    private fun assertNoDialogNorButtons() {
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.onNodeWithText("OK").assertDoesNotExist()
        compose.onNodeWithText("X").assertDoesNotExist()
    }

    private fun assertNoN0nbh() {
        assertEquals(0, compose.onAllNodesWithText("N0NBH", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithText("hamqsl", substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun panel_atFirst_aHint_nothingSelected() {
        show(withEstimate)
        compose.onNodeWithTag(PropagationTags.PANEL).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(PropagationTags.PANEL_HINT).assertTextEquals("Apasă pe un indice sau pe o bandă pentru detalii.")
        panelTitle().assertDoesNotExist()
        for (tag in listOf(PropagationTags.SFI, PropagationTags.K, PropagationTags.A)) compose.onNodeWithTag(tag).assertIsNotSelected()
        for (band in HfBand.entries) compose.onNodeWithTag(PropagationTags.estimate(band)).assertIsNotSelected()
        assertNoDialogNorButtons()
        assertNoN0nbh()
    }

    @Test
    fun panel_sfi_valueTitleLevelExplanationAndReferenceValues() {
        show()
        select(PropagationTags.SFI)
        compose.onNodeWithTag(PropagationTags.SFI).assertIsSelected()
        panelValue().assertTextEquals("93")
        panelTitle().assertTextEquals("Solar Flux Index (SFI)")
        panelSubtitle().assertTextEquals("Mediu")
        compose.onNodeWithText("Măsoară emisia radio solară la 10.7 cm", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Valori orientative").assertIsDisplayed()
        for (text in listOf("< 90", "90–119", "≥ 120", "Slab", "Bun pentru benzile înalte")) compose.onNodeWithText(text).assertIsDisplayed()
        compose.onNodeWithTag(PropagationTags.PANEL_HINT).assertDoesNotExist()
        assertNoDialogNorButtons()
    }

    @Test
    fun panel_k_largeValue_levelNow_andTheScaleOfTheColours() {
        show()
        select(PropagationTags.K)
        compose.onNodeWithTag(PropagationTags.K).assertIsSelected()
        panelValue().assertTextEquals("0")
        panelTitle().assertTextEquals("Indice K")
        panelSubtitle().assertTextEquals("Liniștit")
        compose.onNodeWithText("Indice al activității geomagnetice (0–9)", substring = true).assertIsDisplayed()
        // The scale that colours K (IndexScales): 0–3 quiet, 4 active, 5 and above storm
        for (text in listOf("Valori orientative", "0–3", "Liniștit", "4", "Activ", "≥ 5", "Furtună")) {
            compose.onNode(hasText(text) and hasAnyAncestorTag(PropagationTags.REFERENCE_VALUES)).assertIsDisplayed()
        }
    }

    @Test
    fun panel_referenceValues_areTheScalesThatColourTheBoxes() {
        // Built from IndexScales' own thresholds, so a box's colour and its reference value never disagree
        assertEquals(IndexScales.kIndex(IndexScales.K_ACTIVE - 1.0), GOOD)
        assertEquals(IndexScales.kIndex(IndexScales.K_ACTIVE.toDouble()), FAIR)
        assertEquals(IndexScales.kIndex(IndexScales.K_STORM.toDouble()), POOR)
        assertEquals(IndexScales.aIndex(IndexScales.A_ACTIVE - 1.0), GOOD)
        assertEquals(IndexScales.aIndex(IndexScales.A_STORM.toDouble()), POOR)
        assertEquals(IndexScales.solarFlux(IndexScales.SFI_FAIR - 1.0), POOR)
        assertEquals(IndexScales.solarFlux(IndexScales.SFI_GOOD.toDouble()), GOOD)
        show()
        select(PropagationTags.A)
        for (text in listOf("0–${IndexScales.A_ACTIVE - 1}", "${IndexScales.A_ACTIVE}–${IndexScales.A_STORM - 1}", "≥ ${IndexScales.A_STORM}")) {
            compose.onNodeWithText(text).assertIsDisplayed()
        }
    }

    @Test
    fun panel_k_storm_saysSo() {
        show(data.copy(kIndex = IndexUi("5", POOR)))
        select(PropagationTags.K)
        panelValue().assertTextEquals("5")
        panelSubtitle().assertTextEquals("Furtună")
        assertColor(colors.poor.container, boxColor(PropagationTags.PANEL_VALUE))
    }

    @Test
    fun panel_a_valueTitleAndInterpretation() {
        show()
        select(PropagationTags.A)
        compose.onNodeWithTag(PropagationTags.A).assertIsSelected()
        panelValue().assertTextEquals("3")
        panelTitle().assertTextEquals("Indice A")
        panelSubtitle().assertTextEquals("Liniștit sau instabil")
        compose.onNodeWithText("Media zilnică a activității geomagnetice", substring = true).assertIsDisplayed()
        for (text in listOf("0–15", "16–29", "≥ 30")) compose.onNodeWithText(text).assertIsDisplayed()
    }

    @Test
    fun panel_indexNotReported_dashNoLevel() {
        show(data.copy(kIndex = null))
        select(PropagationTags.K)
        panelValue().assertTextEquals("—")
        panelSubtitle().assertDoesNotExist()
        compose.onNodeWithText("Valori orientative").assertIsDisplayed()
    }

    @Test
    fun panel_bandGroup_nowByDayAndByNight() {
        show()
        select(PropagationTags.band(BandGroup.BANDS_80_40))
        compose.onNodeWithTag(PropagationTags.band(BandGroup.BANDS_80_40)).assertIsSelected()
        panelValue().assertTextEquals("80-40m")
        panelTitle().assertTextEquals("Acum: Mediu")
        panelSubtitle().assertTextEquals("Acum e zi la stație.")
        compose.onNodeWithText("Ziua").assertIsDisplayed()
        compose.onNodeWithText("Noaptea").assertIsDisplayed()
        assertColor(colors.fair.container, boxColor(PropagationTags.PANEL_VALUE))
        assertNoDialogNorButtons()
        assertNoN0nbh()

        select(PropagationTags.band(BandGroup.BANDS_12_10)) // another group, its own values
        panelValue().assertTextEquals("12-10m")
        panelTitle().assertTextEquals("Acum: Slab")
        compose.onNodeWithTag(PropagationTags.band(BandGroup.BANDS_80_40)).assertIsNotSelected()
    }

    @Test
    fun panel_bandGroup_withoutLocator_dayIsByTheClock() {
        show(data.copy(isDay = false, dayNightByClock = true))
        select(PropagationTags.band(BandGroup.BANDS_17_15))
        panelTitle().assertTextEquals("Acum: Mediu")
        panelSubtitle().assertTextEquals("Acum e noapte la stație.")
        compose.onNodeWithText("Fără locator, ziua este între 06:00 și 18:00 ora locală.").assertIsDisplayed()
    }

    @Test
    fun panel_band_theCalculatorsEstimate_andTheBandsExplanation() {
        show(withEstimate)
        select(PropagationTags.estimate(HfBand.BAND_15M))
        compose.onNodeWithTag(PropagationTags.estimate(HfBand.BAND_15M)).assertIsSelected()
        panelValue().assertTextEquals("15m")
        panelTitle().assertTextEquals("15 m · ${HfBand.BAND_15M.frequencies}")
        panelSubtitle().assertTextEquals("Estimare: Bun")
        compose.onNodeWithText("Acum e zi la stație.").assertIsDisplayed()
        compose.onNodeWithText("Bandă de zi. Puternic influențată de fluxul solar (SFI ≥ 90).").assertIsDisplayed()
        compose.onNodeWithText("Estimare offline, calculată pe telefon din SFI, K și Soarele la stație.").assertIsDisplayed()
        assertColor(colors.good.container, boxColor(PropagationTags.PANEL_VALUE))
        assertNoDialogNorButtons()
        assertNoN0nbh()
    }

    @Test
    fun panel_band_unknown_saysWhy() {
        show(withEstimate.copy(estimate = HfBand.entries.map { BandEstimate(it, null) }))
        select(PropagationTags.estimate(HfBand.BAND_20M))
        assertColor(colors.unknown.container, boxColor(PropagationTags.estimate(HfBand.BAND_20M)))
        panelSubtitle().assertTextEquals("Estimare: Necunoscut")
        compose.onNodeWithText("Lipsesc SFI sau indicele K, deci nu există estimare.").assertIsDisplayed()
        assertColor(colors.unknown.container, boxColor(PropagationTags.PANEL_VALUE))
    }

    @Test
    fun panel_band_withoutAPosition_saysWhy() {
        show(withEstimate.copy(estimate = HfBand.entries.map { BandEstimate(it, null) }, phase = null))
        select(PropagationTags.estimate(HfBand.BAND_40M))
        panelSubtitle().assertTextEquals("Estimare: Necunoscut")
        compose.onNodeWithText(
            "Fără poziție (locator sau GPS), ziua și noaptea la stație nu se cunosc, deci nu există estimare.",
        ).assertIsDisplayed()
        compose.onNodeWithText("Lipsesc SFI", substring = true).assertDoesNotExist()
    }

    @Test
    fun panel_everyBand_itsOwnFrequenciesAndExplanation() {
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
        for ((band, level) in HfBand.entries.zip(dayLevels)) {
            select(PropagationTags.estimate(band))
            panelTitle().assertTextEquals("${band.meters} m · ${band.frequencies}")
            panelSubtitle().assertTextEquals("Estimare: " + mapOf(GOOD to "Bun", FAIR to "Mediu", POOR to "Slab").getValue(level))
            compose.onNodeWithText("Acum e crepuscul la stație.").assertIsDisplayed()
            compose.onNodeWithText(explanation.getValue(band)).assertIsDisplayed()
        }
    }

    @Test
    fun panel_sfiThenKThenABand_followsTheSelection_oneSelectedAtATime() {
        show(withEstimate)
        select(PropagationTags.SFI)
        panelTitle().assertTextEquals("Solar Flux Index (SFI)")
        select(PropagationTags.K)
        panelTitle().assertTextEquals("Indice K")
        compose.onNodeWithTag(PropagationTags.SFI).assertIsNotSelected()
        select(PropagationTags.estimate(HfBand.BAND_40M))
        panelTitle().assertTextEquals("40 m · ${HfBand.BAND_40M.frequencies}")
        select(PropagationTags.estimate(HfBand.BAND_15M))
        panelTitle().assertTextEquals("15 m · ${HfBand.BAND_15M.frequencies}")
        compose.onNodeWithTag(PropagationTags.K).assertIsNotSelected()
        compose.onNodeWithTag(PropagationTags.estimate(HfBand.BAND_40M)).assertIsNotSelected()
        compose.onNodeWithTag(PropagationTags.estimate(HfBand.BAND_15M)).assertIsSelected()
        // A second tap on the same box keeps it: nothing to close
        compose.onNodeWithTag(PropagationTags.estimate(HfBand.BAND_15M)).performClick()
        panelTitle().assertTextEquals("15 m · ${HfBand.BAND_15M.frequencies}")
        assertNoDialogNorButtons()
    }

    @Test
    fun panel_selectingKeepsTheBoxesSize() {
        show()
        val before = compose.onNodeWithTag(PropagationTags.K).fetchSemanticsNode().size
        select(PropagationTags.K)
        assertEquals(before, compose.onNodeWithTag(PropagationTags.K).fetchSemanticsNode().size)
    }

    @Test
    fun panel_followsNewData_withoutAnotherTap() {
        var propagation by mutableStateOf(withEstimate)
        compose.setContent {
            UTCRadioClockTheme(darkTheme = false) {
                colors = LocalConditionColors.current
                DashboardScreen(dashboard(propagation), selectedTab = AppTab.PROPAGATION) {}
            }
        }
        select(PropagationTags.K)
        panelValue().assertTextEquals("0")
        propagation = withEstimate.copy(kIndex = IndexUi("4", FAIR)) // the next N0NBH update
        compose.waitForIdle()
        panelValue().assertTextEquals("4")
        panelSubtitle().assertTextEquals("Activ")
        compose.onNodeWithTag(PropagationTags.K).assertIsSelected()
    }

    @Test
    fun panel_selectionKeptThroughARotation() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            UTCRadioClockTheme(darkTheme = false) {
                DashboardScreen(dashboard(withEstimate), selectedTab = AppTab.PROPAGATION) {}
            }
        }
        select(PropagationTags.estimate(HfBand.BAND_15M))
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag(PropagationTags.estimate(HfBand.BAND_15M)).assertIsSelected()
        panelValue().assertTextEquals("15m")
    }

    @Test
    fun panel_loading_noPanel() {
        show(PropagationUiState(status = PropagationState.Status.LOADING))
        compose.onNodeWithTag(PropagationTags.PANEL).assertDoesNotExist()
    }

    @Test
    fun panel_unavailable_noPanel() {
        show(PropagationUiState(status = PropagationState.Status.UNAVAILABLE))
        compose.onNodeWithTag(PropagationTags.PANEL).assertDoesNotExist()
    }

    @Test
    fun panel_staleData_stillExplained() {
        show(data.copy(status = PropagationState.Status.STALE, updated = "30 sept. 21:00"))
        select(PropagationTags.SFI)
        panelValue().assertTextEquals("93")
    }

    @Test
    fun noN0nbhNorHfInTheTitle() {
        show(withEstimate)
        compose.onNodeWithText("PROPAGARE").assertIsDisplayed()
        compose.onNodeWithText("Propagare HF", substring = true, ignoreCase = true).assertDoesNotExist()
        for (tag in listOf(PropagationTags.SFI, PropagationTags.band(BandGroup.BANDS_30_20), PropagationTags.estimate(HfBand.BAND_20M))) {
            select(tag)
            assertNoN0nbh()
        }
    }

    @Test
    fun panel_darkTheme() {
        show(withEstimate, dark = true)
        select(PropagationTags.K)
        panelTitle().assertTextEquals("Indice K")
        assertColor(colors.good.container, boxColor(PropagationTags.PANEL_VALUE))
        select(PropagationTags.estimate(HfBand.BAND_10M))
        assertColor(colors.poor.container, boxColor(PropagationTags.PANEL_VALUE))
    }

    @Test
    @Config(qualifiers = "en-w411dp-h891dp")
    fun panel_inEnglish() {
        show(withEstimate.copy(phase = DayPhase.NIGHT))
        compose.onNodeWithTag(PropagationTags.PANEL_HINT).performScrollTo().assertTextEquals("Tap an index or a band for details.")
        select(PropagationTags.K)
        panelTitle().assertTextEquals("K index")
        panelSubtitle().assertTextEquals("Quiet")
        for (text in listOf("Reference values", "0–3", "Active", "≥ 5", "Storm")) compose.onNodeWithText(text).assertIsDisplayed()
        select(PropagationTags.A)
        panelTitle().assertTextEquals("A index")
        panelSubtitle().assertTextEquals("Quiet or unsettled")
        select(PropagationTags.SFI)
        panelSubtitle().assertTextEquals("Fair")
        select(PropagationTags.band(BandGroup.BANDS_80_40))
        panelTitle().assertTextEquals("Now: Fair")
        for (text in listOf("By day", "At night", "It is daytime at the station now.")) compose.onNodeWithText(text).assertIsDisplayed()
        select(PropagationTags.estimate(HfBand.BAND_160M))
        panelSubtitle().assertTextEquals("Estimate: Poor")
        compose.onNodeWithText("It is night at the station now.").assertIsDisplayed()
        compose.onNodeWithText("Offline estimate, calculated on the phone from SFI, K and the Sun at the station.").assertIsDisplayed()
        assertNoN0nbh()
    }

    private fun dashboard(propagation: PropagationUiState) = DashboardUiState(
        utcDate = "1 octombrie 2026",
        utcTime = "05:42:31",
        localTime = "08:42:31",
        localDate = null,
        zone = ZoneUi(name = "Europe/Chisinau", abbreviation = "EEST", offset = "UTC+03:00"),
        propagation = propagation,
    )

    private fun hasAnyAncestorTag(tag: String) = androidx.compose.ui.test.hasAnyAncestor(hasTestTag(tag))
}
