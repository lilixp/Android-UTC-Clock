package io.github.lilixp.utcradioclock.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

class TimeFormatterTest {

    private val formatter = TimeFormatter(Locale.forLanguageTag("ro"))
    private val winter = Instant.parse("2026-01-15T08:05:09Z")
    private val chisinau = ZoneId.of("Europe/Chisinau")

    @Test
    fun timesAre24HourWithSeconds() {
        assertEquals("08:05:09", formatter.utcTime(winter))
        assertEquals("10:05:09", formatter.localTime(winter, chisinau)) // winter time: UTC+2
    }

    @Test
    fun alwaysTwoDigitsAnd24Hours() {
        val lateEvening = Instant.parse("2026-01-15T23:04:05Z")
        assertEquals("23:04:05", formatter.utcTime(lateEvening)) // not 11:04:05 PM
        assertEquals("00:00:00", formatter.utcTime(Instant.parse("2026-01-15T00:00:00Z")))
        // The same HH:mm:ss in English: no AM/PM, whatever the phone's language
        assertEquals("23:04:05", TimeFormatter(Locale.US).utcTime(lateEvening))
    }

    @Test
    fun oneInstantInSeveralTimeZones() {
        val instant = Instant.parse("2026-09-30T15:42:31Z")
        assertEquals("15:42:31", formatter.utcTime(instant))
        assertEquals("18:42:31", formatter.localTime(instant, chisinau)) // UTC+3 (summer)
        assertEquals("11:42:31", formatter.localTime(instant, ZoneId.of("America/New_York"))) // UTC-4
        assertEquals("21:12:31", formatter.localTime(instant, ZoneId.of("Asia/Kolkata"))) // UTC+5:30
        assertEquals("00:42:31", formatter.localTime(instant, ZoneId.of("Asia/Tokyo"))) // UTC+9, next day
        assertEquals("Joi, 1 octombrie 2026", formatter.localDate(instant, ZoneId.of("Asia/Tokyo")))
        assertEquals("Miercuri, 30 septembrie 2026", formatter.utcDate(instant))
    }

    @Test
    fun timeZoneNameOffsetAndAbbreviation() {
        assertEquals("Europe/Chisinau", formatter.zoneName(chisinau))
        assertEquals("UTC+02:00", formatter.zoneOffset(winter, chisinau)) // winter: EET
        assertEquals("EET", formatter.zoneAbbreviation(winter, chisinau))
        assertEquals("UTC+03:00", formatter.zoneOffset(Instant.parse("2026-07-01T12:00:00Z"), chisinau))
        assertEquals("EEST", formatter.zoneAbbreviation(Instant.parse("2026-07-01T12:00:00Z"), chisinau))
        assertEquals("UTC", formatter.zoneOffset(winter, ZoneOffset.UTC))
        assertNull(formatter.zoneName(ZoneOffset.ofHours(2))) // only an offset: no name
        assertNull(formatter.zoneAbbreviation(winter, ZoneOffset.ofHours(2))) // nor an abbreviation
    }

    @Test
    fun datesWithTheDayOfTheWeek_capitalisedInRomanian() {
        assertEquals("Vineri, 2 octombrie 2026", formatter.utcDate(Instant.parse("2026-10-02T05:12:35Z")))
        assertEquals("Friday, 2 October 2026", TimeFormatter(Locale.ENGLISH).utcDate(Instant.parse("2026-10-02T05:12:35Z")))
        assertEquals("25 oct. 2026", formatter.shortDate(java.time.LocalDate.parse("2026-10-25")))
        assertEquals("04:00", formatter.hoursMinutes(java.time.LocalDateTime.parse("2026-10-25T04:00:00")))
    }

    @Test
    fun unknownValuesStayNull() {
        assertNull(formatter.eventTime(null, chisinau))
        assertNull(formatter.duration(null))
    }

    @Test
    fun eventTimesAreRoundedToTheNearestMinute() {
        assertEquals("10:05", formatter.eventTime(winter, chisinau)) // 10:05:09
        assertEquals("10:06", formatter.eventTime(Instant.parse("2026-01-15T08:05:30Z"), chisinau))
        assertEquals("00:00", formatter.eventTime(Instant.parse("2026-01-15T21:59:45Z"), chisinau)) // into the next day
        assertEquals(java.time.LocalDate.parse("2026-01-16"), formatter.eventDate(Instant.parse("2026-01-15T21:59:45Z"), chisinau))
        assertNull(formatter.eventDate(null, chisinau))
    }

    @Test fun solarDateIsLocalizedWithoutAWeekday() {
        val date = java.time.LocalDate.parse("2026-10-06")
        assertEquals("6 octombrie 2026", formatter.solarDate(date))
        assertEquals("6 October 2026", TimeFormatter(Locale.UK).solarDate(date))
    }

    @Test
    fun dayLengthInHoursAndMinutes() {
        assertEquals("11h 52m", formatter.duration(Duration.ofHours(11).plusMinutes(52)))
        assertEquals("11h 44m", formatter.duration(Duration.parse("PT11H44M23S")))
        assertEquals("8h 31m", formatter.duration(Duration.parse("PT8H30M30S")))
        assertEquals("24h 00m", formatter.duration(Duration.ofHours(24)))
        assertEquals("0h 00m", formatter.duration(Duration.ZERO))
    }

    // ---- Coordinates and the GPS time ----

    @Test
    fun coordinates_commaInRomanian_pointInEnglish_fourDecimals() {
        assertEquals("46,9612° N", formatter.latitude(46.9612))
        assertEquals("28,3041° E", formatter.longitude(28.3041))
        val english = TimeFormatter(Locale.ENGLISH)
        assertEquals("46.9612° N", english.latitude(46.9612))
        assertEquals("28.2917° E", english.longitude(28.291667))
    }

    @Test
    fun coordinates_southAndWestWithoutMinusSign() {
        assertEquals("33,8688° S", formatter.latitude(-33.8688))
        assertEquals("0,1278° W", formatter.longitude(-0.1278))
        assertEquals("0,0000° N", formatter.latitude(0.0))
    }

    @Test
    fun altitudeDeclinationDistanceAndBearing_forTheLocationScreen() {
        assertEquals("185 m", formatter.altitude(184.6))
        assertEquals("-12 m", formatter.altitude(-12.2))
        assertEquals("6,4° E", formatter.declination(6.43))
        assertEquals("13,1° W", formatter.declination(-13.07))
        assertEquals("2,8 km", formatter.distance(2.76))
        assertEquals("35 km", formatter.distance(35.4))
        assertEquals("238°", formatter.bearing(237.6))
        assertEquals("0°", formatter.bearing(359.7)) // a full turn is north again
        val english = TimeFormatter(Locale.UK)
        assertEquals("6.4° E", english.declination(6.43))
        assertEquals("2.8 km", english.distance(2.76))
    }

    @Test
    fun accuracyInWholeMetres() {
        assertEquals("±12 m", formatter.accuracy(12.4f))
        assertEquals("±2500 m", formatter.accuracy(2500f))
    }

    @Test
    fun localStamp_timeOnTheSameLocalDay_dateOtherwise() {
        val now = Instant.parse("2026-09-30T15:42:31Z")
        assertEquals("18:40", formatter.localStamp(Instant.parse("2026-09-30T15:40:00Z"), chisinau, now))
        assertEquals("29 sept. 18:40", formatter.localStamp(Instant.parse("2026-09-29T15:40:00Z"), chisinau, now))
    }
}
