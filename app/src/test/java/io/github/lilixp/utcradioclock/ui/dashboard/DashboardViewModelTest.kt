package io.github.lilixp.utcradioclock.ui.dashboard

import io.github.lilixp.utcradioclock.data.time.ClockRepository
import io.github.lilixp.utcradioclock.testing.FakeSettingsRepository
import io.github.lilixp.utcradioclock.testing.FakeTimeSource
import io.github.lilixp.utcradioclock.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val settings = FakeSettingsRepository()

    private fun TestScope.createViewModel(locale: Locale = Locale.forLanguageTag("ro")) =
        DashboardViewModel(ClockRepository(FakeTimeSource(testScheduler)), settings) { locale }

    /** Collects uiState while the test runs, as the screen does, so the clock ticks. */
    private fun TestScope.subscribe(viewModel: DashboardViewModel) {
        backgroundScope.launch(mainDispatcher.dispatcher) { viewModel.uiState.collect {} }
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

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals("15:42:32", viewModel.uiState.value.utcTime)

        advanceTimeBy(59_000)
        runCurrent()
        assertEquals("15:43:31", viewModel.uiState.value.utcTime)
        assertEquals("18:43:31", viewModel.uiState.value.localTime)
    }

    @Test
    fun localDateAppearsWhenItDiffersFromUtc() = runTest(mainDispatcher.dispatcher.scheduler) {
        val viewModel = createViewModel()
        subscribe(viewModel)

        // 21:00 UTC is already 00:00 on 1 October in Chișinău
        advanceTimeBy(java.time.Duration.ofHours(5).plusMinutes(17).plusSeconds(29).toMillis())
        runCurrent()
        val state = viewModel.uiState.value
        assertEquals("21:00:00", state.utcTime)
        assertEquals("00:00:00", state.localTime)
        assertEquals("30 septembrie 2026", state.utcDate)
        assertEquals("1 octombrie 2026", state.localDate)
    }

    @Test
    fun textsFollowTheLanguage() = runTest(mainDispatcher.dispatcher.scheduler) {
        val state = createViewModel(Locale.ENGLISH).uiState.value
        assertEquals("30 September 2026", state.utcDate)
    }
}
