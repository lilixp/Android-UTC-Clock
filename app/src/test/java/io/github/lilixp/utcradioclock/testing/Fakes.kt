package io.github.lilixp.utcradioclock.testing

import io.github.lilixp.utcradioclock.data.settings.SettingsRepository
import io.github.lilixp.utcradioclock.data.time.TimeProvider
import io.github.lilixp.utcradioclock.domain.model.PositionSource
import io.github.lilixp.utcradioclock.domain.model.StationIdentity
import io.github.lilixp.utcradioclock.domain.model.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** 30 September 2026, 15:42:31.000 UTC: the moment shown in the phase 1 mock-up. */
val START: Instant = Instant.parse("2026-09-30T15:42:31Z")
val CHISINAU: ZoneId = ZoneId.of("Europe/Chisinau")

/**
 * A [Clock] that starts at [start] and moves only with the test's virtual time (delay, advanceTimeBy),
 * never with the PC's real time. It also counts how often it was read, to catch duplicate tickers.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VirtualClock(
    private val scheduler: TestCoroutineScheduler,
    private val start: Instant = START,
) : Clock() {
    var reads = 0
        private set

    override fun instant(): Instant {
        reads++
        return start.plusMillis(scheduler.currentTime)
    }

    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = throw UnsupportedOperationException()
}

/** The phone's time zone in tests; change [zone] to simulate the user changing it. */
class TestZone(var zone: ZoneId = CHISINAU)

/** The app's real [TimeProvider], fed by a test clock and a test time zone. */
fun timeProvider(clock: Clock, zone: TestZone = TestZone()) = TimeProvider(clock) { zone.zone }

/** Settings in memory, with the same cleaning of callsign and locator as the real repository. */
class FakeSettingsRepository(
    initial: ThemeMode = ThemeMode.SYSTEM,
    station: StationIdentity = StationIdentity(),
    source: PositionSource = PositionSource.MANUAL,
) : SettingsRepository {
    override val themeMode = MutableStateFlow(initial)
    override val station = MutableStateFlow(station)
    override val positionSource = MutableStateFlow(source)

    override fun setPositionSource(source: PositionSource) {
        positionSource.value = source
    }

    override fun setThemeMode(mode: ThemeMode) {
        themeMode.value = mode
    }

    override fun setCallsign(callsign: String) {
        station.value = station.value.copy(callsign = StationIdentity.normalizeCallsign(callsign))
    }

    override fun setLocator(locator: String) {
        station.value = station.value.copy(locator = StationIdentity.normalizeLocator(locator))
    }
}

/** Runs viewModelScope (Dispatchers.Main) on the test dispatcher, so virtual time controls the clock. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}
