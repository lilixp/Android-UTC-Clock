package io.github.lilixp.utcradioclock.ui.dashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasScrollAction
import io.github.lilixp.utcradioclock.domain.model.ThemeMode
import io.github.lilixp.utcradioclock.ui.theme.UTCRadioClockTheme
import org.junit.Assert.assertEquals
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

    private var chosenTheme: ThemeMode? = null

    private fun show(s: DashboardUiState = state) = compose.setContent {
        UTCRadioClockTheme(darkTheme = false) { DashboardScreen(s) { chosenTheme = it } }
    }

    private fun scrollTo(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }

    @Test
    fun showsTitleDateAndBothClocks() {
        show()
        compose.onNodeWithText("UTCRadioClock").assertIsDisplayed()
        compose.onNodeWithTag(DashboardTags.DATE).assertTextEquals("30 septembrie 2026")
        compose.onNodeWithText("UTC").assertIsDisplayed()
        compose.onNodeWithTag(DashboardTags.UTC_TIME).assertTextEquals("15:42:31").assertIsDisplayed()
        compose.onNodeWithText("LOCAL").assertIsDisplayed()
        compose.onNodeWithTag(DashboardTags.LOCAL_TIME).assertTextEquals("18:42:31")
        compose.onNodeWithText("Europe/Chisinau · UTC+03:00").assertExists()
    }

    @Test
    fun unknownValuesAreShownAsDashes() {
        show()
        for (text in listOf("Răsărit: —", "Apus: —", "Durata zilei: —", "Latitudine: —", "Longitudine: —", "QTH: —")) {
            scrollTo(text)
            compose.onNodeWithText(text).assertIsDisplayed()
        }
    }

    @Test
    fun propagationSaysItComesLater() {
        show()
        scrollTo("PROPAGARE")
        compose.onNodeWithText("PROPAGARE").assertIsDisplayed()
        compose.onNodeWithText("Funcția va fi disponibilă într-o fază ulterioară.").assertExists()
    }

    @Test
    fun knownValuesReplaceTheDashes() {
        show(state.copy(sunrise = "06:58", locator = "KN46dw"))
        scrollTo("Răsărit: 06:58")
        compose.onNodeWithText("Răsărit: 06:58").assertIsDisplayed()
        scrollTo("QTH: KN46dw")
        compose.onNodeWithText("QTH: KN46dw").assertIsDisplayed()
    }

    @Test
    fun themeSelectorShowsTheChoiceAndReportsClicks() {
        show(state.copy(themeMode = ThemeMode.LIGHT))
        scrollTo("Întunecat")
        compose.onNodeWithText("Luminos").assertIsSelected()
        compose.onNodeWithText("Întunecat").performClick()
        assertEquals(ThemeMode.DARK, chosenTheme)
    }
}
