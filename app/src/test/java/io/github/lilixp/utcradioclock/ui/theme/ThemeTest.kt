package io.github.lilixp.utcradioclock.ui.theme

import io.github.lilixp.utcradioclock.domain.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeTest {

    @Test
    fun systemFollowsThePhone() {
        assertEquals(true, ThemeMode.SYSTEM.isDark(systemIsDark = true))
        assertEquals(false, ThemeMode.SYSTEM.isDark(systemIsDark = false))
    }

    @Test
    fun lightAndDarkIgnoreThePhone() {
        for (phoneDark in listOf(true, false)) {
            assertEquals(false, ThemeMode.LIGHT.isDark(phoneDark))
            assertEquals(true, ThemeMode.DARK.isDark(phoneDark))
        }
    }
}
