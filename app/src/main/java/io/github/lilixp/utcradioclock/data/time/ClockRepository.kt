package io.github.lilixp.utcradioclock.data.time

import io.github.lilixp.utcradioclock.domain.model.ClockReading
import io.github.lilixp.utcradioclock.domain.model.LocalDay
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/**
 * The current time, once per second. Each tick waits only until the start of the next second of the
 * clock (not a fixed 1000 ms), so a late tick is corrected by the next one and the display never drifts.
 * The flow is cold: it ticks only while someone collects it and stops with the collector's coroutine.
 */
class ClockRepository(private val time: TimeProvider) {

    /** UTC and local time are always taken from this one reading, so they belong to the same instant. */
    fun current(): ClockReading = ClockReading(time.now(), time.zone())

    fun ticks(): Flow<ClockReading> = flow {
        while (true) {
            val reading = current()
            emit(reading)
            delay(MILLIS_PER_SECOND - reading.instant.toEpochMilli() % MILLIS_PER_SECOND)
        }
    }

    /** The local calendar day now. */
    fun currentDay(): LocalDay = current().day

    /**
     * The local day and time zone, looked at once a minute (separately from the 1 s ticks) and emitted
     * only when one of them changes: at local midnight, or within a minute of a time zone change.
     */
    fun localDays(): Flow<LocalDay> = flow {
        while (true) {
            val reading = current()
            emit(reading.day)
            delay(MILLIS_PER_MINUTE - reading.instant.toEpochMilli() % MILLIS_PER_MINUTE)
        }
    }.distinctUntilChanged()

    private val ClockReading.day get() = LocalDay(instant.atZone(zone).toLocalDate(), zone)

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
