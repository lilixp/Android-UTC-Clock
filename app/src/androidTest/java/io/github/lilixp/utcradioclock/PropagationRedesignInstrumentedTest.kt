package io.github.lilixp.utcradioclock

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.lilixp.utcradioclock.data.propagation.PropagationState
import io.github.lilixp.utcradioclock.domain.model.BandGroup
import io.github.lilixp.utcradioclock.domain.model.ConditionLevel.*
import io.github.lilixp.utcradioclock.ui.dashboard.*
import io.github.lilixp.utcradioclock.ui.theme.UTCRadioClockTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real phone rendering with deterministic data at 320 dp. Font scale is local to these tests. */
@RunWith(AndroidJUnit4::class)
class PropagationRedesignInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val sample = PropagationUiState(
        status = PropagationState.Status.CURRENT,
        solarFlux = IndexUi("92", FAIR), kIndex = IndexUi("3", GOOD), aIndex = IndexUi("36", POOR),
        bands = BandGroup.entries.zip(listOf(POOR, GOOD, FAIR, POOR)).map { (group, level) -> BandUi(group, level, level, level) },
        updated = "14:34",
    )

    private fun verify(dark: Boolean, scale: Float) {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, scale)) {
                UTCRadioClockTheme(dark) {
                    Column(Modifier.width(320.dp).fillMaxHeight().background(MaterialTheme.colorScheme.background)
                        .verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PropagationCard(sample)
                    }
                }
            }
        }
        checkText()
        val name = "${if (dark) "dark" else "light"}-${scale}"
        screenshot("$name-summary")
        for (tag in listOf(PropagationTags.SFI, PropagationTags.K, PropagationTags.A) + BandGroup.entries.map(PropagationTags::band)) {
            compose.onNodeWithTag(tag).performScrollTo().performClick().assertIsSelected()
            compose.onNodeWithTag(PropagationTags.PANEL_TITLE).performScrollTo().assertIsDisplayed()
            checkText()
        }
        compose.onNodeWithTag(PropagationTags.band(BandGroup.BANDS_30_20)).performScrollTo().performClick()
        compose.onNodeWithTag(PropagationTags.BAND_TABLE).performScrollTo().assertIsDisplayed()
        screenshot("$name-table")
    }

    private fun checkText() {
        val nodes = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue(nodes.isNotEmpty())
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
                if (layout.lineCount == 1) assertTrue("intrinsic width: $text", layout.multiParagraph.maxIntrinsicWidth <= node.size.width + 1)
                for (line in 0 until layout.lineCount - 1) {
                    val end = layout.getLineEnd(line)
                    assertFalse("word broken: $text", end in 1 until text.length && text[end - 1].isLetter() && text[end].isLetter())
                }
            }
        }
    }

    private fun screenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "propagation-review").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun light13() = verify(false, 1.3f)
    @Test fun dark13() = verify(true, 1.3f)
    @Test fun light20() = verify(false, 2f)
    @Test fun dark20() = verify(true, 2f)
}

/** Full MainActivity, live data and the phone's unchanged display/font settings, including both bars. */
@RunWith(AndroidJUnit4::class)
class PropagationCompactPhoneTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun verify(dark: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val settings = (context.applicationContext as RadioClockApplication).container.settingsRepository
        val originalTheme = settings.themeMode.value
        val originalFont = context.resources.configuration.fontScale
        try {
            compose.onNodeWithContentDescription(context.getString(R.string.open_settings)).performClick()
            compose.onNodeWithText(context.getString(if (dark) R.string.theme_dark else R.string.theme_light))
                .performScrollTo().performClick()
            compose.onNodeWithContentDescription(context.getString(R.string.back)).performClick()
            compose.onNodeWithTag(AppTab.PROPAGATION.testTag).performClick()
            compose.waitUntil(timeoutMillis = 30_000) {
                compose.onAllNodesWithTag(PropagationTags.SFI).fetchSemanticsNodes().isNotEmpty()
            }
            val theme = if (dark) "dark" else "light"
            assertCompactRows()
            screenshot("compact-$theme-none")
            assertScreenFits()
            var expectedColumns: List<androidx.compose.ui.geometry.Rect>? = null
            for ((group, name) in BandGroup.entries.zip(listOf("80-40", "30-20", "17-15", "12-10"))) {
                compose.onNodeWithTag(PropagationTags.band(group)).performClick().assertIsSelected()
                val columns = listOf(io.github.lilixp.utcradioclock.domain.model.DayPhase.DAY,
                    io.github.lilixp.utcradioclock.domain.model.DayPhase.NIGHT).map {
                    compose.onNodeWithTag(PropagationTags.phaseColumn(it)).fetchSemanticsNode().boundsInRoot
                }
                assertEquals("Day/night widths must match", columns[0].width, columns[1].width, 0f)
                expectedColumns?.forEachIndexed { index, previous ->
                    assertEquals("Column left must stay fixed", previous.left, columns[index].left, 0f)
                    assertEquals("Column right must stay fixed", previous.right, columns[index].right, 0f)
                }
                expectedColumns = columns
                assertScreenFits()
                screenshot("compact-$theme-$name")
            }
            for ((tag, name) in listOf(PropagationTags.SFI to "sfi", PropagationTags.K to "k", PropagationTags.A to "a")) {
                compose.onNodeWithTag(tag).performClick().assertIsSelected()
                compose.onNodeWithTag(PropagationTags.REFERENCE_VALUES).assertIsDisplayed()
                assertScreenFits()
                screenshot("compact-$theme-$name")
            }
            assertEquals("Phone font must stay unchanged", originalFont, context.resources.configuration.fontScale, 0f)
        } finally {
            // Only the app theme is temporarily selected; restore the user's choice after either outcome.
            instrumentation.runOnMainSync { settings.setThemeMode(originalTheme) }
        }
    }

    private fun assertCompactRows() {
        val bandHeight = compose.onNodeWithTag(PropagationTags.band(BandGroup.BANDS_80_40))
            .fetchSemanticsNode().size.height
        for (tag in listOf(PropagationTags.SFI, PropagationTags.K, PropagationTags.A)) {
            assertEquals("Index and band button heights must match", bandHeight,
                compose.onNodeWithTag(tag).fetchSemanticsNode().size.height)
        }
        for (tags in listOf(listOf(PropagationTags.SFI, PropagationTags.K, PropagationTags.A),
            BandGroup.entries.map(PropagationTags::band))) {
            val boxes = tags.map { compose.onNodeWithTag(it).getUnclippedBoundsInRoot() }
            assertTrue("All boxes must share one row", boxes.all { it.top == boxes.first().top })
            assertTrue("Touch height must be at least 48 dp", boxes.all { (it.bottom - it.top).value >= 48f })
            assertTrue("Touch width must be at least 48 dp", boxes.all { (it.right - it.left).value >= 48f })
        }
    }

    private fun assertScreenFits() {
        compose.onNodeWithTag(PropagationTags.SUMMARY).assertIsDisplayed()
        compose.onNodeWithTag(DashboardTags.CALLSIGN).assertIsDisplayed()
        compose.onNodeWithTag(DashboardTags.LOCATOR).assertIsDisplayed()
        val panel = compose.onNodeWithTag(PropagationTags.PANEL).getUnclippedBoundsInRoot()
        val navigation = compose.onNodeWithTag(AppTab.CLOCK.testTag).getUnclippedBoundsInRoot()
        assertTrue("Complete panel must fit above the bottom navigation: $panel vs $navigation", panel.bottom <= navigation.top)
        val scroll = compose.onNode(hasScrollAction()).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertEquals("No scrolling needed", 0f, scroll.value(), 0f)
        assertEquals("Whole propagation content must fit", 0f, scroll.maxValue(), 0f)
        for (node in compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
            useUnmergedTree = true).fetchSemanticsNodes()) {
            val layouts = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            for (layout in layouts) {
                assertFalse("Text height overflow: ${layout.layoutInput.text}", layout.didOverflowHeight)
                for (line in 0 until layout.lineCount) {
                    assertTrue("Text width overflow: ${layout.layoutInput.text}",
                        layout.getLineRight(line) - layout.getLineLeft(line) <= node.size.width + 1)
                    assertFalse("Text must not be ellipsized", layout.isLineEllipsized(line))
                }
            }
        }
    }

    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        // Let the device compositor finish the selection ripple and redraw both fixed bars.
        android.os.SystemClock.sleep(300)
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "propagation-review").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun lightActualPhone() = verify(false)
    @Test fun darkActualPhone() = verify(true)
}
