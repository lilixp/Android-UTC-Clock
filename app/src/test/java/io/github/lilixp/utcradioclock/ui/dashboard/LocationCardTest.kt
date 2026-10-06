package io.github.lilixp.utcradioclock.ui.dashboard

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.lilixp.utcradioclock.domain.model.GpsStatus
import io.github.lilixp.utcradioclock.domain.model.PositionOrigin
import io.github.lilixp.utcradioclock.domain.model.PositionSource
import io.github.lilixp.utcradioclock.ui.theme.UTCRadioClockTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The LOCATION screen in every state, in Romanian (and English at the end). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ro-w411dp-h891dp")
class LocationCardTest {

    @get:Rule
    val compose = createComposeRule()

    private val actions = mutableListOf<LocationAction>()
    private val sources = mutableListOf<PositionSource>()

    /** A recent GPS position, 2,8 km from the home locator. */
    private val gps = LocationUiState(
        latitude = "46,9612° N",
        longitude = "28,3041° E",
        origin = PositionOrigin.GPS,
        automatic = true,
        gps = GpsStatus.OK,
        fixTime = "15:41",
        accuracy = "±12 m",
        source = SunPositionSource.GPS,
        extendedLocator = "KN46dx53",
        altitude = "185 m",
        declination = "6,4° E",
        home = HomeUi(locator = "KN46dw", distance = "2,8 km", bearing = "200°"),
    )

    /** Manual mode: the locator's centre. */
    private val manual = LocationUiState(
        latitude = "46,9375° N",
        longitude = "28,2917° E",
        origin = PositionOrigin.LOCATOR,
        source = SunPositionSource.MANUAL,
        declination = "6,4° E",
    )

    /** Automatic mode with the locator as fallback, the GPS in [status]. */
    private fun fallback(status: GpsStatus) = manual.copy(automatic = true, gps = status, source = SunPositionSource.BACKUP_LOCATOR)

    private fun show(location: LocationUiState, locator: String? = "KN46dw", blocked: Boolean = false, dark: Boolean = false) =
        compose.setContent {
            UTCRadioClockTheme(darkTheme = dark) {
                LocationCard(location, locator, blocked, { actions += it }) { sources += it }
            }
        }

    private fun assertNoProblem() {
        compose.onNodeWithTag(DashboardTags.LOCATION_STATUS).assertDoesNotExist()
        compose.onNodeWithTag(DashboardTags.LOCATION_ACTION).assertDoesNotExist()
    }

    private fun assertNoGpsTiles() {
        compose.onNodeWithTag(DashboardTags.LOCATION_ALTITUDE).assertDoesNotExist()
        compose.onNodeWithTag(DashboardTags.LOCATION_ACCURACY).assertDoesNotExist()
        compose.onNodeWithTag(DashboardTags.LOCATION_EXTENDED_LOCATOR).assertDoesNotExist()
        compose.onNodeWithTag(DashboardTags.LOCATION_HOME).assertDoesNotExist()
    }

    @Test
    fun gpsPosition_locatorSourceSwitchHomeAndTiles() {
        show(gps, locator = "KN46dx")
        compose.onNodeWithText("LOCAȚIE").assertIsDisplayed()
        compose.onNodeWithTag(DashboardTags.LOCATION_LOCATOR).assertTextEquals("KN46dx")
        compose.onNodeWithTag(DashboardTags.LOCATION_EXTENDED_LOCATOR).assertTextEquals("KN46dx53")
        compose.onNodeWithTag(DashboardTags.LOCATION_SOURCE).assertTextEquals("Poziție din GPS · 15:41 UTC")
        compose.onNodeWithText("Automat (GPS)").assertIsSelected()
        compose.onNodeWithText("Manual").assertIsNotSelected()
        compose.onNodeWithTag(DashboardTags.LOCATION_HOME).assertTextEquals("La 2,8 km de KN46dw · azimut spre casă 200°")
        for (text in listOf("Latitudine", "46,9612° N", "Longitudine", "28,3041° E", "Altitudine", "185 m", "Din GPS",
            "Precizie", "±12 m", "GPS exact", "Declinație magnetică", "6,4° E", "Nordul busolei față de nordul geografic")) {
            compose.onNodeWithText(text).assertExists()
        }
        assertNoProblem()
    }

    @Test
    fun approximateGpsPosition_saysSo_andMissingValuesAreDashes() {
        show(gps.copy(approximate = true, accuracy = null, altitude = null))
        compose.onNodeWithText("GPS aproximativ").assertExists()
        compose.onNodeWithTag(DashboardTags.LOCATION_ALTITUDE).assertExists()
        compose.onAllNodesWithText("—").assertCountEquals(2) // the height and the accuracy
    }

    @Test
    fun lastGpsPosition_markedAsSuch_withItsUtcTime_andLocationOffSaid() {
        show(gps.copy(gps = GpsStatus.LOCATION_OFF, fixTime = "29 sept. 15:41", source = SunPositionSource.LAST_GPS))
        compose.onNodeWithTag(DashboardTags.LOCATION_SOURCE).assertTextEquals("Ultima poziție GPS disponibilă · 29 sept. 15:41 UTC")
        compose.onNodeWithTag(DashboardTags.LOCATION_STATUS).assertTextEquals("Locația telefonului este oprită.")
        compose.onNodeWithText("Pornește locația").performClick()
        assertEquals(listOf(LocationAction.OPEN_LOCATION_SETTINGS), actions)
    }

    @Test
    fun manualLocator_itsCentre_coordinatesAndDeclinationOnly() {
        show(manual)
        compose.onNodeWithTag(DashboardTags.LOCATION_LOCATOR).assertTextEquals("KN46dw")
        compose.onNodeWithTag(DashboardTags.LOCATION_SOURCE).assertTextEquals("Locator manual · centrul pătratului")
        compose.onNodeWithText("Manual").assertIsSelected()
        compose.onNodeWithText("46,9375° N").assertExists()
        compose.onNodeWithTag(DashboardTags.LOCATION_DECLINATION).assertExists()
        assertNoGpsTiles()
        assertNoProblem()
    }

    @Test
    fun theSwitch_isTheSameSettingAsInSettings() {
        show(manual)
        compose.onNodeWithText("Manual").performClick() // already chosen: nothing to change
        compose.onNodeWithText("Automat (GPS)").performClick()
        assertEquals(listOf(PositionSource.AUTOMATIC), sources)
    }

    @Test
    fun theSwitch_backToManual() {
        show(gps)
        compose.onNodeWithText("Manual").performClick()
        assertEquals(listOf(PositionSource.MANUAL), sources)
    }

    @Test
    fun noPositionAtAll_noTiles_saysSo() {
        show(LocationUiState(), locator = null)
        compose.onNodeWithTag(DashboardTags.LOCATION_LOCATOR).assertTextEquals("—")
        compose.onNodeWithTag(DashboardTags.LOCATION_SOURCE).assertTextEquals("Locația nu este disponibilă.")
        compose.onNodeWithTag(DashboardTags.LOCATION_LATITUDE).assertDoesNotExist()
        compose.onNodeWithTag(DashboardTags.LOCATION_DECLINATION).assertDoesNotExist()
        compose.onNodeWithText("Manual").assertIsSelected() // the switch is there, to choose GPS
    }

    @Test
    fun noPermission_askButton_onTheFallbackLocator() {
        show(fallback(GpsStatus.NO_PERMISSION))
        compose.onNodeWithTag(DashboardTags.LOCATION_SOURCE).assertTextEquals("Locator de rezervă · centrul pătratului")
        compose.onNodeWithTag(DashboardTags.LOCATION_STATUS).assertTextEquals("Aplicația nu are acces la locația telefonului.")
        compose.onNodeWithText("Permite accesul la locație").performClick()
        assertEquals(listOf(LocationAction.REQUEST_PERMISSION), actions)
        assertNoGpsTiles()
    }

    @Test
    fun refusedForGood_appSettingsButton() {
        show(fallback(GpsStatus.NO_PERMISSION), blocked = true)
        compose.onNodeWithTag(DashboardTags.LOCATION_STATUS)
            .assertTextEquals("Accesul la locație a fost refuzat. Îl poți permite din setările aplicației.")
        compose.onNodeWithText("Deschide setările aplicației").performClick()
        assertEquals(listOf(LocationAction.OPEN_APP_SETTINGS), actions)
    }

    @Test
    fun locationOff_turnOnButton() {
        show(fallback(GpsStatus.LOCATION_OFF))
        compose.onNodeWithTag(DashboardTags.LOCATION_STATUS).assertTextEquals("Locația telefonului este oprită.")
        compose.onNodeWithText("Pornește locația").performClick()
        assertEquals(listOf(LocationAction.OPEN_LOCATION_SETTINGS), actions)
    }

    @Test
    fun noPositionRightNow_saidWithoutButton() {
        show(fallback(GpsStatus.UNAVAILABLE))
        compose.onNodeWithTag(DashboardTags.LOCATION_STATUS).assertTextEquals("Poziția nu este disponibilă momentan.")
        compose.onNodeWithTag(DashboardTags.LOCATION_ACTION).assertDoesNotExist()
    }

    @Test
    fun searching_saidOnlyWhileThereIsNoGpsPosition() {
        show(fallback(GpsStatus.SEARCHING))
        compose.onNodeWithTag(DashboardTags.LOCATION_STATUS).assertTextEquals("Se caută poziția…")
    }

    @Test
    fun searchingAgainWithAPositionShown_quiet() {
        show(gps.copy(gps = GpsStatus.SEARCHING))
        assertNoProblem()
    }

    @Test
    fun invalidLocator_saidOnTheCard_noTiles() {
        show(LocationUiState(invalidLocator = "KN4"), locator = null)
        compose.onNodeWithTag(DashboardTags.LOCATION_SOURCE).assertTextEquals("Locatorul „KN4” nu este valid.")
        compose.onNodeWithTag(DashboardTags.LOCATION_LATITUDE).assertDoesNotExist()
    }

    @Test
    fun invalidLocatorInAutomaticMode_locationOffIsAlsoSaid() {
        show(LocationUiState(automatic = true, gps = GpsStatus.LOCATION_OFF, invalidLocator = "KN"), locator = null)
        compose.onNodeWithTag(DashboardTags.LOCATION_SOURCE).assertTextEquals("Locatorul „KN” nu este valid.")
        compose.onNodeWithTag(DashboardTags.LOCATION_STATUS).assertTextEquals("Locația telefonului este oprită.")
    }

    @Test
    fun darkTheme_sameTexts() {
        show(gps, dark = true)
        compose.onNodeWithTag(DashboardTags.LOCATION_SOURCE).assertTextEquals("Poziție din GPS · 15:41 UTC")
        compose.onNodeWithText("6,4° E").assertExists()
    }

    @Test
    @Config(qualifiers = "en-w411dp-h891dp")
    fun english() {
        show(gps.copy(latitude = "46.9612° N", approximate = true, declination = "6.4° E",
            home = HomeUi("KN46dw", "2.8 km", "200°")))
        compose.onNodeWithText("LOCATION").assertIsDisplayed()
        compose.onNodeWithTag(DashboardTags.LOCATION_SOURCE).assertTextEquals("Position from GPS · 15:41 UTC")
        compose.onNodeWithTag(DashboardTags.LOCATION_HOME).assertTextEquals("2.8 km from KN46dw · bearing home 200°")
        for (text in listOf("Latitude", "46.9612° N", "Altitude", "From GPS", "Accuracy", "Approximate GPS",
            "Magnetic declination", "6.4° E", "Compass north vs. true north", "Manual", "Automatic (GPS)")) {
            compose.onNodeWithText(text).assertExists()
        }
    }

    @Test
    @Config(qualifiers = "en-w411dp-h891dp")
    fun englishManualAndNoPermission() {
        show(fallback(GpsStatus.NO_PERMISSION).copy(latitude = "46.9375° N"))
        compose.onNodeWithTag(DashboardTags.LOCATION_SOURCE).assertTextEquals("Fallback locator · centre of the square")
        compose.onNodeWithTag(DashboardTags.LOCATION_STATUS).assertTextEquals("The app may not use the location of the phone.")
        compose.onNodeWithText("Allow location access").assertIsDisplayed()
    }
}
