package io.github.lilixp.utcradioclock.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lilixp.utcradioclock.domain.model.StationIdentity
import io.github.lilixp.utcradioclock.testing.FakeSettingsRepository
import io.github.lilixp.utcradioclock.ui.theme.UTCRadioClockTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Settings screen with its ViewModel, in Romanian, in both themes. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ro")
class SettingsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val settings = FakeSettingsRepository(station = StationIdentity("ER1PL", ""))
    private val viewModel = SettingsViewModel(settings)
    private var wentBack = false

    private fun show(dark: Boolean = false) = compose.setContent {
        val station by viewModel.station.collectAsStateWithLifecycle()
        UTCRadioClockTheme(darkTheme = dark) {
            SettingsScreen(station, viewModel::setCallsign, viewModel::setLocator) { wentBack = true }
        }
    }

    @Test
    fun showsTitleFieldsAndHowSavingWorks() {
        show()
        compose.onNodeWithText("Setări").assertIsDisplayed()
        compose.onNodeWithText("Indicativ").assertIsDisplayed()
        compose.onNodeWithText("Locator Maidenhead").assertIsDisplayed()
        compose.onNodeWithTag(SettingsTags.CALLSIGN_FIELD).assertTextContains("ER1PL")
        compose.onNodeWithText("Litere, cifre și /, cel mult 15 caractere").assertIsDisplayed()
        compose.onNodeWithText("Modificările se salvează automat.").assertIsDisplayed()
    }

    @Test
    fun typedValuesAreCleanedAndSaved() {
        show()
        compose.onNodeWithTag(SettingsTags.CALLSIGN_FIELD).performTextClearance()
        compose.onNodeWithTag(SettingsTags.CALLSIGN_FIELD).performTextInput("yo3abc")
        compose.onNodeWithTag(SettingsTags.LOCATOR_FIELD).performTextInput("kn46dw")
        compose.waitForIdle()

        assertEquals(StationIdentity("YO3ABC", "KN46dw"), settings.station.value)
        compose.onNodeWithTag(SettingsTags.CALLSIGN_FIELD).assertTextContains("YO3ABC")
        compose.onNodeWithTag(SettingsTags.LOCATOR_FIELD).assertTextContains("KN46dw")
    }

    @Test
    fun backButtonGoesBack() {
        show()
        compose.onNodeWithContentDescription("Înapoi").performClick()
        assertTrue(wentBack)
    }

    @Test
    fun worksInDarkTheme() {
        show(dark = true)
        compose.onNodeWithText("Setări").assertIsDisplayed()
        compose.onNodeWithTag(SettingsTags.LOCATOR_FIELD).assertIsDisplayed()
    }
}
