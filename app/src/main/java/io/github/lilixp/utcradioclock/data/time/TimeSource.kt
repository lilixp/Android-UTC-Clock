package io.github.lilixp.utcradioclock.data.time

import java.time.Instant
import java.time.ZoneId

/** Where the current time and the phone's time zone come from; replaced by a fake in tests. */
interface TimeSource {
    fun now(): Instant
    fun zone(): ZoneId
}

/** The phone's own clock and time zone (read each time, so a zone change while travelling is picked up). */
object SystemTimeSource : TimeSource {
    override fun now(): Instant = Instant.now()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}
