package io.github.lilixp.utcradioclock.ui.dashboard

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.performScrollTo
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
        utcDate = "Miercuri, 30 septembrie 2026",
        utcTime = "15:42:31",
        localTime = "18:42:31",
        localDate = null,
        zone = ZoneUi(
            name = "Europe/Chisinau",
            abbreviation = "EEST",
            offset = "UTC+03:00",
            nextChange = ClockChangeUi(date = "25 oct. 2026", from = "04:00", to = "03:00", toWinter = true),
        ),
    )

    private var settingsOpened = false
    private var background = Color.Unspecified

    /** The main screen on [tab] (the Clock by default). */
    private fun show(s: DashboardUiState = state, dark: Boolean = false, tab: AppTab = AppTab.CLOCK) = compose.setContent {
        UTCRadioClockTheme(darkTheme = dark) {
            background = MaterialTheme.colorScheme.background
            DashboardScreen(s, selectedTab = tab, onOpenSettings = { settingsOpened = true })
        }
    }

    /** The main screen with its own selected section, as MainActivity keeps it: the tabs can be tapped. */
    private fun showWithTabs(s: DashboardUiState = state) = compose.setContent {
        var tab by remember { mutableStateOf(AppTab.CLOCK) }
        UTCRadioClockTheme(darkTheme = false) {
            DashboardScreen(s, selectedTab = tab, onSelectTab = { tab = it }, onOpenSettings = { settingsOpened = true })
        }
    }

    private fun scrollTo(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }

    @Test
    fun showsTitleDateAndBothClocks() {
        show()
        compose.onNodeWithText("UTC Radio Clock").assertIsDisplayed()
        compose.onNodeWithTag(DashboardTags.DATE).assertTextEquals("Miercuri, 30 septembrie 2026")
        compose.onNodeWithText("UTC").assertIsDisplayed()
        compose.onNodeWithTag(DashboardTags.UTC_TIME).assertTextEquals("15:42:31").assertIsDisplayed()
        compose.onNodeWithTag(DashboardTags.LOCAL_LABEL).assertTextEquals("LOCAL").assertIsDisplayed()
        compose.onNodeWithTag(DashboardTags.LOCAL_TIME).assertTextEquals("18:42:31")
        compose.onNodeWithText("EEST · UTC+03:00").assertExists()
        compose.onNodeWithText("Europe/Chisinau").assertExists()
    }

    @Test
    fun stationIsShownInTheTopBar() {
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
    fun unknownLocation_aDashForTheLocator_andSaidSo() {
        show(tab = AppTab.LOCATION)
        compose.onNodeWithTag(DashboardTags.LOCATION_LOCATOR).assertTextEquals("—")
        compose.onNodeWithTag(DashboardTags.LOCATION_SOURCE).assertTextEquals("Locația nu este disponibilă.")
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
        show(state.copy(sun = sunKn46dw, locator = "KN46dw"), tab = AppTab.SUN)
        assertSunLines(
            "SOARE",
            "Răsărit", "07:04",
            "Apus", "18:49",
            "Amiază solară", "12:57",
            "Durata zilei", "11h 44m",
            "Crepuscul dimineața", "06:33–07:04",
            "Crepuscul seara", "18:49–19:19",
        )
        compose.onNodeWithText("Locația nu este disponibilă.").assertDoesNotExist()
    }

    @Test
    fun sunCardWithoutLocator_noInventedTimes() {
        show(state.copy(sun = SunUiState(SunStatus.NO_LOCATOR)), tab = AppTab.SUN)
        assertSunLines("Locația nu este disponibilă.", "Introdu locatorul Maidenhead în Setări.")
        compose.onNodeWithText("Răsărit", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Apus", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Amiază solară", substring = true).assertDoesNotExist()
    }

    /** Automatic mode without a position: the SUN card says why, from the GPS status (Phase A). */
    private fun assertSunSays(status: SunStatus, advice: String) {
        show(state.copy(sun = SunUiState(status)), tab = AppTab.SUN)
        // "Location not available." is also on the LOCATION card below, so only the advice is looked for
        assertSunLines(advice)
        compose.onNodeWithText("Răsărit", substring = true).assertDoesNotExist()
    }

    @Test
    fun sunCardAutomatic_noPermission() =
        assertSunSays(SunStatus.GPS_NO_PERMISSION, "Permite accesul la locație sau introdu locatorul Maidenhead în Setări.")

    @Test
    fun sunCardAutomatic_locationOff_notAboutThePermission() {
        assertSunSays(SunStatus.GPS_LOCATION_OFF, "Pornește locația telefonului sau introdu locatorul Maidenhead în Setări.")
        compose.onNodeWithText("Permite accesul", substring = true).assertDoesNotExist()
    }

    @Test
    fun sunCardAutomatic_searching() = assertSunSays(SunStatus.GPS_SEARCHING, "Se caută poziția…")

    @Test
    fun sunCardAutomatic_unavailable() = assertSunSays(
        SunStatus.GPS_UNAVAILABLE,
        "Poziția GPS nu este disponibilă momentan. Poți introduce locatorul Maidenhead în Setări.",
    )

    @Test
    fun sunCardWithInvalidLocator() {
        // As the ViewModel gives it since Phase A: an invalid locator is not the station's locator
        show(state.copy(sun = SunUiState(SunStatus.INVALID_LOCATOR, locator = "KN4"), locator = null), tab = AppTab.SUN)
        assertSunLines("Locația nu este disponibilă.", "Locatorul „KN4” nu este valid.")
        compose.onNodeWithText("Răsărit", substring = true).assertDoesNotExist()
        compose.onNodeWithTag(DashboardTags.LOCATOR).assertDoesNotExist() // not next to the callsign
    }

    @Test
    fun sunCardOnAPolarDay() {
        val sun = SunUiState(SunStatus.MIDNIGHT_SUN, solarNoon = "13:02", dayLength = "24h 00m", locator = "JQ78")
        show(state.copy(sun = sun), tab = AppTab.SUN)
        assertSunLines("Soarele nu apune în această zi.", "Amiază solară", "13:02", "Durata zilei", "24h 00m")
        compose.onNodeWithTag(SunTags.SUNRISE).assertIsDisplayed()
        compose.onNodeWithText("00:00").assertDoesNotExist() // no invented event time
    }

    @Test
    fun sunCardOnAWhiteNight_twilightMissingIsADash() {
        show(state.copy(sun = sunKn46dw.copy(civilDawn = null, civilDusk = null)), tab = AppTab.SUN)
        assertSunLines("Răsărit", "07:04", "Crepuscul dimineața", "Crepuscul seara")
        compose.onNodeWithText("06:33–07:04").assertDoesNotExist()
        compose.onNodeWithText("18:49–19:19").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "en")
    fun sunCardInEnglish() {
        show(state.copy(sun = sunKn46dw), tab = AppTab.SUN)
        assertSunLines(
            "SUN",
            "Sunrise", "07:04",
            "Sunset", "18:49",
            "Solar noon", "12:57",
            "Day length", "11h 44m",
            "Morning twilight", "06:33–07:04",
            "Evening twilight", "18:49–19:19",
        )
    }

    @Test
    @Config(qualifiers = "en")
    fun sunCardWithoutLocatorInEnglish() {
        show(state.copy(sun = SunUiState(SunStatus.NO_LOCATOR)), tab = AppTab.SUN)
        assertSunLines("Location not available.", "Enter your Maidenhead locator in Settings.")
    }

    @Test
    fun sunCardInTheDarkTheme() {
        show(state.copy(sun = sunKn46dw), dark = true, tab = AppTab.SUN)
        assertSunLines("Răsărit", "07:04", "Durata zilei", "11h 44m")
        assertTrue(background.luminance() < 0.1f)
    }

    @Test
    fun propagationTab_loadingAtFirst() {
        show(tab = AppTab.PROPAGATION)
        scrollTo("PROPAGARE")
        compose.onNodeWithText("PROPAGARE").assertIsDisplayed()
        compose.onNodeWithText("Se încarcă datele…").assertExists()
    }

    @Test
    fun locatorIsShownAtQth() {
        show(state.copy(locator = "KN46dw"), tab = AppTab.LOCATION)
        compose.onNodeWithTag(DashboardTags.LOCATION_LOCATOR).assertTextEquals("KN46dw").assertIsDisplayed()
    }

    @Test
    fun appearanceIsNoLongerOnTheDashboard() {
        showWithTabs()
        // On none of the four sections: no ASPECT card and no theme buttons (they are in Settings)
        for (tab in AppTab.entries) {
            compose.onNodeWithTag(tab.testTag).performClick()
            compose.onNodeWithText("ASPECT").assertDoesNotExist()
            compose.onNodeWithText("Sistem").assertDoesNotExist()
            compose.onNodeWithText("Luminos").assertDoesNotExist()
            compose.onNodeWithText("Întunecat").assertDoesNotExist()
        }
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

    // ---- The four sections (bottom bar) ----

    @Test
    fun tabBar_fourSections_clockFirstAndMarked() {
        show()
        for (label in listOf("Ceas", "Soare", "Locație", "Propagare")) compose.onNodeWithText(label).assertIsDisplayed()
        compose.onNodeWithTag(AppTab.CLOCK.testTag).assertIsSelected()
        for (tab in listOf(AppTab.SUN, AppTab.LOCATION, AppTab.PROPAGATION)) compose.onNodeWithTag(tab.testTag).assertIsNotSelected()
        compose.onNodeWithTag(DashboardTags.UTC_TIME).assertIsDisplayed()
    }

    @Test
    fun tabOrder_clockPropagationSunLocation() {
        show()
        val left = listOf("Ceas", "Propagare", "Soare", "Locație")
            .map { compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot.left }
        assertEquals(left.sorted(), left) // left to right in this order
        assertEquals(listOf(AppTab.CLOCK, AppTab.PROPAGATION, AppTab.SUN, AppTab.LOCATION), AppTab.entries.toList())
    }

    @Test
    fun tapOnATab_showsItsCard_andMarksIt() {
        showWithTabs()
        compose.onNodeWithTag(AppTab.SUN.testTag).performClick()
        compose.onNodeWithText("SOARE").assertIsDisplayed()
        compose.onNodeWithTag(AppTab.SUN.testTag).assertIsSelected()
        compose.onNodeWithTag(DashboardTags.UTC_TIME).assertDoesNotExist() // one section at a time

        compose.onNodeWithTag(AppTab.LOCATION.testTag).performClick()
        compose.onNodeWithText("LOCAȚIE").assertIsDisplayed()
        compose.onNodeWithText("SOARE").assertDoesNotExist()

        compose.onNodeWithTag(AppTab.PROPAGATION.testTag).performClick()
        compose.onNodeWithText("PROPAGARE").assertIsDisplayed()
        compose.onNodeWithTag(AppTab.PROPAGATION.testTag).assertIsSelected()

        compose.onNodeWithTag(AppTab.CLOCK.testTag).performClick()
        compose.onNodeWithTag(DashboardTags.UTC_TIME).assertIsDisplayed()
        compose.onNodeWithTag(AppTab.CLOCK.testTag).assertIsSelected()
    }

    @Test
    fun stationAndSettings_onEverySection() {
        showWithTabs(state.copy(callsign = "ER1PL", locator = "KN46dw"))
        for (tab in AppTab.entries) {
            compose.onNodeWithTag(tab.testTag).performClick()
            compose.onNodeWithText("UTC Radio Clock").assertIsDisplayed()
            compose.onNodeWithTag(DashboardTags.CALLSIGN).assertTextEquals("ER1PL").assertIsDisplayed()
            compose.onNodeWithTag(DashboardTags.LOCATOR).assertTextEquals("KN46dw").assertIsDisplayed()
            compose.onNodeWithContentDescription("Setări").assertIsDisplayed()
        }
        compose.onNodeWithContentDescription("Setări").performClick()
        assertTrue(settingsOpened)
    }

    @Test
    fun noStation_onlyTheAppName() {
        show(state.copy(callsign = null, locator = null))
        compose.onNodeWithText("UTC Radio Clock").assertIsDisplayed()
        compose.onNodeWithTag(DashboardTags.CALLSIGN).assertDoesNotExist()
        compose.onNodeWithTag(DashboardTags.LOCATOR).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "en")
    fun tabsInEnglish() {
        show()
        for (label in listOf("Clock", "Sun", "Location", "Propagation")) compose.onNodeWithText(label).assertIsDisplayed()
    }

    @Test
    fun darkTheme_tabsToo() {
        show(dark = true)
        compose.onNodeWithText("Propagare").assertIsDisplayed()
        assertTrue(background.luminance() < 0.1f)
    }

    // ---- The Clock (redesign stage 2) ----

    @Test
    fun clock_utcDateWithTheDayOfTheWeek_andLocalWithoutTheZonesAbbreviation() {
        show() // the zone has an abbreviation (EEST): it is shown on the time zone tile only
        compose.onNodeWithTag(DashboardTags.DATE).assertTextEquals("Miercuri, 30 septembrie 2026")
        compose.onNodeWithTag(DashboardTags.LOCAL_LABEL).assertTextEquals("LOCAL")
        compose.onNodeWithText("LOCAL · EEST").assertDoesNotExist()
        compose.onNodeWithTag(DashboardTags.ZONE_VALUE, useUnmergedTree = true).assertTextEquals("EEST · UTC+03:00")
    }

    @Test
    fun clock_sameDate_theLocalDateIsNotShown() {
        show() // localDate = null: the same date as UTC
        compose.onNodeWithTag(DashboardTags.LOCAL_DATE).assertDoesNotExist()
    }

    @Test
    fun clock_otherDate_theLocalDateIsShown() {
        show(state.copy(utcTime = "23:30:00", localTime = "02:30:00", localDate = "Joi, 1 octombrie 2026"))
        compose.onNodeWithTag(DashboardTags.LOCAL_DATE).assertTextEquals("Joi, 1 octombrie 2026")
        compose.onNodeWithTag(DashboardTags.DATE).assertTextEquals("Miercuri, 30 septembrie 2026")
    }

    @Test
    fun clock_tiles_timeZoneAndNextClockChange() {
        show()
        compose.onNodeWithTag(DashboardTags.CLOCK_CHANGE_TILE).performScrollTo()
        for (text in listOf("Fus orar", "EEST · UTC+03:00", "Europe/Chisinau", "Schimbarea orei", "25 oct. 2026", "04:00 → 03:00", "(iarnă)")) {
            compose.onNodeWithText(text).assertIsDisplayed()
        }
    }

    @Test
    fun clock_changeToSummerTime() {
        show(state.copy(zone = state.zone.copy(nextChange = ClockChangeUi("28 mar. 2027", "03:00", "04:00", toWinter = false))))
        compose.onNodeWithTag(DashboardTags.CLOCK_CHANGE_TILE).performScrollTo()
        compose.onNodeWithText("03:00 → 04:00").assertIsDisplayed()
        compose.onNodeWithText("(vară)").assertIsDisplayed()
    }

    @Test
    fun clock_zoneWithoutAChange_saysSo_notEmpty() {
        show(state.copy(zone = ZoneUi(name = "Asia/Tokyo", abbreviation = "JST", offset = "UTC+09:00", nextChange = null)))
        compose.onNodeWithTag(DashboardTags.CLOCK_CHANGE_TILE).performScrollTo()
        compose.onNodeWithText("Fără schimbarea orei").assertIsDisplayed()
        compose.onNodeWithText("Nu este prevăzută în acest fus orar.").assertIsDisplayed()
    }

    @Test
    fun clock_zoneWithoutAbbreviationOrName_onlyTheOffset() {
        show(state.copy(zone = ZoneUi(name = null, abbreviation = null, offset = "UTC+05:45", nextChange = null)))
        compose.onNodeWithTag(DashboardTags.LOCAL_LABEL).assertTextEquals("LOCAL")
        compose.onNodeWithTag(DashboardTags.ZONE_TILE).performScrollTo()
        compose.onNodeWithText("UTC+05:45").assertIsDisplayed()
    }

    @Test
    fun clock_noNoteAboutUtc() {
        show()
        compose.onNodeWithText("timpul comun", substring = true).assertDoesNotExist()
        compose.onNodeWithText("radioamatorilor", substring = true).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "en")
    fun clock_inEnglish() {
        show(state.copy(utcDate = "Wednesday, 30 September 2026",
            zone = state.zone.copy(nextChange = ClockChangeUi("25 Oct 2026", "04:00", "03:00", toWinter = true))))
        compose.onNodeWithTag(DashboardTags.DATE).assertTextEquals("Wednesday, 30 September 2026")
        compose.onNodeWithTag(DashboardTags.CLOCK_CHANGE_TILE).performScrollTo()
        for (text in listOf("Time zone", "Clock change", "25 Oct 2026", "(winter time)")) compose.onNodeWithText(text).assertIsDisplayed()
    }

    @Test
    fun clock_darkTheme() {
        show(dark = true)
        compose.onNodeWithTag(DashboardTags.UTC_TIME).assertIsDisplayed()
        compose.onNodeWithTag(DashboardTags.ZONE_TILE).performScrollTo().assertIsDisplayed()
        assertTrue(background.luminance() < 0.1f)
    }
}
