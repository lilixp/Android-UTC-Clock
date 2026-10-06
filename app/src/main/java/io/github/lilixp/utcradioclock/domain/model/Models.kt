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
    /**
     * Height in metres: above sea level when the phone gives it, otherwise the GPS (WGS 84 ellipsoid)
     * height; null when the phone gave none.
     */
    val altitudeMeters: Double? = null,
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
    /** The 6-character locator of the GPS position, or the valid locator from Settings; never an invalid one. */
    val locator: String?,
    val source: PositionSource,
    /** Null in [PositionSource.MANUAL] mode: GPS is not used at all. */
    val gps: GpsStatus? = null,
    /** The GPS position used, when [origin] is [PositionOrigin.GPS]. */
    val fix: LocationFix? = null,
    /** The locator entered in Settings when it is not a valid locator (e.g. "KN4"), to say so; otherwise null. */
    val invalidLocator: String? = null,
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

    /**
     * Day, civil twilight or night at the station at [instant] — the one place the app decides it
     * (the N0NBH band conditions use "day", the offline estimate all three):
     * - day: from sunrise to sunset (always under the midnight sun);
     * - civil twilight: from civil dawn to sunrise and from sunset to civil dusk, the whole night of a
     *   "white night" (no civil dawn or dusk), and from dawn to dusk on a polar-night day that has them;
     * - night: before civil dawn and after civil dusk, and all day in a polar night without twilight.
     *
     * Null when it cannot be decided: a day with a sunrise but no sunset (or the other way round),
     * which happens only very close to a polar day or night.
     */
    fun phaseAt(instant: Instant): DayPhase? = when {
        polar == PolarCondition.MIDNIGHT_SUN -> DayPhase.DAY
        sunrise != null && sunset != null && instant >= sunrise && instant < sunset -> DayPhase.DAY
        polar == null && (sunrise == null || sunset == null) -> null
        civilDawn != null && instant < civilDawn -> DayPhase.NIGHT
        civilDusk != null && instant >= civilDusk -> DayPhase.NIGHT
        polar == PolarCondition.POLAR_NIGHT && civilDawn == null && civilDusk == null -> DayPhase.NIGHT
        else -> DayPhase.TWILIGHT
    }
}

/** Where the Sun is for HF: above the horizon, up to 6° below it (civil twilight), or lower. */
enum class DayPhase { DAY, TWILIGHT, NIGHT }

/**
 * The station's callsign and Maidenhead locator, as entered in Settings; either may be empty.
 * Only the characters and the length are cleaned here, so a locator can be saved while it is typed;
 * whether it is a valid locator is decided by [io.github.lilixp.utcradioclock.domain.location.Maidenhead].
 */
data class StationIdentity(val callsign: String = DEFAULT_CALLSIGN, val locator: String = "") {
    companion object {
        const val DEFAULT_CALLSIGN = "ER1PL"

        /** Even long callsigns with a prefix and suffix (e.g. "ER/YO3ABC/QRP") fit in 15 characters. */
        const val MAX_CALLSIGN_LENGTH = 15

        /** A Maidenhead locator has 4, 6 or 8 characters (e.g. KN46dw); shorter text is kept while typing. */
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
