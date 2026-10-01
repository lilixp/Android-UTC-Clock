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
    /** Read on every tick: the ViewModel survives a language change, the texts must follow it. */
    private val locale: () -> Locale,
) : ViewModel() {

    private var formatter = TimeFormatter(locale())

    /** The last solar calculation, reused while the day, the time zone and the position stay the same. */
    private var lastSolar: Pair<Pair<LocalDay, GeoPosition>, SolarDay>? = null

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
        return DashboardUiState(
            utcDate = utcDate,
            utcTime = format.utcTime(instant),
            localTime = format.localTime(instant, zone),
            localDate = localDate.takeIf { it != utcDate },
            timeZone = format.timeZone(instant, zone),
            callsign = station.callsign.ifEmpty { null },
            sun = sun.ui,
            propagation = propagationState(propagation, reading, sun.day, format),
            location = locationState(position, reading, format),
            // From GPS in automatic mode, otherwise as entered in Settings
            locator = position.locator,
        )
    }

    /** The LOCATION card: coordinates of the position used (GPS or the locator's centre) and where it came from. */
    private fun locationState(position: StationPosition, reading: ClockReading, format: TimeFormatter): LocationUiState {
        val fix = position.fix.takeIf { position.origin == PositionOrigin.GPS }
        return LocationUiState(
            latitude = position.position?.let { format.latitude(it.latitude) },
            longitude = position.position?.let { format.longitude(it.longitude) },
            origin = position.origin,
            automatic = position.source == PositionSource.AUTOMATIC,
            gps = position.gps,
            fixTime = fix?.let { format.localStamp(it.time, reading.zone, reading.instant) },
            accuracy = fix?.accuracyMeters?.let(format::accuracy),
            approximate = fix?.approximate == true,
            invalidLocator = position.invalidLocator,
        )
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
                    BandUi(group, now = if (isDay) band?.day else band?.night, day = band?.day, night = band?.night)
                }
            },
            isDay = isDay,
            dayNightByClock = solarDay == null,
            estimate = conditions?.let {
                OfflinePropagationCalculator.estimate(phase, it.solarFlux, it.kIndex)
            }.orEmpty(),
            phase = phase,
            // N0NBH's own "updated" time from the feed, in UTC (not when the phone downloaded it)
            updated = conditions?.updated?.let { format.utcStamp(it, reading.instant) },
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
        val key = day to position
        lastSolar?.let { (lastKey, result) -> if (lastKey == key) return result }
        return solar(day.date, day.zone, position).also { lastSolar = key to it }
    }

    private fun formatterFor(current: Locale): TimeFormatter {
        if (formatter.locale != current) formatter = TimeFormatter(current)
        return formatter
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        /** Without a position, "day" for the band conditions is 06:00–18:00 local time. */
        const val DAY_START_HOUR = 6
        const val DAY_END_HOUR = 18
    }
}
