package io.github.lilixp.utcradioclock.data.time

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * The only source of time in the app: the current instant and the phone's time zone.
 *
 * - [clock] gives the instant. The app uses the device clock in UTC ([Clock.systemUTC]); tests pass
 *   [Clock.fixed] or a clock they move themselves, so no test depends on the PC's real time.
 * - [zone] gives the time zone and is asked again every time: when the user changes the phone's time
 *   zone (or travels), Android updates the default zone of the running app and the next tick shows the
 *   new local time. Nothing is hard-coded: Chișinău is only what the phone reports.
 */
class TimeProvider(
    private val clock: Clock = Clock.systemUTC(),
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    fun now(): Instant = clock.instant()

    fun zone(): ZoneId = zone.invoke()
}
