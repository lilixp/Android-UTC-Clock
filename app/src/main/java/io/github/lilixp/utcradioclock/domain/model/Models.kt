package io.github.lilixp.utcradioclock.domain.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One moment on the clock, with the phone's time zone at that moment. */
data class ClockReading(val instant: Instant, val zone: ZoneId)

/** Light, dark or following the phone's setting. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** A calendar day in a time zone: the solar data is calculated for it and again when it changes. */
data class LocalDay(val date: LocalDate, val zone: ZoneId)

/**
 * A place on Earth in degrees: latitude north positive, longitude east positive. It comes from GPS or
 * from the centre of the station's Maidenhead locator.
 */
data class GeoPosition(val latitude: Double, val longitude: Double) {
    /** Within −90…90° and −180…180° (NaN and infinity are not). */
    val isValid: Boolean
        get() = latitude in -90.0..90.0 && longitude in -180.0..180.0
}

/** Where the station's position comes from, as chosen in Settings. */
enum class PositionSource {
    /** The Maidenhead locator entered in Settings (the default: no permission needed). */
    MANUAL,

    /** The phone's location (GPS / Location Services), with the manual locator as fallback. */
    AUTOMATIC,
}

/** One position from the phone's Location Services. */
data class LocationFix(
    val position: GeoPosition,
    /** Radius of 68 % confidence, in metres; null when the phone did not say. */
    val accuracyMeters: Float?,
    /** When the phone measured it. */
    val time: Instant,
    /** Only "approximate location" was allowed (Android 12+): off by up to about 3 km. */
    val approximate: Boolean = false,
)

/** How the automatic (GPS) position is doing. */
enum class GpsStatus {
    /** Looking for a position (the first one, or a newer one). */
    SEARCHING,

    /** A position from the phone, recent enough. */
    OK,

    /** The app may not use the phone's location (not asked yet, refused, or revoked). */
    NO_PERMISSION,

    /** Location is turned off on the phone. */
    LOCATION_OFF,

    /** The phone gave no position this time (e.g. indoors, timeout). */
    UNAVAILABLE,
}

/** Where the station's position finally comes from. */
enum class PositionOrigin { GPS, LOCATOR, NONE }

/** The station's position as everything else uses it, and where it came from. */
data class StationPosition(
    /** Null when there is neither a GPS position nor a valid locator. */
    val position: GeoPosition?,
    val origin: PositionOrigin,
    /** The 6-character locator of the GPS position, or the locator as entered; null when empty. */
    val locator: String?,
    val source: PositionSource,
    /** Null in [PositionSource.MANUAL] mode: GPS is not used at all. */
    val gps: GpsStatus? = null,
    /** The GPS position used, when [origin] is [PositionOrigin.GPS]. */
    val fix: LocationFix? = null,
)

/** Days on which the Sun does not cross the horizon at all (far north or south). */
enum class PolarCondition {
    /** The Sun stays above the horizon all day: no sunrise and no sunset. */
    MIDNIGHT_SUN,

    /** The Sun stays below the horizon all day: no sunrise and no sunset. */
    POLAR_NIGHT,
}

/**
 * The Sun for one local calendar day at one position. A missing time means the event does not happen
 * on that day (it is never invented): no sunrise/sunset on polar days ([polar]), no civil twilight on
 * "white nights" when the Sun never goes 6° below the horizon.
 */
data class SolarDay(
    val date: LocalDate,
    val solarNoon: Instant,
    val sunrise: Instant?,
    val sunset: Instant?,
    val civilDawn: Instant?,
    val civilDusk: Instant?,
    val polar: PolarCondition?,
) {
    /** From sunrise to sunset; 24 h under the midnight sun, 0 in the polar night. */
    val dayLength: Duration
        get() = when {
            sunrise != null && sunset != null -> Duration.between(sunrise, sunset)
            polar == PolarCondition.MIDNIGHT_SUN -> Duration.ofHours(24)
            else -> Duration.ZERO
        }
}

/**
 * The station's callsign and Maidenhead locator, as entered in Settings; either may be empty.
 * Only simple rules for now: allowed characters and a maximum length, no full locator check.
 */
data class StationIdentity(val callsign: String = DEFAULT_CALLSIGN, val locator: String = "") {
    companion object {
        const val DEFAULT_CALLSIGN = "ER1PL"

        /** Even long callsigns with a prefix and suffix (e.g. "ER/YO3ABC/QRP") fit in 15 characters. */
        const val MAX_CALLSIGN_LENGTH = 15

        /** A Maidenhead locator has 2, 4, 6 or 8 characters (e.g. KN46dw). */
        const val MAX_LOCATOR_LENGTH = 8

        /** Upper-case letters, digits and "/", without spaces, at most [MAX_CALLSIGN_LENGTH] characters. */
        fun normalizeCallsign(text: String): String =
            text.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' || it == '/' }.take(MAX_CALLSIGN_LENGTH)

        /**
         * Latin letters and digits, at most [MAX_LOCATOR_LENGTH] characters, written the usual way:
         * the first four upper case, the rest lower case (KN46dw).
         */
        fun normalizeLocator(text: String): String {
            val clean = text.filter { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' }.take(MAX_LOCATOR_LENGTH)
            return clean.take(4).uppercase() + clean.drop(4).lowercase()
        }
    }
}
