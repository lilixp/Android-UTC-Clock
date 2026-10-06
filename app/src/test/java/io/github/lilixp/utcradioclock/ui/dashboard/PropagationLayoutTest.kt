package io.github.lilixp.utcradioclock.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.lilixp.utcradioclock.data.propagation.PropagationState
import io.github.lilixp.utcradioclock.domain.model.BandGroup
import io.github.lilixp.utcradioclock.domain.model.ConditionLevel.*
import io.github.lilixp.utcradioclock.ui.theme.UTCRadioClockTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ro-w320dp-h800dp")
class PropagationLayoutTest {
    @get:Rule val compose = createComposeRule()
    private var pixelsPerDp = 1f

    private val sample = PropagationUiState(
        status = PropagationState.Status.CURRENT,
        solarFlux = IndexUi("152.4", GOOD), kIndex = IndexUi("3", GOOD), aIndex = IndexUi("36", POOR),
        bands = BandGroup.entries.zip(listOf(POOR, GOOD, FAIR, POOR)).map { (group, level) ->
            BandUi(group, level, level, if (level == GOOD) null else GOOD)
        }, updated = "14:34",
    )

    private fun checkLayout(scale: Float, dark: Boolean) {
        var input by mutableStateOf(sample)
        compose.setContent {
            val density = LocalDensity.current.density
            pixelsPerDp = density
            CompositionLocalProvider(LocalDensity provides Density(density, scale)) {
                UTCRadioClockTheme(dark) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                        PropagationCard(input)
                    }
                }
            }
        }
        assertTextFits()
        for (tag in listOf(PropagationTags.SFI, PropagationTags.K, PropagationTags.A) + BandGroup.entries.map(PropagationTags::band)) {
            val node = compose.onNodeWithTag(tag)
            node.performScrollTo().performClick().assertIsSelected()
            val size = node.fetchSemanticsNode().size
            assertTrue("touch height $tag", size.height >= 48 * pixelsPerDp)
            compose.onNodeWithTag(PropagationTags.PANEL_TITLE).performScrollTo().assertIsDisplayed()
            assertTextFits()
        }
        for (variant in listOf(
            sample.copy(status = PropagationState.Status.STALE),
            sample.copy(bands = emptyList()),
            sample.copy(status = PropagationState.Status.LOADING),
            sample.copy(status = PropagationState.Status.UNAVAILABLE),
            sample.copy(bands = sample.bands.map { it.copy(now = GOOD) }),
            sample.copy(bands = sample.bands.map { it.copy(now = POOR) }),
        )) {
            compose.runOnIdle { input = variant }
            compose.onNodeWithTag(PropagationTags.SUMMARY_TITLE).performScrollTo().assertIsDisplayed()
            assertTextFits()
        }
    }

    private fun assertTextFits() {
        val nodes = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes()
        for (node in nodes) {
            val layouts = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            for (layout in layouts) {
                val text = layout.layoutInput.text.text
                assertFalse("height: $text", layout.didOverflowHeight)
                for (line in 0 until layout.lineCount) {
                    val width = layout.getLineRight(line) - layout.getLineLeft(line)
                    assertTrue("line width: $text ($width > ${node.size.width})", width <= node.size.width + 1)
                }
                if (layout.lineCount == 1) {
                    assertTrue("intrinsic width: $text", layout.multiParagraph.maxIntrinsicWidth <= node.size.width + 1)
                }
                for (line in 0 until layout.lineCount - 1) {
                    val end = layout.getLineEnd(line)
                    assertFalse("word broken: $text", end in 1 until text.length && text[end - 1].isLetter() && text[end].isLetter())
                }
            }
        }
    }

    /** S24+: 1440x3120 / 3.75, reserving 24 dp status + 48 dp system navigation.
     * Uses the complete unchanged DashboardScreen, including its header and application navigation.
     */
    private fun checkS24(dark: Boolean) {
        var input by mutableStateOf(sample.copy(
            solarFlux = IndexUi("100", FAIR), kIndex = IndexUi("3", GOOD), aIndex = IndexUi("24", FAIR),
        ))
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.3f)) {
                UTCRadioClockTheme(dark) {
                    Box(Modifier.requiredSize(384.dp, 760.dp)) {
                        DashboardScreen(DashboardUiState(
                            utcDate = "6 octombrie 2026", utcTime = "08:00:00", localTime = "11:00:00", localDate = null,
                            zone = ZoneUi("Europe/Chisinau", "EEST", "UTC+03:00"), callsign = "ER1PL", locator = "KN46dw",
                            propagation = input,
                        ), selectedTab = AppTab.PROPAGATION) {}
                    }
                }
            }
        }
        val bandHeight = compose.onNodeWithTag(PropagationTags.band(BandGroup.BANDS_80_40)).fetchSemanticsNode().size.height
        val bandWidths = BandGroup.entries.map { compose.onNodeWithTag(PropagationTags.band(it)).fetchSemanticsNode().size.width }
        for (tag in listOf(PropagationTags.SFI, PropagationTags.K, PropagationTags.A)) {
            compose.onNodeWithTag(tag).assertIsDisplayed().performClick().assertIsSelected()
            assertEquals("Index height must equal the unchanged band height", bandHeight,
                compose.onNodeWithTag(tag).fetchSemanticsNode().size.height)
            val panel = compose.onNodeWithTag(PropagationTags.PANEL).getUnclippedBoundsInRoot()
            val navigation = compose.onNodeWithTag(AppTab.CLOCK.testTag).getUnclippedBoundsInRoot()
            assertTrue("$tag: whole panel $panel must fit above navigation $navigation", panel.bottom <= navigation.top)
            val reference = compose.onNodeWithTag(PropagationTags.REFERENCE_VALUES).getUnclippedBoundsInRoot()
            assertTrue("$tag: complete threshold section must be inside the panel", reference.bottom <= panel.bottom)
            for (part in listOf(PropagationTags.SUMMARY_TITLE, PropagationTags.PANEL_VALUE, PropagationTags.PANEL_TITLE,
                PropagationTags.PANEL_SUBTITLE, PropagationTags.REFERENCE_VALUES)) compose.onNodeWithTag(part).assertIsDisplayed()
            val scroll = compose.onNode(hasScrollAction()).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
            assertEquals("$tag: no scrolling", 0f, scroll.value(), 0f)
            assertEquals("$tag: no content below the viewport", 0f, scroll.maxValue(), 0f)
            val referenceTexts = compose.onAllNodes(hasAnyAncestor(hasTestTag(PropagationTags.REFERENCE_VALUES)) and
                SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true).fetchSemanticsNodes()
            assertEquals("Reference title, all three thresholds and all three explanations must be present", 7, referenceTexts.size)
            println("S24 dark=$dark $tag: button=$bandHeight px, panel bottom=${panel.bottom}, navigation top=${navigation.top}, scroll=${scroll.maxValue()}")
            assertTextFits()
        }
        var expectedColumns: List<Pair<Float, Float>>? = null
        // Changing the selected group, level words, missing values and offline rows must never move columns.
        for (levels in listOf(listOf(POOR, GOOD, FAIR, POOR), listOf(GOOD, GOOD, GOOD, GOOD), listOf(null, null, null, null))) {
            compose.runOnIdle { input = input.copy(bands = input.bands.mapIndexed { i, band ->
                band.copy(day = levels[i], night = levels[3 - i], estimate = if (levels[i] == null)
                    band.group.bands.map { BandPhasesUi(it, GOOD, FAIR, POOR) } else null)
            }) }
            for (group in BandGroup.entries) {
                compose.onNodeWithTag(PropagationTags.band(group)).performClick().assertIsSelected()
                val day = compose.onNodeWithTag(PropagationTags.phaseColumn(io.github.lilixp.utcradioclock.domain.model.DayPhase.DAY))
                val night = compose.onNodeWithTag(PropagationTags.phaseColumn(io.github.lilixp.utcradioclock.domain.model.DayPhase.NIGHT))
                assertEquals("Day/night must have exactly equal pixel widths", day.fetchSemanticsNode().size.width,
                    night.fetchSemanticsNode().size.width)
                if (expectedColumns == null) println("S24 dark=$dark: each phase column=${day.fetchSemanticsNode().size.width} px")
                val columns = listOf(day, night).map { it.fetchSemanticsNode().boundsInRoot.let { r -> r.left to r.right } }
                if (expectedColumns == null) expectedColumns = columns else assertEquals("Stable positions for $group", expectedColumns, columns)
                assertTextFits()
            }
        }
        assertEquals("Band buttons must retain their widths", bandWidths,
            BandGroup.entries.map { compose.onNodeWithTag(PropagationTags.band(it)).fetchSemanticsNode().size.width })
        assertEquals("Band buttons must retain their height", bandHeight,
            compose.onNodeWithTag(PropagationTags.band(BandGroup.BANDS_80_40)).fetchSemanticsNode().size.height)
    }

    @Test @Config(qualifiers = "ro-w384dp-h832dp-600dpi") fun s24Light13() = checkS24(false)
    @Test @Config(qualifiers = "ro-w384dp-h832dp-600dpi") fun s24Dark13() = checkS24(true)

    @Test fun romanianLight13() = checkLayout(1.3f, false)
    @Test fun romanianDark13() = checkLayout(1.3f, true)
    @Test fun romanianLight20() = checkLayout(2f, false)
    @Test fun romanianDark20() = checkLayout(2f, true)
    @Test @Config(qualifiers = "en-w320dp-h800dp") fun englishLight13() = checkLayout(1.3f, false)
    @Test @Config(qualifiers = "en-w320dp-h800dp") fun englishDark13() = checkLayout(1.3f, true)
    @Test @Config(qualifiers = "en-w320dp-h800dp") fun englishLight20() = checkLayout(2f, false)
    @Test @Config(qualifiers = "en-w320dp-h800dp") fun englishDark20() = checkLayout(2f, true)
}
