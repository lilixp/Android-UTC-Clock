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
        assertEquals("1 octombrie 2026", formatter.localDate(instant, ZoneId.of("Asia/Tokyo")))
        assertEquals("30 septembrie 2026", formatter.utcDate(instant))
    }

    @Test
    fun timeZoneShowsNameAndCurrentOffset() {
        assertEquals("Europe/Chisinau · UTC+02:00", formatter.timeZone(winter, chisinau))
        assertEquals("UTC", formatter.timeZone(winter, ZoneOffset.UTC))
    }

    @Test
    fun unknownValuesStayNull() {
        assertNull(formatter.eventTime(null, chisinau))
        assertNull(formatter.duration(null))
    }

    @Test
    fun eventTimeAndDuration() {
        assertEquals("10:05", formatter.eventTime(winter, chisinau))
        assertEquals("11:52", formatter.duration(Duration.ofHours(11).plusMinutes(52)))
    }
}
