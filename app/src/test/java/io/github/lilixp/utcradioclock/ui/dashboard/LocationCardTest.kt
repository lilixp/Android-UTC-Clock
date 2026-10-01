package io.github.lilixp.utcradioclock.ui.dashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.lilixp.utcradioclock.domain.model.GpsStatus
import io.github.lilixp.utcradioclock.domain.model.PositionOrigin
import io.github.lilixp.utcradioclock.ui.theme.UTCRadioClockTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The LOCATION card in every state, in Romanian (and English at the end). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ro")
class LocationCardTest {

    @get:Rule
    val compose = createComposeRule()

    private val actions = mutableListOf<LocationAction>()

    private val gps = LocationUiState(
        latitude = "46,9612° N",
        longitude = "28,3041° E",
        origin = PositionOrigin.GPS,
        automatic = true,
        gps = GpsStatus.OK,
        fixTime = "18:41",
        accuracy = "±12 m",
    )

    /** Automatic mode with the locator as fallback, the GPS in [status]. */
    private fun fallback(status: GpsStatus) = LocationUiState(
        latitude = "46,9375° N",
        longitude = "28,2917° E",
        origin = PositionOrigin.LOCATOR,
        automatic = true,
        gps = status,
    )

    private fun show(location: LocationUiState, locator: String? = "KN46dw", blocked: Boolean = false, dark: Boolean = false) =
        compose.setContent {
            UTCRadioClockTheme(darkTheme = dark) { LocationCard(location, locator, blocked) { actions += it } }
        }

    private fun assertNoProblem() {
        compose.onNodeWithTag(DashboardTags.LOCATION_STATUS).assertDoesNotExist()
        compose.onNodeWithTag(DashboardTags.LOCATION_ACTION).assertDoesNotExist()
    }

    @Test
    fun gpsPosition_coordinatesLocatorAndWhereFrom() {
        show(gps, locator = "KN46dx")
        compose.onNodeWithText("LOCAȚIE").assertIsDisplayed()
        compose.onNodeWithText("Latitudine: 46,9612° N").assertIsDisplayed()
        compose.onNodeWithText("Longitudine: 28,3041° E").assertIsDisplayed()
        compose.onNodeWithText("QTH: KN46dx").assertIsDisplayed()
        compose.onNodeWithTag(DashboardTags.LOCATION_SOURCE).assertTextEquals("GPS · 18:41 · ±12 m")
        assertNoProblem()
    }

    @Test
    fun approximateGpsPosition_saysSo() {
        show(gps.copy(approximate = true, accuracy = null))
        compose.onNodeWithTag(DashboardTags.LOCATION_SOURCE).assertTextEquals("GPS (aproximativ) · 18:41")
    }

    @Test
    fun manualLocator_itsCentre_noGpsMessages() {
        show(fallback(GpsStatus.OK).copy(automatic = false, gps = null))
        compose.onNodeWithText("Latitudine: 46,9375° N").assertIsDisplayed()
        compose.onNodeWithText("QTH: KN46dw").assertIsDisplayed()
        compose.onNodeWithTag(DashboardTags.LOCATION_SOURCE).assertTextEquals("Locator din Setări (centrul pătratului)")
        assertNoProblem()
    }

    @Test
    fun noPositionAtAll_dashesAndNotAvailable() {
        show(LocationUiState(), locator = null)
        for (text in listOf("Latitudine: —", "Longitudine: —", "QTH: —")) compose.onNodeWithText(text).assertIsDisplayed()
        compose.onNodeWithTag(DashboardTags.LOCATION_SOURCE).assertTextEquals("Locația nu este disponibilă.")
    }

    @Test
    fun noPermission_askButton() {
        show(fallback(GpsStatus.NO_PERMISSION))
        compose.onNodeWithTag(DashboardTags.LOCATION_SOURCE).assertTextEquals("Locator din Setări (centrul pătratului)")
        compose.onNodeWithTag(DashboardTags.LOCATION_STATUS).assertTextEquals("Aplicația nu are acces la locația telefonului.")
        compose.onNodeWithText("Permite accesul la locație").performClick()
        assertEquals(listOf(LocationAction.REQUEST_PERMISSION), actions)
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
    fun oldGpsPositionWhileLocationIsOff_bothSaid() {
        show(gps.copy(gps = GpsStatus.LOCATION_OFF, fixTime = "29 sept. 18:41"))
        compose.onNodeWithTag(DashboardTags.LOCATION_SOURCE).assertTextEquals("GPS · 29 sept. 18:41 · ±12 m")
        compose.onNodeWithTag(DashboardTags.LOCATION_STATUS).assertTextEquals("Locația telefonului este oprită.")
    }

    @Test
    fun darkTheme_sameTexts() {
        show(fallback(GpsStatus.NO_PERMISSION), dark = true)
        compose.onNodeWithText("Permite accesul la locație").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en")
    fun english() {
        show(gps.copy(latitude = "46.9612° N", approximate = true, gps = GpsStatus.LOCATION_OFF))
        compose.onNodeWithText("LOCATION").assertIsDisplayed()
        compose.onNodeWithText("Latitude: 46.9612° N").assertIsDisplayed()
        compose.onNodeWithTag(DashboardTags.LOCATION_SOURCE).assertTextEquals("GPS (approximate) · 18:41 · ±12 m")
        compose.onNodeWithTag(DashboardTags.LOCATION_STATUS).assertTextEquals("Location is turned off on the phone.")
        compose.onNodeWithText("Turn on location").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en")
    fun englishNoPermission() {
        show(fallback(GpsStatus.NO_PERMISSION).copy(latitude = "46.9375° N"))
        compose.onNodeWithTag(DashboardTags.LOCATION_SOURCE).assertTextEquals("Locator from Settings (centre of the square)")
        compose.onNodeWithTag(DashboardTags.LOCATION_STATUS).assertTextEquals("The app may not use the location of the phone.")
        compose.onNodeWithText("Allow location access").assertIsDisplayed()
    }
}
