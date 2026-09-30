package io.github.lilixp.utcradioclock.domain.solar

import io.github.lilixp.utcradioclock.domain.model.GeoPosition
import io.github.lilixp.utcradioclock.domain.model.PolarCondition
import io.github.lilixp.utcradioclock.domain.model.SolarDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Fixed places and dates against reference times computed once on the PC, independently of the app's
 * code: the exact moments at which the Sun's centre is at -0.833° (sunrise/sunset), -6° (civil
 * twilight) or due south/north (solar noon), found by bisection on the NOAA solar position formulas
 * evaluated at each instant. The app's shortcut (NOAA's hour-angle formula, refined once) must agree
 * within [TOLERANCE]. The Python library "astral" 3.2 agrees with these within 2 minutes (at 65° N it is
 * the one that is 1¾ minutes off). Nothing here depends on the PC's clock.
 */
class SolarCalculatorTest {

    private val kn46dw = GeoPosition(46.9375, 28.291667) // centre of KN46dw
    private val chisinau = ZoneId.of("Europe/Chisinau")

    private fun sun(date: String, position: GeoPosition, zone: String): SolarDay =
        SolarCalculator.calculate(LocalDate.parse(date), ZoneId.of(zone), position)

    /** [expected] is local time with offset, e.g. "2026-09-30T07:04:15+03:00". */
    private fun assertTime(what: String, expected: String, actual: Instant?) {
        assertNotNull(what, actual)
        val difference = Duration.between(ZonedDateTime.parse(expected).toInstant(), actual).abs()
        assertTrue("$what: $actual, expected $expected (±$TOLERANCE)", difference <= TOLERANCE)
    }

    private fun assertLength(expected: Duration, actual: Duration) =
        assertTrue("day length $actual, expected $expected", (actual - expected).abs() <= TOLERANCE)

    @Test
    fun kn46dw_summerTime() {
        val day = sun("2026-09-30", kn46dw, "Europe/Chisinau") // UTC+3
        assertTime("sunrise", "2026-09-30T07:03:59+03:00", day.sunrise)
        assertTime("sunset", "2026-09-30T18:48:54+03:00", day.sunset)
        assertTime("solar noon", "2026-09-30T12:56:51+03:00", day.solarNoon)
        assertTime("civil dawn", "2026-09-30T06:33:41+03:00", day.civilDawn)
        assertTime("civil dusk", "2026-09-30T19:19:09+03:00", day.civilDusk)
        assertLength(Duration.parse("PT11H44M55S"), day.dayLength)
        assertNull(day.polar)
    }

    @Test
    fun kn46dw_standardTime_winterSolstice() {
        val day = sun("2026-12-21", kn46dw, "Europe/Chisinau") // UTC+2
        assertTime("sunrise", "2026-12-21T07:49:21+02:00", day.sunrise)
        assertTime("sunset", "2026-12-21T16:20:22+02:00", day.sunset)
        assertTime("solar noon", "2026-12-21T12:04:52+02:00", day.solarNoon)
        assertTime("civil dawn", "2026-12-21T07:13:52+02:00", day.civilDawn)
        assertTime("civil dusk", "2026-12-21T16:55:51+02:00", day.civilDusk)
        assertLength(Duration.parse("PT8H31M1S"), day.dayLength)
    }

    @Test
    fun london_westOfGreenwich_summerSolstice() {
        val day = sun("2026-06-21", GeoPosition(51.520833, -0.125), "Europe/London") // BST, UTC+1
        assertTime("sunrise", "2026-06-21T04:42:59+01:00", day.sunrise)
        assertTime("sunset", "2026-06-21T21:21:38+01:00", day.sunset)
        assertTime("solar noon", "2026-06-21T13:02:19+01:00", day.solarNoon)
        assertTime("civil dawn", "2026-06-21T03:55:11+01:00", day.civilDawn)
        assertTime("civil dusk", "2026-06-21T22:09:26+01:00", day.civilDusk)
        assertLength(Duration.parse("PT16H38M39S"), day.dayLength)
    }

    @Test
    fun newYork_westernHemisphere_equinox() {
        val day = sun("2026-03-20", GeoPosition(40.770833, -73.958333), "America/New_York") // EDT, UTC-4
        assertTime("sunrise", "2026-03-20T06:59:05-04:00", day.sunrise)
        assertTime("sunset", "2026-03-20T19:08:00-04:00", day.sunset)
        assertTime("solar noon", "2026-03-20T13:03:12-04:00", day.solarNoon)
        assertLength(Duration.parse("PT12H8M55S"), day.dayLength)
    }

    @Test
    fun tokyo_sunriseIsOnThePreviousUtcDate() {
        val day = sun("2026-09-30", GeoPosition(35.5, 139.0), "Asia/Tokyo") // UTC+9
        assertTime("sunrise", "2026-09-30T05:37:43+09:00", day.sunrise)
        assertTime("sunset", "2026-09-30T17:29:58+09:00", day.sunset)
        assertTime("solar noon", "2026-09-30T11:34:07+09:00", day.solarNoon)
        // The local date is 30 September, but in UTC the Sun rises on 29 September
        assertEquals(LocalDate.parse("2026-09-29"), day.sunrise!!.atZone(ZoneId.of("UTC")).toLocalDate())
        assertEquals(LocalDate.parse("2026-09-30"), day.date)
    }

    @Test
    fun sydney_southernHemisphere_summerTime() {
        val day = sun("2026-12-21", GeoPosition(-33.9375, 151.208333), "Australia/Sydney") // AEDT, UTC+11
        assertTime("sunrise", "2026-12-21T05:40:27+11:00", day.sunrise)
        assertTime("sunset", "2026-12-21T20:05:37+11:00", day.sunset)
        assertTime("solar noon", "2026-12-21T12:53:02+11:00", day.solarNoon)
        assertTime("civil dawn", "2026-12-21T05:11:15+11:00", day.civilDawn)
        assertTime("civil dusk", "2026-12-21T20:34:49+11:00", day.civilDusk)
        assertLength(Duration.parse("PT14H25M10S"), day.dayLength)
    }

    @Test
    fun sameInstantsWhateverTheDisplayZone_onlyTheDayMatters() {
        // The events are moments in time; the zone only decides which local day is meant
        val inChisinau = sun("2026-09-30", kn46dw, "Europe/Chisinau")
        val inUtc = SolarCalculator.calculate(LocalDate.parse("2026-09-30"), ZoneId.of("UTC"), kn46dw)
        assertEquals(inChisinau.sunrise, inUtc.sunrise)
        assertEquals(inChisinau.sunset, inUtc.sunset)
    }

    @Test
    fun whiteNight_65North_sunsetAfterMidnight_noCivilTwilight() {
        val day = sun("2026-06-21", GeoPosition(65.0, 25.5), "Europe/Helsinki") // EEST, UTC+3
        assertTime("sunrise", "2026-06-21T02:18:35+03:00", day.sunrise)
        // The sunset of this solar day comes after local midnight, on 22 June
        assertTime("sunset", "2026-06-22T00:21:00+03:00", day.sunset)
        assertTime("solar noon", "2026-06-21T13:19:48+03:00", day.solarNoon)
        assertNull("the Sun never goes 6° below the horizon", day.civilDawn)
        assertNull(day.civilDusk)
        assertNull(day.polar)
        assertTrue(day.dayLength > Duration.ofHours(21) && day.dayLength < Duration.ofHours(23))
    }

    @Test
    fun midnightSun_78North_noSunriseNoSunset() {
        val day = sun("2026-06-21", GeoPosition(78.5, 15.0), "Arctic/Longyearbyen")
        assertEquals(PolarCondition.MIDNIGHT_SUN, day.polar)
        assertNull(day.sunrise)
        assertNull(day.sunset)
        assertEquals(Duration.ofHours(24), day.dayLength)
        assertTime("solar noon", "2026-06-21T13:01:48+02:00", day.solarNoon)
    }

    @Test
    fun polarNight_78North_noSunriseNoSunset() {
        val day = sun("2026-12-21", GeoPosition(78.5, 15.0), "Arctic/Longyearbyen")
        assertEquals(PolarCondition.POLAR_NIGHT, day.polar)
        assertNull(day.sunrise)
        assertNull(day.sunset)
        assertEquals(Duration.ZERO, day.dayLength)
        assertNull("at 78.5° the Sun stays deeper than 6° all day", day.civilDawn)
    }

    @Test
    fun extremeLatitudesAndLongitudes_neverCrash() {
        val zones = listOf("UTC", "Pacific/Kiritimati", "Pacific/Pago_Pago", "Europe/Chisinau")
        for (latitude in listOf(-90.0, -89.99, -66.5, 0.0, 66.5, 89.99, 90.0)) {
            for (longitude in listOf(-180.0, -179.9, -73.9, 0.0, 28.3, 179.9, 180.0)) {
                for (date in listOf("2026-03-20", "2026-06-21", "2026-12-21")) {
                    for (zone in zones) {
                        val day = sun(date, GeoPosition(latitude, longitude), zone)
                        assertEquals(LocalDate.parse(date), day.date)
                        // Either both sunrise and sunset, or a polar day or night: never half-invented
                        assertEquals(day.polar == null, day.sunrise != null && day.sunset != null)
                        // Solar noon is always on the requested local day
                        assertEquals(LocalDate.parse(date), day.solarNoon.atZone(ZoneId.of(zone)).toLocalDate())
                    }
                }
            }
        }
    }

    @Test
    fun orderOfTheDay() {
        val day = sun("2026-09-30", kn46dw, "Europe/Chisinau")
        assertTrue(day.civilDawn!! < day.sunrise && day.sunrise!! < day.solarNoon)
        assertTrue(day.solarNoon < day.sunset && day.sunset!! < day.civilDusk)
    }

    private companion object {
        val TOLERANCE: Duration = Duration.ofSeconds(30)
    }
}
