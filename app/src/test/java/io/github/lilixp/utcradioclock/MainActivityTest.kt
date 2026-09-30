package io.github.lilixp.utcradioclock

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertTextEquals
import androidx.lifecycle.ViewModelProvider
import io.github.lilixp.utcradioclock.testing.FixedTimeApplication
import io.github.lilixp.utcradioclock.ui.dashboard.DashboardTags
import io.github.lilixp.utcradioclock.ui.dashboard.DashboardViewModel
import io.github.lilixp.utcradioclock.ui.settings.SettingsTags
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The real app on the JVM (Robolectric): real repositories and SharedPreferences, both screens,
 * the navigation between them and the theme of the whole app. Phone language: Romanian. The clock is
 * stopped at 30 September 2026, 15:42:31 UTC in Chișinău ([FixedTimeApplication]), not the PC's time.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE) // real drawing, so the colors on screen can be checked
@Config(qualifiers = "ro", application = FixedTimeApplication::class)
class MainActivityTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun openSettings() {
        compose.onNodeWithContentDescription("Setări").performClick()
        compose.onNodeWithTag(SettingsTags.CALLSIGN_FIELD).assertIsDisplayed()
    }

    private fun onDashboard() = compose.onNodeWithTag(DashboardTags.UTC_TIME).assertIsDisplayed()

    /** The color at the top-left corner of the screen (the top app bar, drawn in the theme's surface color). */
    private fun cornerColor(): Color = compose.onRoot().captureToImage().toPixelMap()[4, 4]

    private fun dashboardViewModel(): DashboardViewModel =
        ViewModelProvider(compose.activity)[DashboardViewModel::class.java] // the one the screen created

    @Test
    fun dashboardShowsUtcAndLocalTimeOfTheSameInstant() {
        compose.onNodeWithTag(DashboardTags.UTC_TIME).assertTextEquals("15:42:31")
        compose.onNodeWithTag(DashboardTags.LOCAL_TIME).assertTextEquals("18:42:31")
        compose.onNodeWithTag(DashboardTags.DATE).assertTextEquals("30 septembrie 2026")
        compose.onNodeWithText("Europe/Chisinau · UTC+03:00").assertIsDisplayed()
    }

    @Test
    fun recreatingTheActivityKeepsTheSameClock() {
        onDashboard()
        val before = dashboardViewModel()
        compose.activityRule.scenario.recreate() // like a rotation
        onDashboard()
        // The ViewModel (and the one ticker it shares) survives: no second clock is started
        assertSame(before, dashboardViewModel())
        compose.onNodeWithTag(DashboardTags.UTC_TIME).assertTextEquals("15:42:31")
    }

    @Test
    fun sunCardFollowsTheLocatorEnteredInSettings() {
        val list = compose.onNode(hasScrollAction())
        list.performScrollToNode(hasText("Locația nu este disponibilă."))
        compose.onNodeWithText("Locația nu este disponibilă.").assertIsDisplayed() // no locator yet

        openSettings()
        compose.onNodeWithTag(SettingsTags.LOCATOR_FIELD).performTextInput("kn46dw")
        compose.onNodeWithContentDescription("Înapoi").performClick()

        // 30 September 2026, KN46dw, the phone's zone (Chișinău, UTC+3)
        for (line in listOf("Răsărit: 07:04", "Apus: 18:49", "Amiază solară: 12:57", "Durata zilei: 11h 45m")) {
            compose.onNode(hasScrollAction()).performScrollToNode(hasText(line))
            compose.onNodeWithText(line).assertIsDisplayed()
        }
        compose.onNodeWithText("Locația nu este disponibilă.").assertDoesNotExist()
    }

    @Test
    fun startsOnTheDashboardWithDefaults() {
        onDashboard()
        compose.onNodeWithTag(DashboardTags.CALLSIGN).assertIsDisplayed()
        compose.onNodeWithTag(DashboardTags.LOCATOR).assertDoesNotExist() // no locator until entered
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("PROPAGARE"))
        compose.onNodeWithText("ASPECT").assertDoesNotExist()
    }

    @Test
    fun backArrowReturnsToTheDashboard() {
        openSettings()
        compose.onNodeWithContentDescription("Înapoi").performClick()
        onDashboard()
    }

    @Test
    fun phoneBackButtonReturnsToTheDashboard() {
        openSettings()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        onDashboard()
        compose.onNodeWithTag(SettingsTags.CALLSIGN_FIELD).assertDoesNotExist()
    }

    @Test
    fun darkThemeIsAppliedAndKeptWhenTheScreenIsRecreated() {
        assertTrue(cornerColor().luminance() > 0.8f) // light by default (the test "phone" is not in dark mode)

        openSettings()
        compose.onNodeWithText("Întunecat").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(cornerColor().luminance() < 0.1f)

        // Like a rotation: the activity is rebuilt and reads the saved choice again
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Întunecat").performScrollTo().assertIsSelected() // still on Settings
        assertTrue(cornerColor().luminance() < 0.1f)

        compose.onNodeWithContentDescription("Înapoi").performClick()
        onDashboard()
        assertTrue(cornerColor().luminance() < 0.1f) // the dashboard is dark too
    }

    @Test
    fun lightThemeAfterDark() {
        openSettings()
        compose.onNodeWithText("Întunecat").performScrollTo().performClick()
        compose.onNodeWithText("Luminos").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(cornerColor().luminance() > 0.8f)
    }
}
