package io.github.lilixp.utcradioclock

import android.Manifest
import android.content.pm.PackageManager
import android.provider.Settings
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
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import io.github.lilixp.utcradioclock.domain.model.HfBand
import io.github.lilixp.utcradioclock.domain.model.PositionSource
import io.github.lilixp.utcradioclock.testing.BOGHICENI
import io.github.lilixp.utcradioclock.testing.FixedTimeApplication
import io.github.lilixp.utcradioclock.testing.GpsFixedTimeApplication
import io.github.lilixp.utcradioclock.testing.OfflineFixedTimeApplication
import io.github.lilixp.utcradioclock.testing.fix
import io.github.lilixp.utcradioclock.ui.dashboard.AppTab
import io.github.lilixp.utcradioclock.ui.dashboard.DashboardTags
import io.github.lilixp.utcradioclock.ui.dashboard.DashboardViewModel
import io.github.lilixp.utcradioclock.ui.dashboard.PropagationTags
import io.github.lilixp.utcradioclock.ui.settings.SettingsTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
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

    /** Taps a section in the bottom bar. */
    private fun openTab(tab: AppTab) {
        compose.onNodeWithTag(tab.testTag).performClick()
        compose.waitForIdle()
    }

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
        openTab(AppTab.SUN)
        val list = compose.onNode(hasScrollAction())
        list.performScrollToNode(hasText("Locația nu este disponibilă."))
        compose.onNodeWithText("Locația nu este disponibilă.").assertIsDisplayed() // no locator yet

        openSettings()
        compose.onNodeWithTag(SettingsTags.LOCATOR_FIELD).performTextInput("kn46dw")
        compose.onNodeWithContentDescription("Înapoi").performClick()
        compose.onNodeWithTag(AppTab.SUN.testTag).assertIsSelected() // back on the section Settings was opened from

        // 30 September 2026, KN46dw, the phone's zone (Chișinău, UTC+3)
        for (line in listOf("Răsărit: 07:04", "Apus: 18:49", "Amiază solară: 12:57", "Durata zilei: 11h 45m")) {
            compose.onNode(hasScrollAction()).performScrollToNode(hasText(line))
            compose.onNodeWithText(line).assertIsDisplayed()
        }
        compose.onNodeWithText("Locația nu este disponibilă.").assertDoesNotExist()
    }

    /**
     * Opens the PROPAGATION section and waits for [text], which arrives from another thread (the fake
     * Internet).
     */
    private fun waitForText(text: String) {
        openTab(AppTab.PROPAGATION)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("PROPAGARE"))
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }

    @Test
    fun propagationCardShowsTheN0nbhData() {
        waitForText("SFI 93") // the real feed of 1 October 2026, served by the fake Internet
        compose.onNodeWithText("K 0").assertIsDisplayed()
        compose.onNodeWithText("A 3").assertIsDisplayed()
        val updated = "Actualizat 1 oct. 05:29 UTC" // the feed's "updated" (UTC), another UTC day than now
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(updated))
        compose.onNodeWithText(updated).assertIsDisplayed()

        // No locator: 18:42 local is night by the clock; the feed has the same values day and night
        compose.onNodeWithContentDescription("17-15m: Mediu").performClick()
        compose.onNodeWithText("Ziua: Mediu • Noaptea: Mediu").assertIsDisplayed()
        compose.onNodeWithText("Condiții calculate de N0NBH (hamqsl.com).").assertIsDisplayed()
        compose.onNodeWithText("OK").performClick()
    }

    @Test
    fun offlineEstimate_fromTheRealN0nbhFeed_unknownUntilThereIsAPosition() {
        waitForText("SFI 93")
        val list = compose.onNode(hasScrollAction())
        list.performScrollToNode(hasTestTag(PropagationTags.estimate(HfBand.BAND_10M)))
        compose.onNodeWithText("Estimare offline · 10 benzi").assertIsDisplayed()
        compose.onNodeWithContentDescription("20m: Necunoscut").assertIsDisplayed() // no locator yet

        openSettings()
        compose.onNodeWithTag(SettingsTags.LOCATOR_FIELD).performTextInput("kn46dw")
        compose.onNodeWithContentDescription("Înapoi").performClick()

        // 18:42 local, before sunset: day; the feed's SFI 93 and K 0
        compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag(PropagationTags.estimate(HfBand.BAND_10M)))
        compose.onNodeWithContentDescription("20m: Bun").assertIsDisplayed()
        compose.onNodeWithContentDescription("160m: Slab").assertIsDisplayed()
        compose.onNodeWithContentDescription("10m: Slab").assertIsDisplayed() // SFI 93 is below 95
        compose.onNodeWithContentDescription("20m: Bun").performClick()
        compose.onNodeWithText("Estimare: Bun").assertIsDisplayed()
        compose.onNodeWithText("OK").performClick()
    }

    @Test
    @Config(qualifiers = "ro", application = OfflineFixedTimeApplication::class)
    fun noInternetAndNothingSaved_dataUnavailable() {
        waitForText("Date indisponibile")
        compose.onNodeWithText("SFI", substring = true).assertDoesNotExist()
        onDashboardStillWorks()
    }

    private fun onDashboardStillWorks() {
        openTab(AppTab.CLOCK)
        compose.onNodeWithTag(DashboardTags.UTC_TIME).assertTextEquals("15:42:31")
    }

    // ---- Position from GPS ----

    private fun app() = compose.activity.application as FixedTimeApplication

    private fun chooseAutomaticPosition() {
        openSettings()
        chooseAutomaticPositionFromSettings()
    }

    private fun chooseAutomaticPositionFromSettings() {
        compose.onNodeWithText("Automat (GPS)").performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun backToDashboard() {
        compose.onNodeWithContentDescription("Înapoi").performClick()
        onDashboard()
    }

    private fun assertOnLocationCard(text: String) {
        openTab(AppTab.LOCATION)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text))
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    /** What Android's permission dialog answers, delivered as the system would. */
    @Suppress("DEPRECATION") // the way the system hands the answer to the activity
    private fun answerPermissionDialog(granted: Boolean) {
        val request = shadowOf(compose.activity).lastRequestedPermission
        val results = IntArray(request.requestedPermissions.size) {
            if (granted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
        }
        compose.runOnUiThread {
            compose.activity.onRequestPermissionsResult(request.requestCode, request.requestedPermissions, results)
        }
        compose.waitForIdle()
    }

    @Test
    fun manualByDefault_thePermissionIsNeverAskedAtStart() {
        onDashboard()
        assertNull(shadowOf(compose.activity).lastRequestedPermission)
        assertEquals(0, app().locations.requests)
    }

    @Test
    @Config(qualifiers = "ro", application = GpsFixedTimeApplication::class)
    fun automatic_theGpsPositionOnTheDashboard() {
        chooseAutomaticPosition()
        assertNull(shadowOf(compose.activity).lastRequestedPermission) // already allowed: no dialog
        backToDashboard()

        compose.onNodeWithTag(DashboardTags.LOCATOR).assertTextEquals("KN46dx") // the station badge
        assertOnLocationCard("Latitudine: 46,9612° N")
        assertOnLocationCard("Longitudine: 28,3041° E")
        assertOnLocationCard("QTH: KN46dx")
        assertOnLocationCard("GPS · 18:42 · ±12 m")
    }

    @Test
    fun automatic_asksForBothPermissions_whenChosen() {
        chooseAutomaticPosition()
        val asked = shadowOf(compose.activity).lastRequestedPermission.requestedPermissions.toSet()
        assertEquals(setOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), asked)
    }

    @Test
    fun permissionAllowedInTheDialog_thePositionAppears() {
        chooseAutomaticPosition()
        app().locations.permission = true
        app().locations.current = fix(BOGHICENI)
        answerPermissionDialog(granted = true)
        backToDashboard()
        assertOnLocationCard("QTH: KN46dx")
    }

    @Test
    fun permissionRefused_theLocatorIsUsed_andTheCardOffersToAskAgain() {
        openSettings()
        compose.onNodeWithTag(SettingsTags.LOCATOR_FIELD).performTextInput("kn46dw")
        chooseAutomaticPositionFromSettings()
        answerPermissionDialog(granted = false)
        backToDashboard()

        assertOnLocationCard("Locator din Setări (centrul pătratului)")
        assertOnLocationCard("QTH: KN46dw")
        // Robolectric's Android says the dialog may not be shown again: "Don't ask again"
        assertOnLocationCard("Accesul la locație a fost refuzat. Îl poți permite din setările aplicației.")
        compose.onNodeWithText("Deschide setările aplicației").performClick()
        val opened = shadowOf(compose.activity).nextStartedActivity
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, opened.action)
        assertEquals("package:io.github.lilixp.utcradioclock", opened.dataString)
    }

    @Test
    fun notAskedYet_theCardButtonShowsTheDialog() {
        app().settings().setPositionSource(PositionSource.AUTOMATIC) // chosen earlier, permission revoked since
        compose.activityRule.scenario.recreate()
        onDashboard()
        assertOnLocationCard("Aplicația nu are acces la locația telefonului.")
        compose.onNodeWithText("Permite accesul la locație").performClick()
        assertTrue(shadowOf(compose.activity).lastRequestedPermission != null)
    }

    @Test
    @Config(qualifiers = "ro", application = GpsFixedTimeApplication::class)
    fun locationTurnedOff_theCardOpensTheLocationSettings() {
        app().locations.enabled = false
        chooseAutomaticPosition()
        backToDashboard()
        assertOnLocationCard("Locația telefonului este oprită.")
        compose.onNodeWithText("Pornește locația").performClick()
        assertEquals(Settings.ACTION_LOCATION_SOURCE_SETTINGS, shadowOf(compose.activity).nextStartedActivity.action)
    }

    private fun FixedTimeApplication.settings() = container.settingsRepository

    // ---- The four sections ----

    @Test
    fun back_fromAnotherSectionToTheClock_fromTheClockOut() {
        for (tab in listOf(AppTab.SUN, AppTab.LOCATION, AppTab.PROPAGATION)) {
            openTab(tab)
            compose.onNodeWithTag(tab.testTag).assertIsSelected()
            compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
            compose.waitForIdle()
            compose.onNodeWithTag(AppTab.CLOCK.testTag).assertIsSelected()
            onDashboard()
            assertFalse(compose.activity.isFinishing)
        }
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        assertTrue(compose.activity.isFinishing) // from the Clock, Back leaves the app
    }

    @Test
    fun theSectionSurvivesARotation() {
        openTab(AppTab.PROPAGATION)
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag(AppTab.PROPAGATION.testTag).assertIsSelected()
        compose.onNodeWithText("PROPAGARE").assertIsDisplayed()
    }

    @Test
    fun settingsGoesBackToTheSectionItWasOpenedFrom() {
        openTab(AppTab.LOCATION)
        openSettings()
        compose.onNodeWithContentDescription("Înapoi").performClick() // the arrow
        compose.onNodeWithTag(AppTab.LOCATION.testTag).assertIsSelected()
        compose.onNodeWithText("LOCAȚIE").assertIsDisplayed()

        openTab(AppTab.PROPAGATION)
        openSettings()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() } // the phone's button
        compose.waitForIdle()
        compose.onNodeWithTag(AppTab.PROPAGATION.testTag).assertIsSelected()
        compose.onNodeWithText("PROPAGARE").assertIsDisplayed()
    }

    @Test
    fun settingsAndRotation_stillBackToTheSameSection() {
        openTab(AppTab.SUN)
        openSettings()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag(SettingsTags.CALLSIGN_FIELD).assertIsDisplayed() // still in Settings
        compose.onNodeWithContentDescription("Înapoi").performClick()
        compose.onNodeWithTag(AppTab.SUN.testTag).assertIsSelected()
    }

    @Test
    fun switchingSections_oneViewModel_oneN0nbhDownload() {
        waitForText("SFI 93")
        val viewModel = dashboardViewModel()
        val downloads = app().httpRequests.get()
        assertEquals(1, downloads)
        repeat(3) {
            for (tab in AppTab.entries) openTab(tab)
        }
        assertSame(viewModel, dashboardViewModel()) // the same clock, GPS and N0NBH behind every section
        assertEquals(downloads, app().httpRequests.get()) // no new download because of the sections
        openTab(AppTab.PROPAGATION)
        compose.onNodeWithText("SFI 93").assertIsDisplayed() // its data still there, not downloaded again
    }

    @Test
    @Config(qualifiers = "ro", application = GpsFixedTimeApplication::class)
    fun switchingSections_oneGpsReading() {
        chooseAutomaticPosition()
        backToDashboard()
        assertOnLocationCard("QTH: KN46dx")
        val readings = app().locations.requests
        assertEquals(1, readings)
        repeat(3) {
            for (tab in AppTab.entries) openTab(tab)
        }
        assertEquals(readings, app().locations.requests) // the sections do not ask the phone again
    }

    @Test
    fun startsOnTheDashboardWithDefaults() {
        onDashboard()
        compose.onNodeWithTag(DashboardTags.CALLSIGN).assertIsDisplayed()
        compose.onNodeWithTag(DashboardTags.LOCATOR).assertDoesNotExist() // no locator until entered
        compose.onNodeWithTag(AppTab.CLOCK.testTag).assertIsSelected() // the Clock is the start section
        openTab(AppTab.PROPAGATION)
        compose.onNodeWithText("PROPAGARE").assertIsDisplayed()
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
