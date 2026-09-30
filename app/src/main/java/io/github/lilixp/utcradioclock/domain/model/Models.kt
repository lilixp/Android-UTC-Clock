package io.github.lilixp.utcradioclock.domain.model

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** One moment on the clock, with the phone's time zone at that moment. */
data class ClockReading(val instant: Instant, val zone: ZoneId)

/** Light, dark or following the phone's setting. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Sunrise and sunset at the station; unknown until the sun calculation is added (a later phase). */
data class SunInfo(
    val sunrise: Instant? = null,
    val sunset: Instant? = null,
    val dayLength: Duration? = null,
)

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

/** Where the station is; coordinates come in a later phase (the locator is in [StationIdentity]). */
data class StationLocation(
    val latitude: Double? = null,
    val longitude: Double? = null,
)
