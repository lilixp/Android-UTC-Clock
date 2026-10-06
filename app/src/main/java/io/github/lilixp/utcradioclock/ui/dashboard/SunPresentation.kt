package io.github.lilixp.utcradioclock.ui.dashboard

import io.github.lilixp.utcradioclock.domain.model.*
import io.github.lilixp.utcradioclock.data.location.PositionRepository
import io.github.lilixp.utcradioclock.util.TimeFormatter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/** Presentation of the existing astronomical events; all geometry uses elapsed time, not wall times. */
data class SunSegment(val start: Float, val end: Float, val phase: DayPhase?,
    val startInstant: Instant? = null, val endInstant: Instant? = null)
data class SunPhaseRange(val phase: DayPhase?, val start: String, val end: String)
enum class SunEvent { SUNRISE, SUNSET }
data class SunCountdown(val event: SunEvent, val remaining: Duration)
enum class SunPositionSource { MANUAL, BACKUP_LOCATOR, GPS, LAST_GPS }
data class SunPresentation(
    val date: String,
    val locator: String?,
    val source: SunPositionSource,
    val zone: String,
    val sunriseDate: String?,
    val sunsetDate: String?,
    val noon: String?,
    val morningTwilight: String?,
    val eveningTwilight: String?,
    val dayLength: Duration,
    val segments: List<SunSegment>,
    val marker: Float,
    val currentTime: String,
    val midday: Float,
    val countdown: SunCountdown?,
    val phaseRanges: List<SunPhaseRange> = emptyList(),
)

/** Small shared solar-result cache: the centre day and its neighbours use the same calculator.
 * Adjacent days cover events across local midnight and a remote station's solar noon. No per-tick astronomy.
 */
internal class SunSolarDays(private val calculate: (LocalDate, ZoneId, GeoPosition) -> SolarDay) {
    private data class Key(val date: LocalDate, val zone: ZoneId, val position: GeoPosition)
    private val cache = linkedMapOf<Key, SolarDay>()
    fun day(date: LocalDate, zone: ZoneId, position: GeoPosition): SolarDay {
        val key = Key(date, zone, position)
        return cache[key] ?: calculate(date, zone, position).also {
            cache[key] = it
            if (cache.size > 12) cache.remove(cache.keys.first())
        }
    }
    fun window(date: LocalDate, zone: ZoneId, position: GeoPosition): List<SolarDay> =
        (-2L..2L).map { day(date.plusDays(it), zone, position) }
}

internal fun sunPositionSource(station: StationPosition, now: Instant): SunPositionSource =
    if (station.origin == PositionOrigin.GPS) {
        val age = station.fix?.let { Duration.between(it.time, now) }
        if (station.gps != GpsStatus.OK || age?.let { it.isNegative || it >= PositionRepository.RECENT } == true)
            SunPositionSource.LAST_GPS else SunPositionSource.GPS
    } else if (station.source == PositionSource.AUTOMATIC) SunPositionSource.BACKUP_LOCATOR
    else SunPositionSource.MANUAL

internal fun nextSunEvent(now: Instant, days: List<SolarDay>, polar: PolarCondition?): SunCountdown? {
    if (polar != null) return null
    return days.flatMap { day -> listOfNotNull(
        day.sunrise?.let { it to SunEvent.SUNRISE }, day.sunset?.let { it to SunEvent.SUNSET },
    ) }.filter { it.first > now }.minByOrNull { it.first }
        ?.let { SunCountdown(it.second, Duration.between(now, it.first)) }
}

/** Complete, ordered coverage of [start, end), clipped to the local calendar day (23/24/25 hours).
 * The nearest solar noon selects the applicable polar/white-night state. Explicit daylight and
 * twilight spans from every adjacent day take precedence, including spans crossing local midnight.
 */
internal fun sunSegments(start: Instant, end: Instant, days: List<SolarDay>): List<SunSegment> {
    if (days.isEmpty() || end <= start) return emptyList()
    val noons = days.map { it.solarNoon }.sorted()
    val boundaries = (listOf(start, end) + days.flatMap {
        listOfNotNull(it.sunrise, it.sunset, it.civilDawn, it.civilDusk)
    } + noons.zipWithNext { a, b -> a.plusMillis(Duration.between(a, b).toMillis() / 2) })
        .filter { it >= start && it <= end }.distinct().sorted()
    fun between(t: Instant, a: Instant?, b: Instant?) = a != null && b != null && t >= a && t < b
    fun phase(t: Instant): DayPhase? {
        if (days.any { between(t, it.sunrise, it.sunset) }) return DayPhase.DAY
        if (days.any { between(t, it.civilDawn, it.sunrise) || between(t, it.sunset, it.civilDusk) ||
            (it.polar == PolarCondition.POLAR_NIGHT && between(t, it.civilDawn, it.civilDusk)) }) return DayPhase.TWILIGHT
        return days.minBy { abs(Duration.between(it.solarNoon, t).toMillis()) }.phaseAt(t)
    }
    val length = Duration.between(start, end).toMillis().toDouble()
    fun fraction(t: Instant) = (Duration.between(start, t).toMillis() / length).toFloat().coerceIn(0f, 1f)
    val result = mutableListOf<SunSegment>()
    for ((a, b) in boundaries.zipWithNext()) {
        val segment = SunSegment(fraction(a), fraction(b), phase(a.plusMillis(Duration.between(a, b).toMillis() / 2)), a, b)
        val previous = result.lastOrNull()
        if (previous != null && previous.phase == segment.phase) result[result.lastIndex] = previous.copy(end = segment.end, endInstant = b)
        else result += segment
    }
    return result
}

internal fun sunPresentation(
    now: Instant, zone: ZoneId, station: StationPosition, today: SolarDay,
    days: List<SolarDay>, format: TimeFormatter,
): SunPresentation {
    val date = now.atZone(zone).toLocalDate()
    val start = date.atStartOfDay(zone).toInstant()
    val end = date.plusDays(1).atStartOfDay(zone).toInstant()
    fun fraction(t: Instant) = (Duration.between(start, t).toMillis().toDouble() /
        Duration.between(start, end).toMillis()).toFloat().coerceIn(0f, 1f)
    fun otherDate(event: Instant?) = format.eventDate(event, zone)
        ?.takeIf { it != today.date }?.let(format::shortDate)
    fun event(event: Instant?) = format.eventTime(event, zone)?.let { time ->
        otherDate(event)?.let { "$time ($it)" } ?: time
    }
    fun interval(a: Instant?, b: Instant?) = if (a != null && b != null) "${event(a)}–${event(b)}" else null
    val segments = sunSegments(start, end, days)
    return SunPresentation(
        date = format.solarDate(today.date),
        locator = station.locator, source = sunPositionSource(station, now), zone = zone.id,
        sunriseDate = otherDate(today.sunrise), sunsetDate = otherDate(today.sunset),
        noon = event(today.solarNoon), morningTwilight = interval(today.civilDawn, today.sunrise),
        eveningTwilight = interval(today.sunset, today.civilDusk), dayLength = today.dayLength,
        segments = segments, marker = fraction(now),
        currentTime = format.hoursMinutes(now.atZone(zone).toLocalDateTime()),
        midday = fraction(date.atTime(12, 0).atZone(zone).toInstant()),
        countdown = nextSunEvent(now, days, today.polar),
        phaseRanges = segments.map { segment -> SunPhaseRange(segment.phase,
            if (segment.startInstant == start) "00:00" else format.eventTime(segment.startInstant, zone).orEmpty(),
            if (segment.endInstant == end) "24:00" else format.eventTime(segment.endInstant, zone).orEmpty()) },
    )
}
