package io.github.lilixp.utcradioclock.domain.location

import io.github.lilixp.utcradioclock.domain.model.GeoPosition
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

/** Android's own World Magnetic Model, run as on the phone (Robolectric uses the real implementation). */
@RunWith(RobolectricTestRunner::class)
class MagneticDeclinationTest {

    private val october2026 = Instant.parse("2026-10-06T12:00:00Z")

    @Test
    fun chisinau_aboutSevenDegreesEast() {
        val declination = MagneticDeclination.at(GeoPosition(46.9375, 28.2917), 185.0, october2026)
        assertTrue("$declination", declination in 6.0..9.0)
    }

    @Test
    fun newYork_west_negative() {
        val declination = MagneticDeclination.at(GeoPosition(40.71, -74.01), null, october2026)
        assertTrue("$declination", declination in -15.0..-11.0)
    }

    @Test
    fun heightBarelyMatters() {
        val place = GeoPosition(46.9375, 28.2917)
        val ground = MagneticDeclination.at(place, 0.0, october2026)
        val hill = MagneticDeclination.at(place, 1000.0, october2026)
        assertTrue("$ground vs $hill", kotlin.math.abs(ground - hill) < 0.1)
    }
}
