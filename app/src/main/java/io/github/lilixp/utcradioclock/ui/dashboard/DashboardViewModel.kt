package io.github.lilixp.utcradioclock.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lilixp.utcradioclock.data.settings.SettingsRepository
import io.github.lilixp.utcradioclock.data.time.ClockRepository
import io.github.lilixp.utcradioclock.domain.model.ClockReading
import io.github.lilixp.utcradioclock.domain.model.StationIdentity
import io.github.lilixp.utcradioclock.domain.model.StationLocation
import io.github.lilixp.utcradioclock.domain.model.SunInfo
import io.github.lilixp.utcradioclock.util.TimeFormatter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Locale

class DashboardViewModel(
    private val clock: ClockRepository,
    private val settings: SettingsRepository,
    /** Read on every tick: the ViewModel survives a language change, the texts must follow it. */
    private val locale: () -> Locale,
) : ViewModel() {

    private var formatter = TimeFormatter(locale())

    // Not known yet; later phases will provide them from their own repositories
    private val sun = SunInfo()
    private val location = StationLocation()

    val uiState: StateFlow<DashboardUiState> =
        combine(clock.ticks(), settings.station, ::toUiState)
            .stateIn(
                scope = viewModelScope,
                // Keeps ticking through a screen rotation, stops 5 s after the dashboard is no longer shown
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = toUiState(clock.current(), settings.station.value),
            )

    private fun toUiState(reading: ClockReading, station: StationIdentity): DashboardUiState {
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
            sunrise = format.eventTime(sun.sunrise, zone),
            sunset = format.eventTime(sun.sunset, zone),
            dayLength = format.duration(sun.dayLength),
            latitude = location.latitude?.let { "%.4f°".format(Locale.ROOT, it) },
            longitude = location.longitude?.let { "%.4f°".format(Locale.ROOT, it) },
            // Entered in Settings for now; a locator computed from GPS can be chosen here later,
            // the screen only ever receives the text
            locator = station.locator.ifEmpty { null },
        )
    }

    private fun formatterFor(current: Locale): TimeFormatter {
        if (formatter.locale != current) formatter = TimeFormatter(current)
        return formatter
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
