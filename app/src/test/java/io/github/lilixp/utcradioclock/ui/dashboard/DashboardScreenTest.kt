package io.github.lilixp.utcradioclock.ui.dashboard

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import io.github.lilixp.utcradioclock.ui.theme.UTCRadioClockTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The dashboard, drawn on the JVM with Robolectric (in Romanian, as on the user's phone). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ro")
class DashboardScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val state = DashboardUiState(
        utcDate = "30 septembrie 2026",
        utcTime = "15:42:31",
        localTime = "18:42:31",
        localDate = null,
        timeZone = "Europe/Chisinau · UTC+03:00",
    )

    private var settingsOpened = false
    private var background = Color.Unspecified

    private fun show(s: DashboardUiState = state, dark: Boolean = false) = compose.setContent {
        UTCRadioClockTheme(darkTheme = dark) {
            background = MaterialTheme.colorScheme.background
            DashboardScreen(s, onOpenSettings = { settingsOpened = true })
        }
    }

    private fun scrollTo(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }

    @Test
    fun showsTitleDateAndBothClocks() {
        show()
        compose.onNodeWithText("UTC Radio Clock").assertIsDisplayed()
        compose.onNodeWithTag(DashboardTags.DATE).assertTextEquals("30 septembrie 2026")
        compose.onNodeWithText("UTC").assertIsDisplayed()
        compose.onNodeWithTag(DashboardTags.UTC_TIME).assertTextEquals("15:42:31").assertIsDisplayed()
        compose.onNodeWithText("LOCAL").assertIsDisplayed()
        compose.onNodeWithTag(DashboardTags.LOCAL_TIME).assertTextEquals("18:42:31")
        compose.onNodeWithText("Europe/Chisinau · UTC+03:00").assertExists()
    }

    @Test
    fun stationIsShownNextToTheDate() {
        show(state.copy(callsign = "ER1PL", locator = "KN46dw"))
        compose.onNodeWithTag(DashboardTags.CALLSIGN).assertTextEquals("ER1PL").assertIsDisplayed()
        compose.onNodeWithTag(DashboardTags.LOCATOR).assertTextEquals("KN46dw").assertIsDisplayed()
    }

    @Test
    fun emptyStationValuesAreLeftOut() {
        show(state.copy(callsign = "ER1PL", locator = null))
        compose.onNodeWithTag(DashboardTags.CALLSIGN).assertIsDisplayed()
        compose.onNodeWithTag(DashboardTags.LOCATOR).assertDoesNotExist()
    }

    @Test
    fun settingsIconOpensSettings() {
        show()
        compose.onNodeWithContentDescription("Setări").performClick()
        assertTrue(settingsOpened)
    }

    @Test
    fun unknownLocationValuesAreShownAsDashes() {
        show()
        for (text in listOf("Latitudine: —", "Longitudine: —", "QTH: —")) {
            scrollTo(text)
            compose.onNodeWithText(text).assertIsDisplayed()
        }
    }

    // ---- The SUN card ----

    private val sunKn46dw = SunUiState(
        status = SunStatus.NORMAL,
        sunrise = "07:04",
        sunset = "18:49",
        solarNoon = "12:57",
        dayLength = "11h 44m",
        civilDawn = "06:33",
        civilDusk = "19:19",
        locator = "KN46dw",
    )

    private fun assertSunLines(vararg lines: String) {
        for (text in lines) {
            scrollTo(text)
            compose.onNodeWithText(text).assertIsDisplayed()
        }
    }

    @Test
    fun sunCardWithData() {
        show(state.copy(sun = sunKn46dw, locator = "KN46dw"))
        assertSunLines(
            "SOARE",
            "Răsărit: 07:04",
            "Apus: 18:49",
            "Amiază solară: 12:57",
            "Durata zilei: 11h 44m",
            "Crepuscul civil: 06:33 – 19:19",
        )
        compose.onNodeWithText("Locația nu este disponibilă.").assertDoesNotExist()
    }

    @Test
    fun sunCardWithoutLocator_noInventedTimes() {
        show(state.copy(sun = SunUiState(SunStatus.NO_LOCATOR)))
        assertSunLines("Locația nu este disponibilă.", "Introdu locatorul Maidenhead în Setări.")
        compose.onNodeWithText("Răsărit", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Apus", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Amiază solară", substring = true).assertDoesNotExist()
    }

    @Test
    fun sunCardWithInvalidLocator() {
        show(state.copy(sun = SunUiState(SunStatus.INVALID_LOCATOR, locator = "KN4"), locator = "KN4"))
        assertSunLines("Locația nu este disponibilă.", "Locatorul „KN4” nu este valid.")
        compose.onNodeWithText("Răsărit", substring = true).assertDoesNotExist()
    }

    @Test
    fun sunCardOnAPolarDay() {
        val sun = SunUiState(SunStatus.MIDNIGHT_SUN, solarNoon = "13:02", dayLength = "24h 00m", locator = "JQ78")
        show(state.copy(sun = sun))
        assertSunLines("Soarele nu apune în această zi.", "Amiază solară: 13:02", "Durata zilei: 24h 00m")
        compose.onNodeWithText("Răsărit", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Crepuscul", substring = true).assertDoesNotExist() // no twilight to show
    }

    @Test
    fun sunCardOnAWhiteNight_twilightMissingIsADash() {
        show(state.copy(sun = sunKn46dw.copy(civilDawn = null, civilDusk = null)))
        assertSunLines("Răsărit: 07:04")
        compose.onNodeWithText("Crepuscul", substring = true).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "en")
    fun sunCardInEnglish() {
        show(state.copy(sun = sunKn46dw))
        assertSunLines(
            "SUN",
            "Sunrise: 07:04",
            "Sunset: 18:49",
            "Solar noon: 12:57",
            "Day length: 11h 44m",
            "Civil twilight: 06:33 – 19:19",
        )
    }

    @Test
    @Config(qualifiers = "en")
    fun sunCardWithoutLocatorInEnglish() {
        show(state.copy(sun = SunUiState(SunStatus.NO_LOCATOR)))
        assertSunLines("Location not available.", "Enter your Maidenhead locator in Settings.")
    }

    @Test
    fun sunCardInTheDarkTheme() {
        show(state.copy(sun = sunKn46dw), dark = true)
        assertSunLines("Răsărit: 07:04", "Durata zilei: 11h 44m")
        assertTrue(background.luminance() < 0.1f)
    }

    @Test
    fun propagationCardIsLastAndLoadingAtFirst() {
        show()
        scrollTo("PROPAGARE")
        compose.onNodeWithText("PROPAGARE").assertIsDisplayed()
        compose.onNodeWithText("Se încarcă datele…").assertExists()
    }

    @Test
    fun locatorIsShownAtQth() {
        show(state.copy(locator = "KN46dw"))
        scrollTo("QTH: KN46dw")
        compose.onNodeWithText("QTH: KN46dw").assertIsDisplayed()
    }

    @Test
    fun appearanceIsNoLongerOnTheDashboard() {
        show()
        // Propagation is now the last card: after it there is no ASPECT card and no theme buttons
        scrollTo("PROPAGARE")
        compose.onNodeWithText("PROPAGARE").assertIsDisplayed()
        compose.onNodeWithText("ASPECT").assertDoesNotExist()
        compose.onNodeWithText("Sistem").assertDoesNotExist()
        compose.onNodeWithText("Luminos").assertDoesNotExist()
        compose.onNodeWithText("Întunecat").assertDoesNotExist()
    }

    @Test
    fun lightThemeHasALightBackground() {
        show(state.copy(callsign = "ER1PL"), dark = false)
        compose.onNodeWithTag(DashboardTags.CALLSIGN).assertIsDisplayed()
        assertTrue(background.luminance() > 0.8f)
    }

    @Test
    fun darkThemeHasADarkBackground() {
        show(state.copy(callsign = "ER1PL"), dark = true)
        compose.onNodeWithTag(DashboardTags.UTC_TIME).assertIsDisplayed()
        compose.onNodeWithTag(DashboardTags.CALLSIGN).assertIsDisplayed()
        assertTrue(background.luminance() < 0.1f)
    }
}
