package io.github.lilixp.utcradioclock

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.lilixp.utcradioclock.ui.dashboard.DashboardScreen
import io.github.lilixp.utcradioclock.ui.dashboard.DashboardTags
import io.github.lilixp.utcradioclock.ui.dashboard.DashboardUiState
import io.github.lilixp.utcradioclock.ui.settings.SettingsTags
import io.github.lilixp.utcradioclock.ui.theme.UTCRadioClockTheme
import org.junit.Assert.assertNotEquals
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
            UTCRadioClockTheme(darkTheme = false) {
                DashboardScreen(
                    DashboardUiState(
                        utcDate = "30 septembrie 2026",
                        utcTime = "15:42:31",
                        localTime = "18:42:31",
                        localDate = null,
                        timeZone = "Europe/Chisinau · UTC+03:00",
                    ),
                ) {}
            }
        }
        compose.onNodeWithTag(DashboardTags.UTC_TIME).assertTextEquals("15:42:31").assertIsDisplayed()
        compose.onNodeWithTag(DashboardTags.LOCAL_TIME).assertTextEquals("18:42:31")
        val sunrise = context.getString(R.string.sunrise, dash)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(sunrise))
        compose.onNodeWithText(sunrise).assertIsDisplayed()
        val propagation = context.getString(R.string.propagation_later)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(propagation))
        compose.onNodeWithText(propagation).assertIsDisplayed()
    }
}

/** The real app starts and ticks: the UTC time is on screen and changes within two seconds. */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

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
    fun settingsOpenAndCloseFromTheTopBar() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithContentDescription(context.getString(R.string.open_settings)).performClick()
        compose.onNodeWithTag(SettingsTags.CALLSIGN_FIELD).assertIsDisplayed()
        compose.onNodeWithTag(SettingsTags.LOCATOR_FIELD).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.back)).performClick()
        compose.onNodeWithTag(DashboardTags.UTC_TIME).assertIsDisplayed()
    }
}
