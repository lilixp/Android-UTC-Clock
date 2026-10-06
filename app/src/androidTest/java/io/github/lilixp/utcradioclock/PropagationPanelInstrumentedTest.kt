package io.github.lilixp.utcradioclock

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.lilixp.utcradioclock.data.propagation.PropagationState
import io.github.lilixp.utcradioclock.domain.model.BandGroup
import io.github.lilixp.utcradioclock.domain.model.ConditionLevel.FAIR
import io.github.lilixp.utcradioclock.domain.model.ConditionLevel.GOOD
import io.github.lilixp.utcradioclock.domain.model.ConditionLevel.POOR
import io.github.lilixp.utcradioclock.domain.model.DayPhase
import io.github.lilixp.utcradioclock.domain.model.HfBand
import io.github.lilixp.utcradioclock.ui.dashboard.AppTab
import io.github.lilixp.utcradioclock.ui.dashboard.BandPhasesUi
import io.github.lilixp.utcradioclock.ui.dashboard.BandUi
import io.github.lilixp.utcradioclock.ui.dashboard.DashboardScreen
import io.github.lilixp.utcradioclock.ui.dashboard.DashboardUiState
import io.github.lilixp.utcradioclock.ui.dashboard.IndexUi
import io.github.lilixp.utcradioclock.ui.dashboard.PropagationTags
import io.github.lilixp.utcradioclock.ui.dashboard.PropagationUiState
import io.github.lilixp.utcradioclock.ui.dashboard.ZoneUi
import io.github.lilixp.utcradioclock.ui.theme.UTCRadioClockTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Propagation screen's details panel on the real phone, with its font and text size (the S24+ at
 * 1.3): every box (SFI, K, A, the four groups) opens its explanation under the card, a group N0NBH has no
 * data for with its offline estimate table, and every text there is whole (no line wider than its place, no word broken in
 * two), in the phone's language, Light and Dark.
 */
@RunWith(AndroidJUnit4::class)
class PropagationPanelInstrumentedTest {

    @get:Rule
    val compose = createComposeRule()

    private val estimate8040 = listOf(HfBand.BAND_80M, HfBand.BAND_60M, HfBand.BAND_40M).map {
        BandPhasesUi(it, day = POOR, twilight = FAIR, night = FAIR)
    }

    private val propagation = PropagationUiState(
        status = PropagationState.Status.CURRENT,
        solarFlux = IndexUi("152.4", GOOD),
        kIndex = IndexUi("5", POOR),
        aIndex = IndexUi("31", POOR),
        bands = listOf(
            // N0NBH reported nothing for 80-40m: the state brings the estimate for its three bands
            BandUi(BandGroup.BANDS_80_40, now = null, day = null, night = null, estimate = estimate8040),
            BandUi(BandGroup.BANDS_30_20, now = GOOD, day = GOOD, night = GOOD),
            BandUi(BandGroup.BANDS_17_15, now = FAIR, day = FAIR, night = POOR),
            BandUi(BandGroup.BANDS_12_10, now = POOR, day = POOR, night = POOR),
        ),
        dayNightByClock = true,
        phase = DayPhase.TWILIGHT,
        updated = "05:29",
    )

    /** Without SFI: many levels are "Necunoscut", the longest word of the estimate table. */
    private val withoutSfi = propagation.copy(
        solarFlux = null,
        bands = propagation.bands.map { band ->
            band.estimate?.let { rows -> band.copy(estimate = rows.map { it.copy(day = null, night = null) }) } ?: band
        },
    )

    private fun show(dark: Boolean, propagation: PropagationUiState = this.propagation) {
        compose.setContent {
            UTCRadioClockTheme(darkTheme = dark) {
                DashboardScreen(
                    DashboardUiState(
                        utcDate = "Vineri, 2 octombrie 2026",
                        utcTime = "05:42:31",
                        localTime = "08:42:31",
                        localDate = null,
                        zone = ZoneUi(name = "Europe/Chisinau", abbreviation = "EEST", offset = "UTC+03:00"),
                        propagation = propagation,
                    ),
                    selectedTab = AppTab.PROPAGATION,
                ) {}
            }
        }
    }

    /** Every text in the panel: as wide as its place at most, and wrapped between words only. */
    private fun assertPanelTextsWhole(what: String) {
        val nodes = compose.onAllNodes(hasAnyAncestor(hasTestTag(PropagationTags.PANEL)), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .filter { SemanticsActions.GetTextLayoutResult in it.config }
        assertTrue("$what: no text in the panel", nodes.isNotEmpty())
        for (node in nodes) {
            val layouts = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            val layout = layouts.single()
            val text = layout.layoutInput.text.text
            assertFalse("$what: \"$text\" is cut at the bottom", layout.didOverflowHeight)
            if (layout.lineCount == 1) {
                val needed = layout.multiParagraph.maxIntrinsicWidth
                assertTrue("$what: \"$text\" needs $needed px, has ${node.size.width} px", needed <= node.size.width + 1)
            }
            for (line in 0 until layout.lineCount - 1) {
                val end = layout.getLineEnd(line)
                val broken = end in 1 until text.length && text[end - 1].isLetter() && text[end].isLetter()
                assertFalse("$what: a word of \"$text\" is broken at the end of line $line", broken)
            }
        }
    }

    private fun tapAndCheck(tag: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performClick()
        compose.onNodeWithTag(tag).assertIsSelected()
        compose.onNodeWithTag(PropagationTags.PANEL).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(PropagationTags.PANEL_TITLE).assertIsDisplayed()
        compose.onNode(isDialog()).assertDoesNotExist()
        assertPanelTextsWhole(tag)
    }

    private fun everyKindOfBox(dark: Boolean, propagation: PropagationUiState = this.propagation) {
        show(dark, propagation)
        compose.onNodeWithTag(PropagationTags.PANEL).performScrollTo()
        compose.onNodeWithTag(PropagationTags.PANEL_HINT).assertIsDisplayed()
        assertPanelTextsWhole("hint")
        for (tag in listOf(
            PropagationTags.SFI, PropagationTags.K, PropagationTags.A,
        ) + BandGroup.entries.map { PropagationTags.band(it) }) {
            tapAndCheck(tag)
        }
        // The group N0NBH has no data for: its estimate table on the screen, under the card, none of it cut
        tapAndCheck(PropagationTags.band(BandGroup.BANDS_80_40))
        compose.onNodeWithTag(PropagationTags.ESTIMATE_TABLE).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(PropagationTags.bandCell(HfBand.BAND_40M, DayPhase.NIGHT)).assertIsDisplayed()
    }

    @Test
    fun everyKindOfBox_explainedUnderTheCard_textsWhole_light() = everyKindOfBox(dark = false)

    @Test
    fun everyKindOfBox_explainedUnderTheCard_textsWhole_dark() = everyKindOfBox(dark = true)

    @Test
    fun withoutSfi_unknownLevelsFitTheTable() = everyKindOfBox(dark = false, propagation = withoutSfi)
}
