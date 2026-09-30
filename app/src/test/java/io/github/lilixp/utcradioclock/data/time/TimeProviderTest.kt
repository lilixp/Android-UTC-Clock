package io.github.lilixp.utcradioclock.data.time

import io.github.lilixp.utcradioclock.testing.CHISINAU
import io.github.lilixp.utcradioclock.testing.START
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.ZoneId
import java.time.ZoneOffset

/** The single source of time, with [Clock.fixed]: the results never depend on when the test runs. */
class TimeProviderTest {

    private val fixed = Clock.fixed(START, ZoneOffset.UTC)

    @Test
    fun instantComesFromTheInjectedClock() {
        val provider = TimeProvider(fixed) { CHISINAU }
        assertEquals(START, provider.now())
        assertEquals(START, provider.now()) // a fixed clock never moves, whatever the PC's time is
    }

    @Test
    fun timeZoneIsAskedAgainEveryTime() {
        var phoneZone: ZoneId = CHISINAU
        val provider = TimeProvider(fixed) { phoneZone }
        assertEquals(CHISINAU, provider.zone())

        phoneZone = ZoneId.of("Asia/Tokyo") // the user changes the phone's time zone
        assertEquals(ZoneId.of("Asia/Tokyo"), provider.zone())
    }

    @Test
    fun oneReadingHoldsUtcAndLocalForTheSameInstant() {
        val reading = ClockRepository(TimeProvider(fixed) { CHISINAU }).current()
        assertEquals(START, reading.instant)
        assertEquals(CHISINAU, reading.zone)
        assertEquals("2026-09-30T15:42:31Z", reading.instant.toString())
        assertEquals("2026-09-30T18:42:31+03:00[Europe/Chisinau]", reading.instant.atZone(reading.zone).toString())
    }
}
