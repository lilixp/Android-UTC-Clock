package io.github.lilixp.utcradioclock.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lilixp.utcradioclock.data.location.PositionRepository
import io.github.lilixp.utcradioclock.data.settings.SettingsRepository
import io.github.lilixp.utcradioclock.data.time.ClockRepository
import io.github.lilixp.utcradioclock.domain.model.ClockReading
import io.github.lilixp.utcradioclock.domain.model.GeoPosition
import io.github.lilixp.utcradioclock.domain.model.LocalDay
import io.github.lilixp.utcradioclock.domain.model.PolarCondition
import io.github.lilixp.utcradioclock.domain.model.SolarDay
import io.github.lilixp.utcradioclock.domain.model.StationIdentity
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
    /** The solar calculation; tests pass their own to count how often it runs. */
    private val solar: (LocalDate, ZoneId, GeoPosition) -> SolarDay = SolarCalculator::calculate,
    /** Read on every tick: the ViewModel survives a language change, the texts must follow it. */
    private val locale: () -> Locale,
) : ViewModel() {

    private var formatter = TimeFormatter(locale())

    /** The last solar calculation, reused while the day, the time zone and the position stay the same. */
    private var lastSolar: Pair<Pair<LocalDay, GeoPosition>, SolarDay>? = null

    /**
     * The SUN card. It depends on the local day, the time zone and the position only, so it is worked
     * out when one of them changes (at local midnight, on a time zone change, on a new locator), never
     * on the 1 s clock ticks.
     */
    private val sun: Flow<SunUiState> = combine(
        clock.localDays(),
        positions.position,
        settings.station.map { it.locator }.distinctUntilChanged(),
        ::sunState,
    ).distinctUntilChanged()

    val uiState: StateFlow<DashboardUiState> =
        combine(clock.ticks(), settings.station, sun, ::toUiState)
            .stateIn(
                scope = viewModelScope,
                // Keeps ticking through a screen rotation, stops 5 s after the dashboard is no longer shown
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = toUiState(
                    clock.current(),
                    settings.station.value,
                    sunState(clock.currentDay(), positions.current(), settings.station.value.locator),
                ),
            )

    private fun toUiState(reading: ClockReading, station: StationIdentity, sun: SunUiState): DashboardUiState {
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
            sun = sun,
            // Entered in Settings for now; a locator computed from GPS can be chosen here later,
            // the screen only ever receives the text
            locator = station.locator.ifEmpty { null },
        )
    }

    private fun sunState(day: LocalDay, position: GeoPosition?, locator: String): SunUiState {
        if (position == null) {
            return if (locator.isEmpty()) {
                SunUiState(SunStatus.NO_LOCATOR)
            } else {
                SunUiState(SunStatus.INVALID_LOCATOR, locator = locator)
            }
        }
        val solarDay = solarDay(day, position)
        val format = formatterFor(locale())
        fun time(instant: Instant?) = format.eventTime(instant, day.zone)
        return SunUiState(
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
            locator = locator,
        )
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
    }
}
