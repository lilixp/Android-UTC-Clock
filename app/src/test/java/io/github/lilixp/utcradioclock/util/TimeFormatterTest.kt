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
