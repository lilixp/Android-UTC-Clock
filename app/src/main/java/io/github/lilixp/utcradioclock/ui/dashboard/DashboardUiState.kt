package io.github.lilixp.utcradioclock.ui.dashboard

import io.github.lilixp.utcradioclock.data.propagation.PropagationState
import io.github.lilixp.utcradioclock.domain.model.BandGroup
import io.github.lilixp.utcradioclock.domain.model.ConditionLevel
import io.github.lilixp.utcradioclock.domain.model.GpsStatus
import io.github.lilixp.utcradioclock.domain.model.PositionOrigin

/**
 * Everything the dashboard shows, already as text. A null value means "not known yet" and is shown
 * as a dash.
 */
data class DashboardUiState(
    val utcDate: String,
    val utcTime: String,
    val localTime: String,
    /** Only when the local date differs from the UTC date (e.g. just after local midnight). */
    val localDate: String?,
    val timeZone: String,
    /** From Settings; null when empty. */
    val callsign: String? = null,
    val sun: SunUiState = SunUiState(),
    val propagation: PropagationUiState = PropagationUiState(),
    val location: LocationUiState = LocationUiState(),
    /** The station's locator: from GPS (6 characters) or as entered in Settings; null when there is none. */
    val locator: String? = null,
)

/** The LOCATION card (the locator itself is [DashboardUiState.locator]). */
data class LocationUiState(
    /** E.g. "46,9375° N"; null without a position. */
    val latitude: String? = null,
    val longitude: String? = null,
    val origin: PositionOrigin = PositionOrigin.NONE,
    /** Whether "Automatic (GPS)" is chosen in Settings. */
    val automatic: Boolean = false,
    /** How the phone's location is doing; null in manual mode. */
    val gps: GpsStatus? = null,
    /** When the GPS position was taken, local time, e.g. "18:42"; with [PositionOrigin.GPS] only. */
    val fixTime: String? = null,
    /** E.g. "±15 m"; null when the phone did not say. */
    val accuracy: String? = null,
    /** Only approximate location is allowed (Android 12+). */
    val approximate: Boolean = false,
    /** The locator from Settings when it is not valid (e.g. "KN4"): said instead of "not available". */
    val invalidLocator: String? = null,
)

/** SFI, K or A: the value exactly as N0NBH gave it, and its colour level. */
data class IndexUi(val value: String, val level: ConditionLevel)

/** One band group: N0NBH's condition now (day or night at the station), by day and by night. */
data class BandUi(
    val group: BandGroup,
    val now: ConditionLevel?,
    val day: ConditionLevel?,
    val night: ConditionLevel?,
)

/** The PROPAGATION card (N0NBH, hamqsl.com). Null values were not in the data and are shown as "—". */
data class PropagationUiState(
    val status: PropagationState.Status = PropagationState.Status.LOADING,
    val solarFlux: IndexUi? = null,
    val kIndex: IndexUi? = null,
    val aIndex: IndexUi? = null,
    /** The four band groups when there is data; empty while loading or unavailable. */
    val bands: List<BandUi> = emptyList(),
    /** Whether the bands show the day conditions now. */
    val isDay: Boolean = true,
    /** True when day/night comes from the clock (06–18) because there is no position. */
    val dayNightByClock: Boolean = false,
    /** When N0NBH updated the data (the feed's "updated"), in UTC, e.g. "05:29", or "30 sept. 05:29" for another day. */
    val updated: String? = null,
)

/** What the SUN card shows. */
enum class SunStatus {
    /** No locator in Settings: no position, so no solar data. */
    NO_LOCATOR,

    /** Automatic mode, no position: the app may not use the location of the phone. */
    GPS_NO_PERMISSION,

    /** Automatic mode, no position: location is turned off on the phone. */
    GPS_LOCATION_OFF,

    /** Automatic mode, no position yet: the phone is looking for one. */
    GPS_SEARCHING,

    /** Automatic mode, no position: the phone gave none this time. */
    GPS_UNAVAILABLE,

    /** A locator that is not a valid Maidenhead locator: no position, so no solar data. */
    INVALID_LOCATOR,

    /** An ordinary day with sunrise and sunset. */
    NORMAL,

    /** The Sun does not set on this day. */
    MIDNIGHT_SUN,

    /** The Sun does not rise on this day. */
    POLAR_NIGHT,
}

/**
 * The SUN card, as text in local time (HH:mm). Every time is null when that event does not happen on
 * the day (e.g. no civil twilight on a white night); nothing is ever made up.
 */
data class SunUiState(
    val status: SunStatus = SunStatus.NO_LOCATOR,
    val sunrise: String? = null,
    val sunset: String? = null,
    val solarNoon: String? = null,
    val dayLength: String? = null,
    val civilDawn: String? = null,
    val civilDusk: String? = null,
    /** The locator as entered, only to say which one is not valid ([SunStatus.INVALID_LOCATOR]). */
    val locator: String? = null,
)
