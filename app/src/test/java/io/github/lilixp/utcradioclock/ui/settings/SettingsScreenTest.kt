package io.github.lilixp.utcradioclock.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lilixp.utcradioclock.domain.model.PositionSource
import io.github.lilixp.utcradioclock.domain.model.StationIdentity
import io.github.lilixp.utcradioclock.domain.model.ThemeMode
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
    private var background = Color.Unspecified

    private fun show(dark: Boolean = false) = compose.setContent {
        val station by viewModel.station.collectAsStateWithLifecycle()
        val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
        val positionSource by viewModel.positionSource.collectAsStateWithLifecycle()
        UTCRadioClockTheme(darkTheme = dark) {
            background = MaterialTheme.colorScheme.background
            SettingsScreen(
                station = station,
                themeMode = themeMode,
                onCallsignChange = viewModel::setCallsign,
                onLocatorChange = viewModel::setLocator,
                onThemeModeChange = viewModel::setThemeMode,
                onBack = { wentBack = true },
                appVersion = "2.0.0",
                positionSource = positionSource,
                onPositionSourceChange = viewModel::setPositionSource,
            )
        }
    }

    @Test
    fun aboutTheApp_versionAuthorAndTheN0nbhSource() {
        show()
        compose.onNodeWithText("DESPRE").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("UTC Radio Clock · Versiunea 2.0.0").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Autor: Lilian Putină, ER1PL").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(SettingsTags.ABOUT_PROPAGATION).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("N0NBH (hamqsl.com)", substring = true).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en")
    fun aboutTheAppInEnglish() {
        show()
        compose.onNodeWithText("ABOUT").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("UTC Radio Clock · Version 2.0.0").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("N0NBH (hamqsl.com)", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun positionSource_manualByDefault_withWhatItMeans() {
        show()
        compose.onNodeWithText("Poziția stației").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Manual").assertIsSelected()
        compose.onNodeWithText("Automat (GPS)").assertIsNotSelected()
        compose.onNodeWithTag(SettingsTags.POSITION_HINT).assertTextEquals("Centrul locatorului de mai sus.")
    }

    @Test
    fun positionSource_automaticIsSaved_andExplained() {
        show()
        compose.onNodeWithText("Automat (GPS)").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(PositionSource.AUTOMATIC, settings.positionSource.value)
        compose.onNodeWithText("Automat (GPS)").assertIsSelected()
        compose.onNodeWithTag(SettingsTags.POSITION_HINT)
            .assertTextContains("se folosește locatorul de mai sus", substring = true)
            .assertTextContains("Poziția rămâne pe telefon", substring = true)
        // The manual locator stays there, as the fallback
        compose.onNodeWithTag(SettingsTags.LOCATOR_FIELD).assertIsDisplayed()

        compose.onNodeWithText("Manual").performClick()
        compose.waitForIdle()
        assertEquals(PositionSource.MANUAL, settings.positionSource.value)
    }

    @Test
    @Config(qualifiers = "en")
    fun positionSourceInEnglish() {
        show()
        compose.onNodeWithText("Station position").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Automatic (GPS)").performScrollTo().performClick()
        compose.onNodeWithTag(SettingsTags.POSITION_HINT).assertTextContains("The position stays on the phone.", substring = true)
    }

    /** Clicks a theme option and checks that only it is selected and that it was saved. */
    private fun choose(label: String, mode: ThemeMode) {
        compose.onNodeWithText(label).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(mode, settings.themeMode.value)
        for (other in listOf("Sistem", "Luminos", "Întunecat")) {
            val node = compose.onNodeWithText(other)
            if (other == label) node.assertIsSelected() else node.assertIsNotSelected()
        }
    }

    @Test
    fun showsTitleFieldsAndHowSavingWorks() {
        show()
        compose.onNodeWithText("Setări").assertIsDisplayed()
        compose.onNodeWithText("STAȚIE").assertIsDisplayed()
        compose.onNodeWithText("Indicativ").assertIsDisplayed()
        compose.onNodeWithText("Locator Maidenhead").assertIsDisplayed()
        compose.onNodeWithTag(SettingsTags.CALLSIGN_FIELD).assertTextContains("ER1PL")
        compose.onNodeWithText("Litere, cifre și /, cel mult 15 caractere").assertIsDisplayed()
        compose.onNodeWithText("Modificările se salvează automat.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun appearanceSectionComesAfterStation() {
        show()
        compose.onNodeWithText("ASPECT").performScrollTo().assertIsDisplayed()
        val station = compose.onNodeWithText("STAȚIE").fetchSemanticsNode().positionInRoot.y
        val appearance = compose.onNodeWithText("ASPECT").fetchSemanticsNode().positionInRoot.y
        assertTrue(appearance > station)
        compose.onNodeWithText("Sistem").assertIsSelected() // the default
    }

    @Test
    fun systemOption() {
        settings.setThemeMode(ThemeMode.DARK)
        show()
        choose("Sistem", ThemeMode.SYSTEM)
    }

    @Test
    fun lightOption() {
        show()
        choose("Luminos", ThemeMode.LIGHT)
    }

    @Test
    fun darkOption() {
        show()
        choose("Întunecat", ThemeMode.DARK)
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
    fun worksInLightTheme() {
        show(dark = false)
        compose.onNodeWithText("Setări").assertIsDisplayed()
        compose.onNodeWithText("ASPECT").performScrollTo().assertIsDisplayed()
        assertTrue(background.luminance() > 0.8f)
    }

    @Test
    fun worksInDarkTheme() {
        show(dark = true)
        compose.onNodeWithText("Setări").assertIsDisplayed()
        compose.onNodeWithTag(SettingsTags.LOCATOR_FIELD).assertIsDisplayed()
        compose.onNodeWithText("ASPECT").performScrollTo().assertIsDisplayed()
        assertTrue(background.luminance() < 0.1f)
    }
}
