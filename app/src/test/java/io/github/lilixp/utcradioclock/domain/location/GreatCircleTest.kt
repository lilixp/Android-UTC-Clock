package io.github.lilixp.utcradioclock.domain.location

import io.github.lilixp.utcradioclock.domain.model.GeoPosition
import org.junit.Assert.assertEquals
import org.junit.Test

class GreatCircleTest {

    private val london = GeoPosition(51.5074, -0.1278)
    private val paris = GeoPosition(48.8566, 2.3522)

    @Test
    fun londonToParis_about344km_southEast() {
        assertEquals(343.5, GreatCircle.distanceKm(london, paris), 1.0)
        assertEquals(148.1, GreatCircle.bearingDegrees(london, paris), 0.5)
    }

    @Test
    fun theWayBack_otherBearing_sameDistance() {
        assertEquals(GreatCircle.distanceKm(london, paris), GreatCircle.distanceKm(paris, london), 1e-9)
        assertEquals(330.0, GreatCircle.bearingDegrees(paris, london), 0.5)
    }

    @Test
    fun alongTheEquator_oneDegreeIs111km_east() {
        val a = GeoPosition(0.0, 10.0)
        val b = GeoPosition(0.0, 11.0)
        assertEquals(111.19, GreatCircle.distanceKm(a, b), 0.05)
        assertEquals(90.0, GreatCircle.bearingDegrees(a, b), 1e-6)
        assertEquals(270.0, GreatCircle.bearingDegrees(b, a), 1e-6)
    }

    @Test
    fun northAndSouth_andNoDistance() {
        val a = GeoPosition(46.0, 28.0)
        assertEquals(0.0, GreatCircle.bearingDegrees(a, GeoPosition(47.0, 28.0)), 1e-6)
        assertEquals(180.0, GreatCircle.bearingDegrees(a, GeoPosition(45.0, 28.0)), 1e-6)
        assertEquals(0.0, GreatCircle.distanceKm(a, a), 1e-9)
    }

    @Test
    fun shortDistance_aroundBoghiceni() {
        // The GPS position at Boghiceni and the centre of KN46dw: about 2,8 km, roughly south-south-west
        val here = GeoPosition(46.9612, 28.3041)
        val home = GeoPosition(46.9375, 28.2917)
        assertEquals(2.8, GreatCircle.distanceKm(here, home), 0.1)
        assertEquals(200.0, GreatCircle.bearingDegrees(here, home), 2.0)
    }
}
