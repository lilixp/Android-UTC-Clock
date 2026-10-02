package io.github.lilixp.utcradioclock

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.lilixp.utcradioclock.ui.dashboard.AppTab
import io.github.lilixp.utcradioclock.ui.dashboard.DashboardScreen
import io.github.lilixp.utcradioclock.ui.dashboard.DashboardTags
import io.github.lilixp.utcradioclock.ui.dashboard.DashboardUiState
import io.github.lilixp.utcradioclock.ui.dashboard.ZoneUi
import io.github.lilixp.utcradioclock.ui.settings.SettingsTags
import io.github.lilixp.utcradioclock.ui.theme.UTCRadioClockTheme
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The same dashboard checks as the JVM tests, on a real phone or emulator
 * (`gradlew connectedDebugAndroidTest`). Texts come from the resources, so any phone language works.
 */
@RunWith(AndroidJUnit4::class)
class DashboardInstrumentedTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dash = context.getString(R.string.not_available)

    @Test
    fun dashboardShowsClocksAndPlaceholders() {
        compose.setContent {
            // The selected section kept here, as MainActivity keeps it, so the tabs can be tapped
            var tab by remember { mutableStateOf(AppTab.CLOCK) }
            UTCRadioClockTheme(darkTheme = false) {
                DashboardScreen(
                    DashboardUiState(
                        utcDate = "Miercuri, 30 septembrie 2026",
                        utcTime = "15:42:31",
                        localTime = "18:42:31",
                        localDate = null,
                        zone = ZoneUi(name = "Europe/Chisinau", abbreviation = "EEST", offset = "UTC+03:00"),
                    ),
                    selectedTab = tab,
                    onSelectTab = { tab = it },
                ) {}
            }
        }
        compose.onNodeWithTag(DashboardTags.UTC_TIME).assertTextEquals("15:42:31").assertIsDisplayed()
        compose.onNodeWithTag(DashboardTags.LOCAL_TIME).assertTextEquals("18:42:31")
        // No locator in this state: the SUN card says so instead of showing times. The LOCATION card says
        // "not available" too, so the text is looked for next to the SUN title, in the SUN card only
        compose.onNodeWithTag(AppTab.SUN.testTag).performClick()
        val inSunCard = hasAnySibling(hasText(context.getString(R.string.section_sun)))
        val noLocation = hasText(context.getString(R.string.sun_no_location)) and inSunCard
        compose.onNode(hasScrollAction()).performScrollToNode(noLocation)
        compose.onNode(noLocation).assertIsDisplayed()
        compose.onNode(hasText(context.getString(R.string.sun_enter_locator)) and inSunCard).assertIsDisplayed()
        val latitude = context.getString(R.string.latitude, dash)
        compose.onNodeWithTag(AppTab.LOCATION.testTag).performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(latitude))
        compose.onNodeWithText(latitude).assertIsDisplayed()
        val propagation = context.getString(R.string.propagation_loading) // no data in this state yet
        compose.onNodeWithTag(AppTab.PROPAGATION.testTag).performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(propagation))
        compose.onNodeWithText(propagation).assertIsDisplayed()
        // The theme choice lives in Settings now: on none of the sections
        for (section in AppTab.entries) {
            compose.onNodeWithTag(section.testTag).performClick()
            compose.onNodeWithText(context.getString(R.string.section_appearance)).assertDoesNotExist()
        }
    }
}

/** The real app on the phone: it starts and ticks, and Settings opens and closes. */
@RunWith(AndroidJUnit4::class)
class AppInstrumentedTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun utcText(): String =
        compose.onNodeWithTag(DashboardTags.UTC_TIME).fetchSemanticsNode()
            .config[SemanticsProperties.Text].joinToString()

    @Test
    fun appStartsAndClockTicks() {
        compose.onNodeWithTag(DashboardTags.UTC_TIME).assertIsDisplayed()
        val first = utcText()
        Thread.sleep(2_000)
        compose.waitForIdle()
        assertNotEquals(first, utcText())
    }

    @Test
    fun theFourSections_andBackToTheClock() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for ((section, title) in listOf(
            AppTab.SUN to R.string.section_sun,
            AppTab.LOCATION to R.string.section_location,
            AppTab.PROPAGATION to R.string.section_propagation,
        )) {
            compose.onNodeWithTag(section.testTag).performClick()
            compose.onNodeWithTag(section.testTag).assertIsSelected()
            compose.onNodeWithText(context.getString(title)).assertIsDisplayed()
            compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
            compose.onNodeWithTag(AppTab.CLOCK.testTag).assertIsSelected()
            compose.onNodeWithTag(DashboardTags.UTC_TIME).assertIsDisplayed()
        }
    }

    /**
     * On the real phone, with its own font and text size (the JVM tests use other, narrower fonts): every
     * section's label is shown whole, the width its text needs at the size drawn is within its tab.
     */
    @Test
    fun theTabLabelsFit_withThePhonesFontAndTextSize() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (section in AppTab.entries) {
            val label = context.getString(section.titleRes)
            val tabWidth = compose.onNodeWithTag(section.testTag).fetchSemanticsNode().size.width
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode()
                .config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            val needed = layouts.single().multiParagraph.maxIntrinsicWidth
            assertTrue("$label needs $needed px, its tab has $tabWidth px", needed <= tabWidth)
        }
    }

    /**
     * On the real phone, with its font and text size: the clock's texts are shown whole (the width each
     * needs is within the width it has), the UTC time and date first of all.
     */
    @Test
    fun theClockTexts_fit_withThePhonesFontAndTextSize() {
        compose.onNodeWithTag(DashboardTags.UTC_TIME).assertIsDisplayed()
        for (tag in listOf(DashboardTags.UTC_TIME, DashboardTags.DATE, DashboardTags.LOCAL_TIME, DashboardTags.LOCAL_LABEL, DashboardTags.ZONE_VALUE)) {
            val node = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            val layouts = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            // (didOverflowWidth is no help here: it says true even when the text fits exactly)
            val needed = layouts.single().multiParagraph.maxIntrinsicWidth
            assertTrue("$tag needs $needed px, has ${node.size.width} px", needed <= node.size.width + 1)
        }
    }

    @Test
    fun settingsOpenAndCloseFromTheTopBar() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithContentDescription(context.getString(R.string.open_settings)).performClick()
        compose.onNodeWithTag(SettingsTags.CALLSIGN_FIELD).assertIsDisplayed()
        compose.onNodeWithTag(SettingsTags.LOCATOR_FIELD).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.section_appearance)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.back)).performClick()
        compose.onNodeWithTag(DashboardTags.UTC_TIME).assertIsDisplayed()
    }
}
