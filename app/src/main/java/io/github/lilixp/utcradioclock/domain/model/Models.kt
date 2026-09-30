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

/** Where the station is; unknown until location and locator are added (a later phase). */
data class StationLocation(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locator: String? = null,
)
