package io.github.lilixp.utcradioclock.ui.dashboard

import io.github.lilixp.utcradioclock.domain.model.*
import io.github.lilixp.utcradioclock.domain.solar.SolarCalculator
import io.github.lilixp.utcradioclock.util.TimeFormatter
import org.junit.Assert.*
import org.junit.Test
import java.time.*
import java.util.Locale

class SunPresentationTest {
    private val date = LocalDate.parse("2026-10-06")
    private val utc = ZoneId.of("UTC")
    private val position = GeoPosition(46.9375, 28.291667)
    private fun instant(day: LocalDate, hour: Int, minute: Int = 0) = day.atTime(hour, minute).toInstant(ZoneOffset.UTC)
    private fun day(date: LocalDate) = SolarDay(date, instant(date, 12), instant(date, 6), instant(date, 18),
        instant(date, 5, 30), instant(date, 18, 30), null)
    private val days = (-2L..2L).map { day(date.plusDays(it)) }
    private fun next(hour: Int, minute: Int = 0) = nextSunEvent(instant(date, hour, minute), days, null)!!

    @Test fun beforeSunrise_countsToSunrise() {
        assertEquals(SunEvent.SUNRISE, next(5).event)
        assertEquals(Duration.ofHours(1), next(5).remaining)
    }
    @Test fun atSunrise_switchesToSunset() {
        assertEquals(SunEvent.SUNSET, next(6).event)
        assertEquals(Duration.ofHours(12), next(6).remaining)
    }
    @Test fun justBeforeAndAtSunset_switchesToRealNextSunrise() {
        val now = instant(date, 18).minusSeconds(20)
        assertEquals(Duration.ofSeconds(20), nextSunEvent(now, days, null)!!.remaining)
        assertEquals(SunEvent.SUNRISE, next(18).event)
        assertEquals(Duration.ofHours(12), next(18).remaining)
    }
    @Test fun afterSunset_andAcrossMidnight_countsToTomorrow() {
        assertEquals(Duration.ofHours(7), next(23).remaining)
        val afterMidnight = instant(date.plusDays(1), 0)
        assertEquals(Duration.ofHours(6), nextSunEvent(afterMidnight, days, null)!!.remaining)
    }
    @Test fun unorderedAndDuplicateDays_doNotChangeNextEvent() {
        assertEquals(next(19), nextSunEvent(instant(date, 19), days.reversed() + days, null))
    }
    @Test fun polarOrMissingEvents_doNotInventCountdowns() {
        assertNull(nextSunEvent(instant(date, 12), days, PolarCondition.MIDNIGHT_SUN))
        assertNull(nextSunEvent(instant(date, 12), days, PolarCondition.POLAR_NIGHT))
        assertNull(nextSunEvent(instant(date, 12), emptyList(), null))
    }
    private fun coverage(segments: List<SunSegment>) {
        assertTrue(segments.isNotEmpty())
        assertEquals(0f, segments.first().start, 0f)
        assertEquals(1f, segments.last().end, 0f)
        segments.forEach { assertTrue(it.start >= 0 && it.end <= 1 && it.end > it.start) }
        segments.zipWithNext().forEach { (a, b) -> assertEquals(a.end, b.start, 0f) }
    }
    @Test fun normalAxis_hasRealProportionsAndCompleteOrderedCoverage() {
        val segments = sunSegments(instant(date, 0), instant(date.plusDays(1), 0), days.reversed())
        coverage(segments)
        assertEquals(listOf(DayPhase.NIGHT, DayPhase.TWILIGHT, DayPhase.DAY, DayPhase.TWILIGHT, DayPhase.NIGHT), segments.map { it.phase })
        assertEquals(6f / 24, segments[2].start, .000001f)
        assertEquals(18f / 24, segments[2].end, .000001f)
    }
    @Test fun daylightCrossingMidnight_isClippedFromAdjacentSolarDays() {
        val remote = days.map { it.copy(sunrise = instant(it.date.minusDays(1), 20), sunset = instant(it.date, 8),
            solarNoon = instant(it.date, 2), civilDawn = instant(it.date.minusDays(1), 19, 30), civilDusk = instant(it.date, 8, 30)) }
        val segments = sunSegments(instant(date, 0), instant(date.plusDays(1), 0), remote)
        coverage(segments)
        assertEquals(DayPhase.DAY, segments.first().phase)
        assertEquals(8f / 24, segments.first().end, .000001f)
        assertEquals(DayPhase.DAY, segments.last().phase)
        assertEquals(20f / 24, segments.last().start, .000001f)
    }
    @Test fun dstSpring_axisHas23Hours_andUsesElapsedInstants() = dst(LocalDate.parse("2026-03-29"), 23)
    @Test fun dstAutumn_axisHas25Hours_andUsesElapsedInstants() = dst(LocalDate.parse("2026-10-25"), 25)
    private fun dst(date: LocalDate, hours: Long) {
        val zone = ZoneId.of("Europe/Chisinau")
        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()
        assertEquals(hours, Duration.between(start, end).toHours())
        val cache = SunSolarDays(SolarCalculator::calculate)
        val noon = date.atTime(12, 0).atZone(zone).toInstant()
        val result = sunPresentation(noon, zone, station(), cache.day(date, zone, position), cache.window(date, zone, position), TimeFormatter(Locale.US))
        coverage(result.segments)
        assertEquals(Duration.between(start, noon).toMillis().toFloat() / Duration.between(start, end).toMillis(), result.marker, .000001f)
        assertEquals(result.marker, result.midday, 0f)
        assertTrue("No fixed 24-hour geometry", result.marker != .5f)
    }
    @Test fun polarAxesAndTwilightWithoutSunrise_keepRealPhases() {
        for ((polar, phase) in listOf(PolarCondition.MIDNIGHT_SUN to DayPhase.DAY, PolarCondition.POLAR_NIGHT to DayPhase.NIGHT)) {
            val polarDays = days.map { it.copy(sunrise = null, sunset = null, civilDawn = null, civilDusk = null, polar = polar) }
            val segments = sunSegments(instant(date, 0), instant(date.plusDays(1), 0), polarDays)
            coverage(segments)
            assertEquals(listOf(phase), segments.map { it.phase })
        }
        val twilight = days.map { it.copy(sunrise = null, sunset = null, civilDawn = instant(it.date, 10),
            civilDusk = instant(it.date, 14), polar = PolarCondition.POLAR_NIGHT) }
        assertEquals(listOf(DayPhase.NIGHT, DayPhase.TWILIGHT, DayPhase.NIGHT),
            sunSegments(instant(date, 0), instant(date.plusDays(1), 0), twilight).map { it.phase })
    }
    @Test fun absentAxisData_hasNoInventedSegments() {
        assertTrue(sunSegments(instant(date, 0), instant(date.plusDays(1), 0), emptyList()).isEmpty())
        val incomplete = days.map { it.copy(sunrise = null, sunset = null, polar = null) }
        assertTrue(sunSegments(instant(date, 0), instant(date.plusDays(1), 0), incomplete).all { it.phase == null })
    }
    @Test fun whiteNight_withoutCivilEvents_isTwilightOutsideDay() {
        val white = days.map { it.copy(civilDawn = null, civilDusk = null) }
        assertEquals(listOf(DayPhase.TWILIGHT, DayPhase.DAY, DayPhase.TWILIGHT),
            sunSegments(instant(date, 0), instant(date.plusDays(1), 0), white).map { it.phase })
    }
    private fun station() = StationPosition(position, PositionOrigin.LOCATOR, "KN46dw", PositionSource.MANUAL)
    @Test fun provenance_usesActualOriginAndGpsStatus_notAutomaticModeAlone() {
        val now = instant(date, 12)
        assertEquals(SunPositionSource.MANUAL, sunPositionSource(station(), now))
        assertEquals(SunPositionSource.BACKUP_LOCATOR, sunPositionSource(station().copy(source = PositionSource.AUTOMATIC), now))
        val gps = station().copy(origin = PositionOrigin.GPS, source = PositionSource.AUTOMATIC,
            gps = GpsStatus.OK, fix = LocationFix(position, 15f, now, false))
        assertEquals(SunPositionSource.GPS, sunPositionSource(gps, now))
        assertEquals(SunPositionSource.LAST_GPS, sunPositionSource(gps.copy(gps = GpsStatus.LOCATION_OFF), now))
        assertEquals(SunPositionSource.LAST_GPS, sunPositionSource(gps, now.plusSeconds(600)))
    }
    @Test fun remoteStation_datesAreShownWhenEventsCrossLocalMidnight() {
        val zone = ZoneId.of("Pacific/Kiritimati")
        val remote = station().copy(position = GeoPosition(51.5, -.1), locator = "IO91wm")
        val cache = SunSolarDays(SolarCalculator::calculate)
        val today = cache.day(date, zone, remote.position!!)
        val now = date.atTime(12, 0).atZone(zone).toInstant()
        val result = sunPresentation(now, zone, remote, today, cache.window(date, zone, remote.position), TimeFormatter(Locale.US))
        assertNotNull(result.sunriseDate)
        assertTrue(result.morningTwilight!!.contains(result.sunriseDate!!))
        assertEquals("Pacific/Kiritimati", result.zone)
        assertEquals("12:00", result.currentTime)
        assertEquals(today.dayLength, result.dayLength)
        coverage(result.segments)
    }
    @Test fun minuteRoundingAcrossMidnight_keepsDisplayedTimeAndDateConsistent() {
        val today = day(date).copy(sunset = instant(date.plusDays(1), 0).minusSeconds(15))
        val result = sunPresentation(instant(date, 12), utc, station(), today, listOf(today), TimeFormatter(Locale.UK))
        assertEquals("7 Oct 2026", result.sunsetDate)
        assertTrue(result.eveningTwilight!!.contains("7 Oct 2026"))
    }
    @Test fun cachedAstronomy_isReusedOnTicksAndRefreshedForRelevantInputs() {
        var calls = 0
        val cache = SunSolarDays { date, zone, coordinates -> calls++; SolarCalculator.calculate(date, zone, coordinates) }
        repeat(120) { cache.window(date, utc, position) }
        assertEquals(5, calls)
        cache.window(date.plusDays(1), utc, position)
        assertEquals(6, calls)
        cache.window(date, ZoneId.of("Europe/Chisinau"), position)
        assertEquals(11, calls)
        cache.window(date, utc, GeoPosition(51.5, -.1))
        assertEquals(16, calls)
    }
}
