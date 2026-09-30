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
 * A place on Earth in degrees: latitude north positive, longitude east positive. For now it is the
 * centre of the station's Maidenhead locator; a later phase can give it from GPS instead.
 */
data class GeoPosition(val latitude: Double, val longitude: Double)

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
