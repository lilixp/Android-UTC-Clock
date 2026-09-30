package io.github.lilixp.utcradioclock.testing

import io.github.lilixp.utcradioclock.data.settings.SettingsRepository
import io.github.lilixp.utcradioclock.data.time.TimeSource
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
import java.time.Instant
import java.time.ZoneId

/** 30 September 2026, 15:42:31.000 UTC: the moment shown in the phase 1 mock-up. */
val START: Instant = Instant.parse("2026-09-30T15:42:31Z")
val CHISINAU: ZoneId = ZoneId.of("Europe/Chisinau")

/** A clock that starts at [start] and moves only with the test's virtual time (delay / advanceTimeBy). */
@OptIn(ExperimentalCoroutinesApi::class)
class FakeTimeSource(
    private val scheduler: TestCoroutineScheduler,
    private val start: Instant = START,
    private val zone: ZoneId = CHISINAU,
) : TimeSource {
    override fun now(): Instant = start.plusMillis(scheduler.currentTime)
    override fun zone(): ZoneId = zone
}

/** Settings in memory, with the same cleaning of callsign and locator as the real repository. */
class FakeSettingsRepository(
    initial: ThemeMode = ThemeMode.SYSTEM,
    station: StationIdentity = StationIdentity(),
) : SettingsRepository {
    override val themeMode = MutableStateFlow(initial)
    override val station = MutableStateFlow(station)

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
