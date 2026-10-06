package io.github.lilixp.utcradioclock.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lilixp.utcradioclock.data.location.PositionRepository
import io.github.lilixp.utcradioclock.data.propagation.PropagationState
import io.github.lilixp.utcradioclock.data.settings.SettingsRepository
import io.github.lilixp.utcradioclock.data.time.ClockRepository
import io.github.lilixp.utcradioclock.domain.model.BandGroup
import io.github.lilixp.utcradioclock.domain.model.ClockReading
import io.github.lilixp.utcradioclock.domain.model.DayPhase
import io.github.lilixp.utcradioclock.domain.model.GeoPosition
import io.github.lilixp.utcradioclock.domain.model.GpsStatus
import io.github.lilixp.utcradioclock.domain.model.LocalDay
import io.github.lilixp.utcradioclock.domain.model.PolarCondition
import io.github.lilixp.utcradioclock.domain.model.PositionOrigin
import io.github.lilixp.utcradioclock.domain.model.PositionSource
import io.github.lilixp.utcradioclock.domain.model.SolarDay
import io.github.lilixp.utcradioclock.domain.model.StationIdentity
import io.github.lilixp.utcradioclock.domain.model.StationPosition
import io.github.lilixp.utcradioclock.domain.location.GreatCircle
import io.github.lilixp.utcradioclock.domain.location.MagneticDeclination
import io.github.lilixp.utcradioclock.domain.location.Maidenhead
import io.github.lilixp.utcradioclock.domain.propagation.IndexScales
import io.github.lilixp.utcradioclock.domain.propagation.OfflinePropagationCalculator
import io.github.lilixp.utcradioclock.domain.solar.SolarCalculator
import io.github.lilixp.utcradioclock.util.TimeFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class DashboardViewModel(
    private val clock: ClockRepository,
    private val settings: SettingsRepository,
    private val positions: PositionRepository,
    /** N0NBH data with its cache ([io.github.lilixp.utcradioclock.data.propagation.PropagationRepository.updates]). */
    propagation: Flow<PropagationState>,
    /** The solar calculation; tests pass their own to count how often it runs. */
    private val solar: (LocalDate, ZoneId, GeoPosition) -> SolarDay = SolarCalculator::calculate,
    /** The magnetic declination (degrees, east positive); tests pass their own (no Android model there). */
    private val magneticDeclination: (GeoPosition, Double?, Instant) -> Double = MagneticDeclination::at,
    /** Read on every tick: the ViewModel survives a language change, the texts must follow it. */
    private val locale: () -> Locale,
) : ViewModel() {

    private var formatter = TimeFormatter(locale())

    /** Bounded cache of solar days, including neighbours needed only by the Sun presentation. */
    private val solarDays = SunSolarDays(solar)

    /** The SUN card and the solar day behind it (null without a position). */
    private data class Sun(val ui: SunUiState, val day: SolarDay?)

    /**
     * What the SUN card depends on: the position, or why there is none. The GPS status is kept only
     * while there is no position, so a new GPS status alone never reaches the card or the calculation.
     */
    private data class SunInput(
        val position: GeoPosition?,
        val source: PositionSource,
        val gps: GpsStatus?,
        val invalidLocator: String?,
    )

    private fun sunInput(station: StationPosition) = SunInput(
        position = station.position,
        source = station.source,
        gps = station.gps.takeIf { station.position == null },
        invalidLocator = station.invalidLocator,
    )

    /**
     * The SUN card. It depends on the local day, the time zone and the position only, so it is worked
     * out when one of them changes (at local midnight, on a time zone change, on a new locator or GPS
     * position), never on the 1 s clock ticks. Without a position it says why, from the same
     * [StationPosition] the LOCATION card shows.
     */
    private val sun: Flow<Sun> = combine(
        clock.localDays(),
        positions.state.map(::sunInput).distinctUntilChanged(),
        ::sunState,
    ).distinctUntilChanged()

    val uiState: StateFlow<DashboardUiState> =
        // positions.updates() asks the phone for its position, only while the dashboard is collected
        combine(clock.ticks(), settings.station, sun, propagation, positions.updates(), ::toUiState)
            .stateIn(
                scope = viewModelScope,
                // Keeps ticking through a screen rotation, stops 5 s after the dashboard is no longer shown
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = toUiState(
                    clock.current(),
                    settings.station.value,
                    sunState(clock.currentDay(), sunInput(positions.currentState())),
                    PropagationState(PropagationState.Status.LOADING),
                    positions.currentState(),
                ),
            )

    private fun toUiState(
        reading: ClockReading,
        station: StationIdentity,
        sun: Sun,
        propagation: PropagationState,
        position: StationPosition,
    ): DashboardUiState {
        val (instant, zone) = reading
        val format = formatterFor(locale())
        val utcDate = format.utcDate(instant)
        val localDate = format.localDate(instant, zone)
        // Only the Sun presentation follows each existing clock tick. Reuse cached astronomy;
        // read the effective station, including fallback provenance, without starting another GPS flow.
        val displayedSun = sunState(LocalDay(instant.atZone(zone).toLocalDate(), zone), sunInput(position))
        val sunPresentation = displayedSun.day?.let { today -> position.position?.let { coordinates ->
            sunPresentation(instant, zone, position, today, solarDays.window(today.date, zone, coordinates), format)
        } }
        return DashboardUiState(
            utcDate = utcDate,
            utcTime = format.utcTime(instant),
            localTime = format.localTime(instant, zone),
            localDate = localDate.takeIf { it != utcDate },
            zone = zoneState(reading, format),
            callsign = station.callsign.ifEmpty { null },
            sun = displayedSun.ui.copy(presentation = sunPresentation),
            propagation = propagationState(propagation, reading, sun.day, format),
            location = locationState(position, station, reading, format),
            // From GPS in automatic mode, otherwise as entered in Settings
            locator = position.locator,
        )
    }

    /**
     * The time zone of the phone at this tick (so a zone change or a summer/winter change shows at once),
     * and its next clock change from the zone's own rules (java.time), nothing fixed in the code.
     */
    private fun zoneState(reading: ClockReading, format: TimeFormatter): ZoneUi {
        val (instant, zone) = reading
        return ZoneUi(
            name = format.zoneName(zone),
            abbreviation = format.zoneAbbreviation(instant, zone),
            offset = format.zoneOffset(instant, zone),
            nextChange = zone.rules.nextTransition(instant)?.let { change ->
                ClockChangeUi(
                    date = format.shortDate(change.dateTimeBefore.toLocalDate()),
                    from = format.hoursMinutes(change.dateTimeBefore),
                    to = format.hoursMinutes(change.dateTimeAfter),
                    toWinter = change.isOverlap, // the clocks go back
                )
            },
        )
    }

    /**
     * The LOCATION screen: the position used (GPS or the locator's centre), where it came from, and what a
     * radio amateur needs about it: the 8-character locator and height of a GPS position, the magnetic
     * declination, and, when portable, the distance and azimut to the home QTH (the locator in Settings).
     */
    private fun locationState(
        position: StationPosition,
        station: StationIdentity,
        reading: ClockReading,
        format: TimeFormatter,
    ): LocationUiState {
        val coordinates = position.position
        val fix = position.fix.takeIf { position.origin == PositionOrigin.GPS }
        return LocationUiState(
            latitude = coordinates?.let { format.latitude(it.latitude) },
            longitude = coordinates?.let { format.longitude(it.longitude) },
            origin = position.origin,
            automatic = position.source == PositionSource.AUTOMATIC,
            gps = position.gps,
            fixTime = fix?.let { format.utcStamp(it.time, reading.instant) },
            accuracy = fix?.accuracyMeters?.let(format::accuracy),
            approximate = fix?.approximate == true,
            invalidLocator = position.invalidLocator,
            source = coordinates?.let { sunPositionSource(position, reading.instant) },
            extendedLocator = fix?.let { Maidenhead.fromPosition(it.position, EXTENDED_LOCATOR) },
            altitude = fix?.altitudeMeters?.let(format::altitude),
            declination = coordinates?.let { format.declination(declination(it, fix?.altitudeMeters, reading.instant)) },
            home = fix?.let { home(it.position, station.locator, format) },
        )
    }

    /** Distance and azimut from a GPS position back to the home locator, from [HOME_AWAY_KM] away. */
    private fun home(here: GeoPosition, homeLocator: String, format: TimeFormatter): HomeUi? {
        val home = Maidenhead.toPosition(homeLocator) ?: return null
        val distance = GreatCircle.distanceKm(here, home)
        if (distance < HOME_AWAY_KM) return null
        return HomeUi(
            locator = Maidenhead.fromPosition(home, homeLocator.length) ?: homeLocator,
            distance = format.distance(distance),
            bearing = format.bearing(GreatCircle.bearingDegrees(here, home)),
        )
    }

    /** The last declination, reused while the position, the height and the UTC day stay the same. */
    private var lastDeclination: Pair<Triple<GeoPosition, Double?, LocalDate>, Double>? = null

    private fun declination(position: GeoPosition, altitude: Double?, instant: Instant): Double {
        val key = Triple(position, altitude, instant.atZone(java.time.ZoneOffset.UTC).toLocalDate())
        lastDeclination?.let { (lastKey, value) -> if (lastKey == key) return value }
        return magneticDeclination(position, altitude, instant).also { lastDeclination = key to it }
    }

    /**
     * The PROPAGATION card. Each band group shows N0NBH's day or night condition, whichever applies now
     * at the station: day between sunrise and sunset there (Phase 3), or, without a position, between
     * 06:00 and 18:00 local time (the dialog says so). Below it, the offline estimate for the ten HF
     * bands, from the same phase of the day and N0NBH's own SFI and K (fresh or saved); without N0NBH
     * data there is no estimate at all.
     */
    private fun propagationState(
        state: PropagationState,
        reading: ClockReading,
        solarDay: SolarDay?,
        format: TimeFormatter,
    ): PropagationUiState {
        val phase = solarDay?.phaseAt(reading.instant)
        val isDay = if (solarDay != null) {
            phase == DayPhase.DAY
        } else {
            reading.instant.atZone(reading.zone).hour in DAY_START_HOUR until DAY_END_HOUR
        }
        val conditions = state.conditions
        return PropagationUiState(
            status = state.status,
            solarFlux = conditions?.solarFlux?.let { IndexUi(format.number(it), IndexScales.solarFlux(it)) },
            kIndex = conditions?.kIndex?.let { IndexUi(format.number(it), IndexScales.kIndex(it)) },
            aIndex = conditions?.aIndex?.let { IndexUi(format.number(it), IndexScales.aIndex(it)) },
            bands = if (conditions == null) {
                emptyList()
            } else {
                BandGroup.entries.map { group ->
                    val band = conditions.bands[group]
                    // N0NBH's data, online or saved, is used as it is; the estimate only stands in for a
                    // group N0NBH reported nothing for, and only when N0NBH gave both SFI and K
                    val reported = band != null && (band.day != null || band.night != null)
                    val estimate = if (!reported && conditions.solarFlux != null && conditions.kIndex != null) {
                        groupEstimate(group, conditions.solarFlux, conditions.kIndex)
                    } else {
                        null
                    }
                    BandUi(group, now = if (isDay) band?.day else band?.night, day = band?.day, night = band?.night, estimate)
                }
            },
            isDay = isDay,
            dayNightByClock = solarDay == null,
            phase = phase,
            // N0NBH's own "updated" time from the feed, in UTC (not when the phone downloaded it)
            updated = conditions?.updated?.let { format.utcStamp(it, reading.instant) },
        )
    }

    /**
     * The fallback for a group without N0NBH data: each of its bands by day, at twilight and at night, from
     * [OfflinePropagationCalculator] with N0NBH's SFI and K as numbers (never read back from the shown text).
     */
    private fun groupEstimate(group: BandGroup, solarFlux: Double, kIndex: Double): List<BandPhasesUi> =
        group.bands.map { band ->
            BandPhasesUi(
                band = band,
                day = OfflinePropagationCalculator.level(band, DayPhase.DAY, solarFlux, kIndex),
                twilight = OfflinePropagationCalculator.level(band, DayPhase.TWILIGHT, solarFlux, kIndex),
                night = OfflinePropagationCalculator.level(band, DayPhase.NIGHT, solarFlux, kIndex),
            )
        }

    private fun sunState(day: LocalDay, input: SunInput): Sun {
        val position = input.position
        if (position == null) {
            val ui = when {
                // Automatic: say what keeps GPS from giving a position (the locator is only the fallback)
                input.source == PositionSource.AUTOMATIC -> SunUiState(
                    when (input.gps) {
                        GpsStatus.NO_PERMISSION -> SunStatus.GPS_NO_PERMISSION
                        GpsStatus.LOCATION_OFF -> SunStatus.GPS_LOCATION_OFF
                        GpsStatus.UNAVAILABLE -> SunStatus.GPS_UNAVAILABLE
                        GpsStatus.SEARCHING, GpsStatus.OK, null -> SunStatus.GPS_SEARCHING
                    },
                )
                input.invalidLocator != null -> SunUiState(SunStatus.INVALID_LOCATOR, locator = input.invalidLocator)
                else -> SunUiState(SunStatus.NO_LOCATOR)
            }
            return Sun(ui, null)
        }
        val solarDay = solarDay(day, position)
        val format = formatterFor(locale())
        fun time(instant: Instant?) = format.eventTime(instant, day.zone)
        val ui = SunUiState(
            status = when (solarDay.polar) {
                null -> SunStatus.NORMAL
                PolarCondition.MIDNIGHT_SUN -> SunStatus.MIDNIGHT_SUN
                PolarCondition.POLAR_NIGHT -> SunStatus.POLAR_NIGHT
            },
            sunrise = time(solarDay.sunrise),
            sunset = time(solarDay.sunset),
            solarNoon = time(solarDay.solarNoon),
            dayLength = format.duration(solarDay.dayLength),
            civilDawn = time(solarDay.civilDawn),
            civilDusk = time(solarDay.civilDusk),
        )
        return Sun(ui, solarDay)
    }

    private fun solarDay(day: LocalDay, position: GeoPosition): SolarDay {
        return solarDays.day(day.date, day.zone, position)
    }

    private fun formatterFor(current: Locale): TimeFormatter {
        if (formatter.locale != current) formatter = TimeFormatter(current)
        return formatter
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        /** Without a position, "day" for the band conditions is 06:00–18:00 local time. */
        /** The locator of a GPS position with 8 characters, as used on VHF and up. */
        const val EXTENDED_LOCATOR = 8

        /** From this far from the home locator, the screen says how far and which way home is. */
        const val HOME_AWAY_KM = 1.0

        const val DAY_START_HOUR = 6
        const val DAY_END_HOUR = 18
    }
}
