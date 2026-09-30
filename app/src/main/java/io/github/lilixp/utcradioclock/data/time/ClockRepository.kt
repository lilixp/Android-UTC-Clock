package io.github.lilixp.utcradioclock.data.time

import io.github.lilixp.utcradioclock.domain.model.ClockReading
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** The current time, once per second, aligned to the start of each second so the display never lags. */
class ClockRepository(private val timeSource: TimeSource) {

    fun current(): ClockReading = ClockReading(timeSource.now(), timeSource.zone())

    fun ticks(): Flow<ClockReading> = flow {
        while (true) {
            val reading = current()
            emit(reading)
            delay(MILLIS_PER_SECOND - reading.instant.toEpochMilli() % MILLIS_PER_SECOND)
        }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
    }
}
