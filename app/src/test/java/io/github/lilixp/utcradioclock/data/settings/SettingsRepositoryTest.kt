package io.github.lilixp.utcradioclock.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.lilixp.utcradioclock.domain.model.StationIdentity
import io.github.lilixp.utcradioclock.domain.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The real SharedPreferences, on the JVM. A new repository over the same storage is what the app
 * gets after a restart.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun restart() = SharedPreferencesSettingsRepository(context)

    @Test
    fun defaults_callsignEr1plAndEmptyLocator() {
        val station = restart().station.value
        assertEquals("ER1PL", station.callsign)
        assertEquals("", station.locator)
        assertEquals(ThemeMode.SYSTEM, restart().themeMode.value)
    }

    @Test
    fun callsignAndLocatorAreSavedAndReloaded() {
        val first = restart()
        first.setCallsign("yo3abc/p")
        first.setLocator("kn46DW")
        assertEquals(StationIdentity("YO3ABC/P", "KN46dw"), first.station.value)

        assertEquals(StationIdentity("YO3ABC/P", "KN46dw"), restart().station.value)
    }

    @Test
    fun emptyValuesStayEmptyAfterRestart() {
        val first = restart()
        first.setCallsign("")
        first.setLocator("")
        assertEquals(StationIdentity("", ""), restart().station.value)
    }

    @Test
    fun themeIsStillSavedAndReloaded() {
        restart().setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, restart().themeMode.value)
    }
}
