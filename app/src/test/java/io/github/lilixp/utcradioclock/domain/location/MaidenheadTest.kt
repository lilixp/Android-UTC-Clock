package io.github.lilixp.utcradioclock.domain.location

import io.github.lilixp.utcradioclock.domain.model.GeoPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MaidenheadTest {

    private fun assertPosition(latitude: Double, longitude: Double, actual: GeoPosition?) {
        assertNotNull(actual)
        assertEquals("latitude", latitude, actual!!.latitude, 1e-6)
        assertEquals("longitude", longitude, actual.longitude, 1e-6)
    }

    @Test
    fun sixCharacters_centreOfTheSubsquare() {
        // KN46dw: 46°54′–46°57′30″ N, 28°15′–28°20′ E → centre 46.9375° N, 28.291667° E
        assertPosition(46.9375, 28.291667, Maidenhead.toPosition("KN46dw"))
    }

    @Test
    fun otherLocatorsGiveOtherPositions_nothingIsFixedOnKn46dw() {
        assertPosition(51.520833, -0.125, Maidenhead.toPosition("IO91wm")) // London, west of Greenwich
        assertPosition(40.770833, -73.958333, Maidenhead.toPosition("FN30as")) // New York
        assertPosition(-33.9375, 151.208333, Maidenhead.toPosition("QF56ob")) // Sydney, southern hemisphere
        assertPosition(35.5, 139.0, Maidenhead.toPosition("PM95")) // Tokyo, 4 characters
    }

    @Test
    fun twoFourAndEightCharacters() {
        assertPosition(45.0, 30.0, Maidenhead.toPosition("KN")) // a 20° × 10° field
        assertPosition(46.5, 29.0, Maidenhead.toPosition("KN46")) // a 2° × 1° square
        // KN46dw15: the 30″ × 15″ square (1, 5) inside KN46dw, whose corner is 46.916667° N, 28.25° E
        assertPosition(46.939583, 28.2625, Maidenhead.toPosition("KN46dw15"))
    }

    @Test
    fun caseAndSpacesDoNotMatter() {
        assertEquals(Maidenhead.toPosition("KN46dw"), Maidenhead.toPosition(" kn46DW "))
    }

    @Test
    fun cornersOfTheWorld() {
        assertPosition(-89.979167, -179.958333, Maidenhead.toPosition("AA00aa"))
        assertPosition(89.979167, 179.958333, Maidenhead.toPosition("RR99xx"))
    }

    @Test
    fun emptyLocator_noPosition() {
        assertNull(Maidenhead.toPosition(""))
        assertNull(Maidenhead.toPosition("   "))
    }

    @Test
    fun invalidLocators_noPosition() {
        for (text in listOf("K", "KN4", "KN46d", "SN46", "KS46", "KNA6", "KN4X", "KN46yw", "KN46dz", "KN46dwAB", "KN46dw1", "46KN")) {
            assertNull(text, Maidenhead.toPosition(text))
        }
    }
}
