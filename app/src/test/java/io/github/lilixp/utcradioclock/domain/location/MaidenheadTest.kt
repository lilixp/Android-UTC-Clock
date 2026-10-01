package io.github.lilixp.utcradioclock.domain.location

import io.github.lilixp.utcradioclock.domain.model.GeoPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
    fun fourAndEightCharacters() {
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
        for (text in listOf("K", "KN", "KN4", "KN46d", "SN46", "KS46", "KNA6", "KN4X", "KN46yw", "KN46dz", "KN46dwAB", "KN46dw1", "46KN")) {
            assertNull(text, Maidenhead.toPosition(text))
        }
    }

    // ---- Coordinates → locator ----

    @Test
    fun fromPosition_sixCharactersWrittenAsInTheApp() {
        assertEquals("KN46dx", Maidenhead.fromPosition(GeoPosition(46.9612, 28.3041))) // Boghiceni (GPS)
        assertEquals("KN46dw", Maidenhead.fromPosition(GeoPosition(46.9375, 28.291667))) // centre of KN46dw
    }

    @Test
    fun fromPosition_allHemispheres() {
        assertEquals("IO91wm", Maidenhead.fromPosition(GeoPosition(51.5074, -0.1278))) // London: west
        assertEquals("FN20xr", Maidenhead.fromPosition(GeoPosition(40.7128, -74.0060))) // New York
        assertEquals("QF56od", Maidenhead.fromPosition(GeoPosition(-33.8688, 151.2093))) // Sydney: south
        assertEquals("GG87jc", Maidenhead.fromPosition(GeoPosition(-22.9068, -43.1729))) // Rio: south and west
    }

    @Test
    fun fromPosition_fourAndEightCharacters() {
        val boghiceni = GeoPosition(46.9612, 28.3041)
        assertEquals("KN46", Maidenhead.fromPosition(boghiceni, 4))
        assertEquals("KN46dw15", Maidenhead.fromPosition(GeoPosition(46.939583, 28.2625), 8))
    }

    @Test
    fun fromPosition_isTheInverseOfToPosition() {
        for (locator in listOf("KN46dw", "IO91wm", "FN30as", "QF56ob", "AA00aa", "RR99xx", "JJ00aa", "KN46dw15")) {
            assertEquals(locator, Maidenhead.fromPosition(Maidenhead.toPosition(locator)!!, locator.length))
        }
    }

    @Test
    fun fromPosition_edgesOfTheWorldStayInTheLastSquare() {
        assertEquals("AA00aa", Maidenhead.fromPosition(GeoPosition(-90.0, -180.0)))
        assertEquals("RR99xx", Maidenhead.fromPosition(GeoPosition(90.0, 180.0))) // not "SS..."
        assertEquals("JJ00aa", Maidenhead.fromPosition(GeoPosition(0.0, 0.0)))
    }

    @Test
    fun fromPosition_invalidCoordinates_noLocator() {
        for (position in listOf(
            GeoPosition(91.0, 0.0),
            GeoPosition(-90.0001, 0.0),
            GeoPosition(0.0, 180.0001),
            GeoPosition(0.0, -181.0),
            GeoPosition(Double.NaN, 28.0),
            GeoPosition(46.0, Double.NaN),
            GeoPosition(Double.POSITIVE_INFINITY, 0.0),
        )) {
            assertNull(position.toString(), Maidenhead.fromPosition(position))
        }
    }

    @Test
    fun fromPosition_onlyTheLengthsTheAppAccepts() {
        for (characters in listOf(0, 2, 3, 5, 7, 10)) {
            try {
                Maidenhead.fromPosition(GeoPosition(46.9612, 28.3041), characters)
                fail("$characters characters")
            } catch (_: IllegalArgumentException) {
                // expected
            }
        }
    }

    // ---- The rule of the app: 4, 6 or 8 characters (Phase A) ----

    @Test
    fun isValid_fourSixOrEightCharacters() {
        for (locator in listOf("KN46", "KN46dw", "KN46dw12", "kn46DW", " KN46dw ", "AA00", "RR99xx99")) {
            assertTrue(locator, Maidenhead.isValid(locator))
        }
    }

    @Test
    fun isValid_notOtherLengthsOrCharacters() {
        for (locator in listOf(
            "", "   ", "K", "KN", "KN4", "KN46d", "KN46dwx", "KN46dw1", "KN46dw123", "KN46dw12ab",
            "KN-46", "KN46d!", "KN46 dw", "ZZ99", "KN46zz", "KN46dwAA", "ĂN46",
        )) {
            assertFalse(locator, Maidenhead.isValid(locator))
            assertNull(locator, Maidenhead.toPosition(locator))
        }
    }
}
