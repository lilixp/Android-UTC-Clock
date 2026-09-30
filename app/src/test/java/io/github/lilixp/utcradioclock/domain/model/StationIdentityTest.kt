package io.github.lilixp.utcradioclock.domain.model

import io.github.lilixp.utcradioclock.domain.model.StationIdentity.Companion.normalizeCallsign
import io.github.lilixp.utcradioclock.domain.model.StationIdentity.Companion.normalizeLocator
import org.junit.Assert.assertEquals
import org.junit.Test

class StationIdentityTest {

    @Test
    fun callsign_upperCaseLettersDigitsAndSlashOnly() {
        assertEquals("ER1PL", normalizeCallsign("er1pl"))
        assertEquals("YO3ABC/P", normalizeCallsign(" yo3abc / p "))
        assertEquals("ER1PL", normalizeCallsign("ER-1_PL!"))
        assertEquals("", normalizeCallsign("șțăî"))
    }

    @Test
    fun callsign_atMost15Characters() {
        assertEquals(15, StationIdentity.MAX_CALLSIGN_LENGTH)
        assertEquals("ER/YO3ABC/QRPAB", normalizeCallsign("ER/YO3ABC/QRPABCDEFG"))
    }

    @Test
    fun locator_writtenTheMaidenheadWay() {
        assertEquals("KN46dw", normalizeLocator("kn46DW"))
        assertEquals("KN46", normalizeLocator("kn46"))
        assertEquals("KN46dw12", normalizeLocator("KN46DW12"))
        assertEquals("KN46dw", normalizeLocator("KN 46-dw"))
    }

    @Test
    fun locator_atMost8Characters() {
        assertEquals(8, StationIdentity.MAX_LOCATOR_LENGTH)
        assertEquals("KN46dw12", normalizeLocator("KN46DW12XX"))
    }

    @Test
    fun defaults() {
        assertEquals(StationIdentity("ER1PL", ""), StationIdentity())
    }
}
