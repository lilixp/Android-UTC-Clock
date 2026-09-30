package io.github.lilixp.utcradioclock.ui.dashboard

import io.github.lilixp.utcradioclock.data.time.ClockRepository
import io.github.lilixp.utcradioclock.testing.FakeSettingsRepository
import io.github.lilixp.utcradioclock.testing.MainDispatcherRule
import io.github.lilixp.utcradioclock.testing.START
import io.github.lilixp.utcradioclock.testing.TestZone
import io.github.lilixp.utcradioclock.testing.VirtualClock
import io.github.lilixp.utcradioclock.testing.timeProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** The dashboard state on a virtual clock: nothing here depends on the PC's real time or time zone. */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val settings = FakeSettingsRepository()
    private val zone = TestZone() // Europe/Chisinau unless a test changes it
    private lateinit var clock: VirtualClock

    private fun TestScope.createViewModel(
        start: Instant = START,
        locale: Locale = Locale.forLanguageTag("ro"),
    ): DashboardViewModel {
        clock = VirtualClock(testScheduler, start)
        return DashboardViewModel(ClockRepository(timeProvider(clock, zone)), settings) { locale }
    }

    /** Collects uiState as the screen does, so the clock ticks. */
    private fun TestScope.subscribe(viewModel: DashboardViewModel) =
        backgroundScope.launch(mainDispatcher.dispatcher) { viewModel.uiState.collect {} }

    private fun TestScope.advance(duration: Duration) {
        advanceTimeBy(duration.toMillis())
        runCurrent()
    }

    @Test
    fun initialState_showsTimesAndDateAndNothingUnknown() = runTest(mainDispatcher.dispatcher.scheduler) {
        val state = createViewModel().uiState.value

        assertEquals("30 septembrie 2026", state.utcDate)
        assertEquals("15:42:31", state.utcTime)
        assertEquals("18:42:31", state.localTime) // Chișinău, summer time: UTC+3
        assertNull(state.localDate) // same day in UTC and locally
        assertEquals("Europe/Chisinau · UTC+03:00", state.timeZone)
        assertNull(state.sunrise)
        assertNull(state.sunset)
        assertNull(state.dayLength)
        assertNull(state.latitude)
        assertNull(state.longitude)
        assertEquals("ER1PL", state.callsign) // default callsign
        assertNull(state.locator) // no locator until it is entered
    }

    @Test
    fun stationFromSettingsIsShownAndFollowsChanges() = runTest(mainDispatcher.dispatcher.scheduler) {
        val viewModel = createViewModel()
        subscribe(viewModel)

        settings.setCallsign("yo3abc")
        settings.setLocator("kn46dw")
        runCurrent()
        assertEquals("YO3ABC", viewModel.uiState.value.callsign)
        assertEquals("KN46dw", viewModel.uiState.value.locator)

        settings.setCallsign("")
        runCurrent()
        assertNull(viewModel.uiState.value.callsign) // an empty callsign is not shown
        assertEquals("15:42:31", viewModel.uiState.value.utcTime) // the clock is unaffected
    }

    @Test
    fun clockTicksEverySecond() = runTest(mainDispatcher.dispatcher.scheduler) {
        val viewModel = createViewModel()
        subscribe(viewModel)

        advance(Duration.ofSeconds(1))
        assertEquals("15:42:32", viewModel.uiState.value.utcTime)
        assertEquals("18:42:32", viewModel.uiState.value.localTime)

        advance(Duration.ofSeconds(59))
        assertEquals("15:43:31", viewModel.uiState.value.utcTime)
        assertEquals("18:43:31", viewModel.uiState.value.localTime)
    }

    @Test
    fun ticksFollowTheStartOfEachSecond_noDrift() = runTest(mainDispatcher.dispatcher.scheduler) {
        // Started 700 ms into a second: the next tick comes after 300 ms, at the next full second
        val viewModel = createViewModel(start = Instant.parse("2026-09-30T15:42:31.700Z"))
        subscribe(viewModel)
        assertEquals("15:42:31", viewModel.uiState.value.utcTime)

        advance(Duration.ofMillis(299))
        assertEquals("15:42:31", viewModel.uiState.value.utcTime)
        advance(Duration.ofMillis(1))
        assertEquals("15:42:32", viewModel.uiState.value.utcTime)

        // After an hour the display is still exactly on the clock's second
        advance(Duration.ofHours(1))
        assertEquals("16:42:32", viewModel.uiState.value.utcTime)
    }

    @Test
    fun localDateAppearsWhenItDiffersFromUtc() = runTest(mainDispatcher.dispatcher.scheduler) {
        val viewModel = createViewModel()
        subscribe(viewModel)

        // 21:00 UTC is already 00:00 on 1 October in Chișinău
        advance(Duration.ofHours(5).plusMinutes(17).plusSeconds(29))
        val state = viewModel.uiState.value
        assertEquals("21:00:00", state.utcTime)
        assertEquals("00:00:00", state.localTime)
        assertEquals("30 septembrie 2026", state.utcDate)
        assertEquals("1 octombrie 2026", state.localDate)
    }

    @Test
    fun utcMidnightWhileItIsStillEveningLocally() = runTest(mainDispatcher.dispatcher.scheduler) {
        zone.zone = ZoneId.of("America/New_York") // UTC-4 in September
        val viewModel = createViewModel(start = Instant.parse("2026-09-30T23:59:59Z"))
        subscribe(viewModel)
        assertEquals("30 septembrie 2026", viewModel.uiState.value.utcDate)
        assertNull(viewModel.uiState.value.localDate)

        advance(Duration.ofSeconds(1))
        val state = viewModel.uiState.value
        assertEquals("00:00:00", state.utcTime)
        assertEquals("1 octombrie 2026", state.utcDate) // the date comes from the same instant as the time
        assertEquals("20:00:00", state.localTime)
        assertEquals("30 septembrie 2026", state.localDate) // local is still the day before
    }

    @Test
    fun phoneTimeZoneChangeIsShownAtTheNextTick() = runTest(mainDispatcher.dispatcher.scheduler) {
        val viewModel = createViewModel()
        subscribe(viewModel)
        assertEquals("18:42:31", viewModel.uiState.value.localTime)

        zone.zone = ZoneId.of("Asia/Kolkata") // UTC+5:30, as if the user changed it in the phone settings
        advance(Duration.ofSeconds(1))
        val state = viewModel.uiState.value
        assertEquals("15:42:32", state.utcTime) // UTC does not change with the time zone
        assertEquals("21:12:32", state.localTime)
        assertEquals("Asia/Kolkata · UTC+05:30", state.timeZone)
    }

    @Test
    fun endOfSummerTimeInChisinau() = runTest(mainDispatcher.dispatcher.scheduler) {
        // Moldova goes back from UTC+3 to UTC+2 on 25 October 2026 at 01:00 UTC (04:00 → 03:00 local),
        // as recorded in the time zone database of the phone (here: of the JVM)
        val viewModel = createViewModel(start = Instant.parse("2026-10-25T00:59:59Z"))
        subscribe(viewModel)
        assertEquals("03:59:59", viewModel.uiState.value.localTime)
        assertEquals("Europe/Chisinau · UTC+03:00", viewModel.uiState.value.timeZone)

        advance(Duration.ofSeconds(1))
        val state = viewModel.uiState.value
        assertEquals("01:00:00", state.utcTime) // UTC simply goes on
        assertEquals("03:00:00", state.localTime) // the local clock goes back one hour
        assertEquals("Europe/Chisinau · UTC+02:00", state.timeZone)
    }

    @Test
    fun twoScreensShareOneTicker() = runTest(mainDispatcher.dispatcher.scheduler) {
        val viewModel = createViewModel()
        subscribe(viewModel)
        subscribe(viewModel) // e.g. a new screen subscribing while the old one is still there
        runCurrent()
        val readsBefore = clock.reads

        advance(Duration.ofSeconds(10))
        // One ticker reads the clock once per second; two tickers would read it about 20 times
        assertEquals(10, clock.reads - readsBefore)
    }

    @Test
    fun tickerStopsWhenTheDashboardIsNoLongerShown() = runTest(mainDispatcher.dispatcher.scheduler) {
        val viewModel = createViewModel()
        val screen = subscribe(viewModel)
        advance(Duration.ofSeconds(3))

        screen.cancel() // the dashboard goes away (background or Settings)
        advance(Duration.ofSeconds(6)) // past the 5 s grace period
        val readsAfterStop = clock.reads
        advance(Duration.ofMinutes(10))
        assertEquals(readsAfterStop, clock.reads) // nothing ticks in the background

        subscribe(viewModel) // back on screen: ticking resumes at once with the current time
        runCurrent()
        assertEquals("15:52:40", viewModel.uiState.value.utcTime)
        assertTrue(clock.reads > readsAfterStop)
    }

    @Test
    fun textsFollowTheLanguage() = runTest(mainDispatcher.dispatcher.scheduler) {
        val state = createViewModel(locale = Locale.ENGLISH).uiState.value
        assertEquals("30 September 2026", state.utcDate)
    }
}
