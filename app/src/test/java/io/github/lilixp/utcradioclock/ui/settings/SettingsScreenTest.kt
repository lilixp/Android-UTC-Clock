package io.github.lilixp.utcradioclock.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
        UTCRadioClockTheme(darkTheme = dark) {
            background = MaterialTheme.colorScheme.background
            SettingsScreen(
                station = station,
                themeMode = themeMode,
                onCallsignChange = viewModel::setCallsign,
                onLocatorChange = viewModel::setLocator,
                onThemeModeChange = viewModel::setThemeMode,
                onBack = { wentBack = true },
            )
        }
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
