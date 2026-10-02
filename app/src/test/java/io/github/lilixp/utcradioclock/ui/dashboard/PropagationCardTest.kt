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
import org.junit.Assert.assertFalse
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

    // ---- N0NBH's data (online or saved); the state brings an estimate only for a group N0NBH has not ----

    /** Now day at the station, N0NBH data for every group (SFI 93, K 0): nothing estimated. */
    private val n0nbhNow = data.copy(phase = DayPhase.DAY)

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
        show(n0nbhNow)
        compose.onNodeWithTag(PropagationTags.PANEL).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(PropagationTags.PANEL_HINT).assertTextEquals("Apasă pe un indice sau pe o bandă pentru detalii.")
        panelTitle().assertDoesNotExist()
        for (tag in listOf(PropagationTags.SFI, PropagationTags.K, PropagationTags.A)) compose.onNodeWithTag(tag).assertIsNotSelected()
        for (group in BandGroup.entries) compose.onNodeWithTag(PropagationTags.band(group)).assertIsNotSelected()
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
    fun panel_bandGroup_n0nbhNowByDayAndByNight_noSentenceAboutTheStation() {
        show(n0nbhNow)
        select(PropagationTags.band(BandGroup.BANDS_80_40))
        compose.onNodeWithTag(PropagationTags.band(BandGroup.BANDS_80_40)).assertIsSelected()
        panelValue().assertTextEquals("80-40m")
        panelTitle().assertTextEquals("Mediu")
        compose.onNodeWithText("Acum:", substring = true).assertDoesNotExist()
        panelSubtitle().assertDoesNotExist()
        compose.onNodeWithText("Ziua").assertIsDisplayed()
        compose.onNodeWithText("Noaptea").assertIsDisplayed()
        assertColor(colors.fair.container, boxColor(PropagationTags.PANEL_VALUE))
        for (text in listOf("Acum e zi la stație", "Acum e noapte la stație", "Acum e crepuscul la stație")) {
            compose.onNodeWithText(text, substring = true).assertDoesNotExist()
        }
        compose.onNodeWithTag(PropagationTags.ESTIMATE_TABLE).assertDoesNotExist() // N0NBH's data, nothing estimated
        assertNoDialogNorButtons()
        assertNoN0nbh()

        select(PropagationTags.band(BandGroup.BANDS_12_10)) // another group, its own values
        panelValue().assertTextEquals("12-10m")
        panelTitle().assertTextEquals("Slab")
        compose.onNodeWithTag(PropagationTags.band(BandGroup.BANDS_80_40)).assertIsNotSelected()
    }

    @Test
    fun panel_bandGroup_withoutLocator_dayIsByTheClock() {
        show(n0nbhNow.copy(isDay = false, dayNightByClock = true, phase = null))
        select(PropagationTags.band(BandGroup.BANDS_17_15))
        panelTitle().assertTextEquals("Mediu")
        panelSubtitle().assertDoesNotExist()
        compose.onNodeWithText("Noaptea").assertIsDisplayed()
        compose.onNodeWithText("Fără locator, ziua este între 06:00 și 18:00 ora locală.").assertIsDisplayed()
    }

    // ---- N0NBH first: a group's own data, or (only without it) the offline estimate the state brings ----

    private fun cell(band: HfBand, phase: DayPhase) = compose.onNodeWithTag(PropagationTags.estimateCell(band, phase))

    /** The state's fallback for 12-10m as the ViewModel would bring it (SFI 93, K 0); here, just data. */
    private val fallback1210 = listOf(
        BandPhasesUi(HfBand.BAND_12M, day = FAIR, twilight = POOR, night = POOR),
        BandPhasesUi(HfBand.BAND_10M, day = POOR, twilight = POOR, night = POOR),
    )

    /** N0NBH reported nothing for 12-10m: its box is neutral and its panel shows the state's estimate. */
    private val withFallback = n0nbhNow.copy(
        bands = n0nbhNow.bands.map {
            if (it.group == BandGroup.BANDS_12_10) BandUi(it.group, null, null, null, estimate = fallback1210) else it
        },
    )

    @Test
    fun card_onlyTheIndicesTheFourGroupsAndTheUpdate_noTenBandBoxes() {
        show(withFallback)
        for (tag in listOf(PropagationTags.SFI, PropagationTags.K, PropagationTags.A)) compose.onNodeWithTag(tag).assertIsDisplayed()
        for (group in BandGroup.entries) compose.onNodeWithTag(PropagationTags.band(group)).assertIsDisplayed()
        compose.onNodeWithText("Actualizat 05:29 UTC").assertIsDisplayed()
        compose.onNodeWithText("Estimare offline · 10 benzi").assertDoesNotExist()
        compose.onNodeWithText("Estimare offline").assertDoesNotExist() // only in a group's panel
        for (band in HfBand.entries) {
            compose.onNodeWithText(band.label).assertDoesNotExist() // "160m", "80m" … "10m"
            compose.onNodeWithText("${band.meters} m").assertDoesNotExist()
        }
    }

    @Test
    fun groupWithN0nbhData_onlyN0nbh_noSecondEstimate() {
        show(n0nbhNow)
        for (group in BandGroup.entries) {
            select(PropagationTags.band(group))
            compose.onNodeWithText("Ziua").assertIsDisplayed()
            compose.onNodeWithTag(PropagationTags.ESTIMATE_TABLE).assertDoesNotExist()
            compose.onNodeWithText("Estimare offline", substring = true).assertDoesNotExist()
            compose.onNodeWithText("Lipsesc SFI", substring = true).assertDoesNotExist()
        }
    }

    @Test
    fun groupFromTheCache_isN0nbhData_noEstimate() {
        show(n0nbhNow.copy(status = PropagationState.Status.STALE, updated = "30 sept. 21:00"))
        compose.onNodeWithText("Date neactualizate · ultima actualizare 30 sept. 21:00 UTC").assertIsDisplayed()
        select(PropagationTags.band(BandGroup.BANDS_30_20))
        panelTitle().assertTextEquals("Bun")
        compose.onNodeWithTag(PropagationTags.ESTIMATE_TABLE).assertDoesNotExist()
    }

    @Test
    fun groupWithoutN0nbhData_neutralBox_andTheStatesEstimateAsItIs() {
        show(withFallback)
        assertBand(BandGroup.BANDS_12_10, null) // no level made up for the group
        compose.onNodeWithContentDescription("12-10m: —").assertIsDisplayed()
        select(PropagationTags.band(BandGroup.BANDS_12_10))
        panelValue().assertTextEquals("12-10m")
        panelTitle().assertTextEquals("—")
        assertColor(colors.unknown.container, boxColor(PropagationTags.PANEL_VALUE))
        compose.onNodeWithTag(PropagationTags.ESTIMATE_TABLE).assertIsDisplayed()
        for (text in listOf("Estimare offline", "Bandă", "Zi", "Amurg", "Noapte", "12 m", "10 m")) compose.onNodeWithText(text).assertIsDisplayed()
        for (row in fallback1210) {
            cell(row.band, DayPhase.DAY).assertTextEquals(levelText(row.day))
            cell(row.band, DayPhase.TWILIGHT).assertTextEquals(levelText(row.twilight))
            cell(row.band, DayPhase.NIGHT).assertTextEquals(levelText(row.night))
        }
        compose.onNodeWithText("Estimare offline, calculată pe telefon din SFI, K și Soarele la stație.").assertIsDisplayed()
        compose.onNodeWithText("Lipsesc SFI", substring = true).assertDoesNotExist()
        compose.onNodeWithText("160 m").assertDoesNotExist()
        assertNoN0nbh()
    }

    @Test
    fun table_showsExactlyWhatTheStateBrings_nothingRecalculated() {
        // Values no calculator would give for SFI 93 and K 0: the table shows them as they are
        val unusual = listOf(
            BandPhasesUi(HfBand.BAND_12M, day = GOOD, twilight = GOOD, night = GOOD),
            BandPhasesUi(HfBand.BAND_10M, day = GOOD, twilight = FAIR, night = GOOD),
        )
        show(withFallback.copy(bands = withFallback.bands.map { if (it.estimate != null) it.copy(estimate = unusual) else it }))
        select(PropagationTags.band(BandGroup.BANDS_12_10))
        cell(HfBand.BAND_12M, DayPhase.NIGHT).assertTextEquals("Bun")
        cell(HfBand.BAND_10M, DayPhase.TWILIGHT).assertTextEquals("Mediu")
        assertColor(colors.good.container, boxColor(PropagationTags.estimateCell(HfBand.BAND_10M, DayPhase.DAY)))
    }

    @Test
    fun table_rowsInTheOrderTheStateBrings() {
        val rows8040 = listOf(
            BandPhasesUi(HfBand.BAND_80M, POOR, GOOD, GOOD),
            BandPhasesUi(HfBand.BAND_60M, FAIR, GOOD, GOOD),
            BandPhasesUi(HfBand.BAND_40M, FAIR, GOOD, GOOD),
        )
        show(n0nbhNow.copy(bands = n0nbhNow.bands.map { if (it.group == BandGroup.BANDS_80_40) BandUi(it.group, null, null, null, rows8040) else it }))
        select(PropagationTags.band(BandGroup.BANDS_80_40))
        val rows = compose.onAllNodes(hasTestTagStartingWith("estimate_row_")).fetchSemanticsNodes()
            .map { it.config[androidx.compose.ui.semantics.SemanticsProperties.TestTag] }
        assertEquals(listOf("estimate_row_80", "estimate_row_60", "estimate_row_40"), rows)
        cell(HfBand.BAND_80M, DayPhase.DAY).assertTextEquals("Slab")
        cell(HfBand.BAND_80M, DayPhase.NIGHT).assertTextEquals("Bun")
    }

    @Test
    fun table_unknownLevels_saidUnknown_inTheNeutralColour_andWhy() {
        val withoutSfi = listOf(
            BandPhasesUi(HfBand.BAND_30M, day = null, twilight = GOOD, night = GOOD),
            BandPhasesUi(HfBand.BAND_20M, day = GOOD, twilight = GOOD, night = null),
        )
        show(
            n0nbhNow.copy(
                solarFlux = null,
                bands = n0nbhNow.bands.map { if (it.group == BandGroup.BANDS_30_20) BandUi(it.group, null, null, null, withoutSfi) else it },
            ),
        )
        select(PropagationTags.band(BandGroup.BANDS_30_20))
        cell(HfBand.BAND_30M, DayPhase.DAY).assertTextEquals("Necunoscut")
        cell(HfBand.BAND_20M, DayPhase.NIGHT).assertTextEquals("Necunoscut")
        cell(HfBand.BAND_30M, DayPhase.NIGHT).assertTextEquals("Bun")
        assertColor(colors.unknown.container, boxColor(PropagationTags.estimateCell(HfBand.BAND_30M, DayPhase.DAY)))
        compose.onNodeWithText("Lipsesc SFI sau indicele K, deci nu există estimare.").assertIsDisplayed()
    }

    @Test
    fun groupWithoutN0nbh_andNoEstimate_neutral_saysWhy() {
        show(
            n0nbhNow.copy(
                solarFlux = null,
                kIndex = null,
                bands = n0nbhNow.bands.map { if (it.group == BandGroup.BANDS_17_15) BandUi(it.group, null, null, null) else it },
            ),
        )
        assertBand(BandGroup.BANDS_17_15, null)
        select(PropagationTags.band(BandGroup.BANDS_17_15))
        panelTitle().assertTextEquals("—")
        compose.onNodeWithTag(PropagationTags.ESTIMATE_TABLE).assertDoesNotExist()
        compose.onNodeWithText("Lipsesc SFI sau indicele K, deci nu există estimare.").assertIsDisplayed()
    }

    @Test
    fun theUiNeitherParsesTheShownValuesNorCallsTheCalculator() {
        // The card is presentation only: the estimate comes calculated in the state (ViewModel)
        val source = java.io.File("src/main/java/io/github/lilixp/utcradioclock/ui/dashboard/PropagationCard.kt").readText()
        assertFalse(source.contains("OfflinePropagationCalculator"))
        assertFalse(source.contains("toDouble"))
        assertFalse(source.contains("IndexScales.kIndex(") || source.contains("IndexScales.solarFlux("))
    }

    @Test
    fun panel_sfiThenKThenAGroup_followsTheSelection_oneSelectedAtATime() {
        show(withFallback)
        select(PropagationTags.SFI)
        panelTitle().assertTextEquals("Solar Flux Index (SFI)")
        select(PropagationTags.K)
        panelTitle().assertTextEquals("Indice K")
        compose.onNodeWithTag(PropagationTags.SFI).assertIsNotSelected()
        select(PropagationTags.band(BandGroup.BANDS_80_40))
        panelValue().assertTextEquals("80-40m")
        select(PropagationTags.band(BandGroup.BANDS_12_10))
        panelValue().assertTextEquals("12-10m")
        compose.onNodeWithTag(PropagationTags.K).assertIsNotSelected()
        compose.onNodeWithTag(PropagationTags.band(BandGroup.BANDS_80_40)).assertIsNotSelected()
        compose.onNodeWithTag(PropagationTags.band(BandGroup.BANDS_12_10)).assertIsSelected()
        // A second tap on the same box keeps it: nothing to close
        compose.onNodeWithTag(PropagationTags.band(BandGroup.BANDS_12_10)).performClick()
        panelValue().assertTextEquals("12-10m")
        assertNoDialogNorButtons()
    }

    @Test
    fun panel_selectingKeepsTheBoxesSize() {
        show()
        for (tag in listOf(PropagationTags.K, PropagationTags.band(BandGroup.BANDS_12_10))) {
            val before = compose.onNodeWithTag(tag).fetchSemanticsNode().size
            select(tag)
            assertEquals(before, compose.onNodeWithTag(tag).fetchSemanticsNode().size)
        }
    }

    @Test
    fun panel_followsNewData_withoutAnotherTap() {
        var propagation by mutableStateOf(withFallback)
        compose.setContent {
            UTCRadioClockTheme(darkTheme = false) {
                colors = LocalConditionColors.current
                DashboardScreen(dashboard(propagation), selectedTab = AppTab.PROPAGATION) {}
            }
        }
        select(PropagationTags.band(BandGroup.BANDS_12_10))
        cell(HfBand.BAND_12M, DayPhase.DAY).assertTextEquals("Mediu")
        // The next N0NBH update reports the group: its own data replaces the estimate
        propagation = n0nbhNow
        compose.waitForIdle()
        panelTitle().assertTextEquals("Slab")
        compose.onNodeWithTag(PropagationTags.ESTIMATE_TABLE).assertDoesNotExist()
        compose.onNodeWithTag(PropagationTags.band(BandGroup.BANDS_12_10)).assertIsSelected()
    }

    @Test
    fun panel_selectionKeptThroughARotation() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            UTCRadioClockTheme(darkTheme = false) {
                DashboardScreen(dashboard(withFallback), selectedTab = AppTab.PROPAGATION) {}
            }
        }
        select(PropagationTags.band(BandGroup.BANDS_12_10))
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag(PropagationTags.band(BandGroup.BANDS_12_10)).assertIsSelected()
        panelValue().assertTextEquals("12-10m")
        compose.onNodeWithTag(PropagationTags.estimateRow(HfBand.BAND_10M)).assertExists()
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
        show(withFallback.copy(status = PropagationState.Status.STALE, updated = "30 sept. 21:00"))
        select(PropagationTags.SFI)
        panelValue().assertTextEquals("93")
        select(PropagationTags.band(BandGroup.BANDS_12_10))
        compose.onNodeWithTag(PropagationTags.ESTIMATE_TABLE).assertIsDisplayed()
    }

    @Test
    fun noN0nbhNorHfInTheTitle() {
        show(withFallback)
        compose.onNodeWithText("PROPAGARE").assertIsDisplayed()
        compose.onNodeWithText("Propagare HF", substring = true, ignoreCase = true).assertDoesNotExist()
        for (tag in listOf(PropagationTags.SFI, PropagationTags.band(BandGroup.BANDS_30_20), PropagationTags.band(BandGroup.BANDS_12_10))) {
            select(tag)
            assertNoN0nbh()
        }
    }

    @Test
    fun panel_darkTheme() {
        show(withFallback, dark = true)
        select(PropagationTags.K)
        panelTitle().assertTextEquals("Indice K")
        assertColor(colors.good.container, boxColor(PropagationTags.PANEL_VALUE))
        select(PropagationTags.band(BandGroup.BANDS_12_10))
        assertColor(colors.unknown.container, boxColor(PropagationTags.PANEL_VALUE))
        assertColor(colors.poor.container, boxColor(PropagationTags.estimateCell(HfBand.BAND_10M, DayPhase.NIGHT)))
        assertColor(colors.fair.container, boxColor(PropagationTags.estimateCell(HfBand.BAND_12M, DayPhase.DAY)))
    }

    @Test
    @Config(qualifiers = "en-w411dp-h891dp")
    fun panel_inEnglish() {
        val withoutSfi = listOf(
            BandPhasesUi(HfBand.BAND_30M, day = null, twilight = GOOD, night = GOOD),
            BandPhasesUi(HfBand.BAND_20M, day = GOOD, twilight = GOOD, night = null),
        )
        show(
            n0nbhNow.copy(
                solarFlux = null,
                bands = n0nbhNow.bands.map { if (it.group == BandGroup.BANDS_30_20) BandUi(it.group, null, null, null, withoutSfi) else it },
            ),
        )
        compose.onNodeWithTag(PropagationTags.PANEL_HINT).performScrollTo().assertTextEquals("Tap an index or a band for details.")
        select(PropagationTags.K)
        panelTitle().assertTextEquals("K index")
        panelSubtitle().assertTextEquals("Quiet")
        for (text in listOf("Reference values", "0–3", "Active", "≥ 5", "Storm")) compose.onNodeWithText(text).assertIsDisplayed()
        select(PropagationTags.A)
        panelTitle().assertTextEquals("A index")
        panelSubtitle().assertTextEquals("Quiet or unsettled")
        select(PropagationTags.band(BandGroup.BANDS_80_40))
        panelTitle().assertTextEquals("Fair")
        compose.onNodeWithText("Offline estimate").assertDoesNotExist() // N0NBH has this group
        select(PropagationTags.band(BandGroup.BANDS_30_20))
        panelTitle().assertTextEquals("—")
        for (text in listOf("By day", "At night", "Offline estimate", "Band", "Day", "Twilight", "Night", "30 m", "20 m")) {
            compose.onNodeWithText(text).assertIsDisplayed()
        }
        cell(HfBand.BAND_30M, DayPhase.DAY).assertTextEquals("Unknown")
        cell(HfBand.BAND_30M, DayPhase.NIGHT).assertTextEquals("Good")
        compose.onNodeWithText("SFI or the K index is missing, so there is no estimate.").assertIsDisplayed()
        compose.onNodeWithText("Offline estimate, calculated on the phone from SFI, K and the Sun at the station.").assertIsDisplayed()
        compose.onNodeWithText("daytime at the station", substring = true).assertDoesNotExist()
        assertNoN0nbh()
    }

    private fun levelText(level: ConditionLevel?) = when (level) {
        GOOD -> "Bun"
        FAIR -> "Mediu"
        POOR -> "Slab"
        null -> "Necunoscut"
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

    private fun hasTestTagStartingWith(prefix: String) = androidx.compose.ui.test.SemanticsMatcher("test tag starts with $prefix") {
        androidx.compose.ui.semantics.SemanticsProperties.TestTag in it.config &&
            it.config[androidx.compose.ui.semantics.SemanticsProperties.TestTag].startsWith(prefix)
    }
}
