package io.github.lilixp.utcradioclock.ui.dashboard

import io.github.lilixp.utcradioclock.data.location.PositionRepository
import io.github.lilixp.utcradioclock.data.propagation.PropagationState
import io.github.lilixp.utcradioclock.data.time.ClockRepository
import io.github.lilixp.utcradioclock.domain.model.BandCondition
import io.github.lilixp.utcradioclock.domain.model.BandGroup
import io.github.lilixp.utcradioclock.domain.model.ConditionLevel.FAIR
import io.github.lilixp.utcradioclock.domain.model.ConditionLevel.GOOD
import io.github.lilixp.utcradioclock.domain.model.ConditionLevel.POOR
import io.github.lilixp.utcradioclock.domain.model.GeoPosition
import io.github.lilixp.utcradioclock.domain.model.SolarConditions
import io.github.lilixp.utcradioclock.domain.solar.SolarCalculator
import io.github.lilixp.utcradioclock.testing.FakeSettingsRepository
import io.github.lilixp.utcradioclock.testing.MainDispatcherRule
import io.github.lilixp.utcradioclock.testing.START
import io.github.lilixp.utcradioclock.testing.TestZone
import io.github.lilixp.utcradioclock.testing.VirtualClock
import io.github.lilixp.utcradioclock.testing.timeProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import java.time.LocalDate
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

    /** The real solar calculation, counting how often it runs and for which day, zone and position. */
    private val solarCalls = mutableListOf<Triple<LocalDate, ZoneId, GeoPosition>>()
    private val countingSolar = { date: LocalDate, zone: ZoneId, position: GeoPosition ->
        solarCalls += Triple(date, zone, position)
        SolarCalculator.calculate(date, zone, position)
    }

    /** What the propagation repository says; tests set it directly (no network here). */
    private val propagation = MutableStateFlow(PropagationState(PropagationState.Status.LOADING))

    private fun TestScope.createViewModel(
        start: Instant = START,
        locale: Locale = Locale.forLanguageTag("ro"),
    ): DashboardViewModel {
        clock = VirtualClock(testScheduler, start)
        return DashboardViewModel(
            clock = ClockRepository(timeProvider(clock, zone)),
            settings = settings,
            positions = PositionRepository(settings),
            propagation = propagation,
            solar = countingSolar,
        ) { locale }
    }

    /** N0NBH data like on 1 October 2026: SFI 93, K 0, A 3; 80-40m Fair by day and Good by night, etc. */
    private val n0nbh = SolarConditions(
        updated = Instant.parse("2026-09-30T15:00:00Z"),
        solarFlux = 93.0,
        kIndex = 0.0,
        aIndex = 3.0,
        bands = mapOf(
            BandGroup.BANDS_80_40 to BandCondition(FAIR, GOOD),
            BandGroup.BANDS_30_20 to BandCondition(GOOD, GOOD),
            BandGroup.BANDS_17_15 to BandCondition(FAIR, POOR),
            BandGroup.BANDS_12_10 to BandCondition(POOR, POOR),
        ),
    )

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
        assertEquals(SunUiState(SunStatus.NO_LOCATOR), state.sun) // no locator, so no position
        assertNull(state.latitude)
        assertNull(state.longitude)
        assertEquals("ER1PL", state.callsign) // default callsign
        assertNull(state.locator) // no locator until it is entered
        assertTrue(solarCalls.isEmpty()) // nothing is calculated without a position
    }

    // ---- The SUN card ----

    @Test
    fun sunFromTheLocatorInSettings() = runTest(mainDispatcher.dispatcher.scheduler) {
        settings.setLocator("KN46dw")
        val sun = createViewModel().uiState.value.sun

        // 30 September 2026 at the centre of KN46dw, in the phone's zone (Chișinău, UTC+3); exact values
        // 07:03:59, 18:48:54, 12:56:51, 11:44:55, 06:33:41, 19:19:09, rounded to the minute
        assertEquals(SunStatus.NORMAL, sun.status)
        assertEquals("07:04", sun.sunrise)
        assertEquals("18:49", sun.sunset)
        assertEquals("12:57", sun.solarNoon)
        assertEquals("11h 45m", sun.dayLength)
        assertEquals("06:34", sun.civilDawn)
        assertEquals("19:19", sun.civilDusk)
        assertEquals(GeoPosition(46.9375, 28.291667).latitude, solarCalls.single().third.latitude, 1e-6)
    }

    @Test
    fun otherLocatorOtherSun() = runTest(mainDispatcher.dispatcher.scheduler) {
        settings.setLocator("KN46dw")
        val viewModel = createViewModel()
        subscribe(viewModel)
        val chisinau = viewModel.uiState.value.sun

        settings.setLocator("IO91wm") // London, while the phone stays in Chișinău time
        runCurrent()
        val london = viewModel.uiState.value.sun
        assertEquals(SunStatus.NORMAL, london.status)
        assertTrue(london.sunrise != chisinau.sunrise && london.sunset != chisinau.sunset)
        assertEquals("08:59", london.sunrise) // 06:59 in London (UTC+1) = 08:59 in the phone's zone (UTC+3)
    }

    @Test
    fun invalidLocator_noSunData() = runTest(mainDispatcher.dispatcher.scheduler) {
        settings.setLocator("KN4") // saved by Settings (letters and digits) but not a Maidenhead locator
        val sun = createViewModel().uiState.value.sun
        assertEquals(SunUiState(SunStatus.INVALID_LOCATOR, locator = "KN4"), sun)
        assertTrue(solarCalls.isEmpty())
    }

    @Test
    fun locatorRemoved_sunDataGoes() = runTest(mainDispatcher.dispatcher.scheduler) {
        settings.setLocator("KN46dw")
        val viewModel = createViewModel()
        subscribe(viewModel)
        settings.setLocator("")
        runCurrent()
        assertEquals(SunUiState(SunStatus.NO_LOCATOR), viewModel.uiState.value.sun)
    }

    @Test
    fun sunIsNotRecalculatedEverySecond() = runTest(mainDispatcher.dispatcher.scheduler) {
        settings.setLocator("KN46dw")
        val viewModel = createViewModel()
        subscribe(viewModel)
        settings.setCallsign("YO3ABC") // not a reason to recalculate either

        advance(Duration.ofHours(2)) // 7200 clock ticks
        assertEquals("17:42:31", viewModel.uiState.value.utcTime)
        assertEquals(1, solarCalls.size)
    }

    @Test
    fun sunIsRecalculatedForTheNewLocalDay() = runTest(mainDispatcher.dispatcher.scheduler) {
        settings.setLocator("KN46dw")
        val viewModel = createViewModel() // 18:42:31 local on 30 September
        subscribe(viewModel)
        val before = viewModel.uiState.value.sun

        advance(Duration.ofHours(5).plusMinutes(18)) // 00:00:31 local on 1 October
        assertEquals(listOf(LocalDate.parse("2026-09-30"), LocalDate.parse("2026-10-01")), solarCalls.map { it.first })
        val after = viewModel.uiState.value.sun
        assertTrue("the days are getting shorter", after.sunrise!! > before.sunrise!!)
    }

    @Test
    fun sunIsRecalculatedForANewTimeZone_andShownInIt() = runTest(mainDispatcher.dispatcher.scheduler) {
        settings.setLocator("KN46dw")
        val viewModel = createViewModel()
        subscribe(viewModel)
        assertEquals("07:04", viewModel.uiState.value.sun.sunrise)

        zone.zone = ZoneId.of("UTC") // the user sets the phone to UTC: same place, times shown in UTC
        advance(Duration.ofMinutes(1)) // the day and zone are looked at once a minute
        assertEquals("04:04", viewModel.uiState.value.sun.sunrise)
        assertEquals(2, solarCalls.size)
        assertEquals(ZoneId.of("UTC"), solarCalls.last().second)
    }

    @Test
    fun summerAndWinterTimeInTheSunTimes() = runTest(mainDispatcher.dispatcher.scheduler) {
        settings.setLocator("KN46dw")
        // 21 December: winter time in Chișinău (UTC+2), local times still right
        val sun = createViewModel(start = Instant.parse("2026-12-21T10:00:00Z")).uiState.value.sun
        assertEquals("07:49", sun.sunrise) // exact 07:49:21
        assertEquals("16:20", sun.sunset) // exact 16:20:22
        assertEquals("8h 31m", sun.dayLength) // exact 8:31:01
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

    // ---- The PROPAGATION card ----

    private fun TestScope.shownPropagation(state: PropagationState): PropagationUiState {
        val viewModel = createViewModel()
        subscribe(viewModel)
        propagation.value = state
        runCurrent()
        return viewModel.uiState.value.propagation
    }

    private fun current(conditions: SolarConditions = n0nbh) =
        PropagationState(PropagationState.Status.CURRENT, conditions, START.minus(Duration.ofMinutes(10)))

    @Test
    fun loading_noValues() = runTest(mainDispatcher.dispatcher.scheduler) {
        val shown = createViewModel().uiState.value.propagation
        assertEquals(PropagationState.Status.LOADING, shown.status)
        assertNull(shown.solarFlux)
        assertTrue(shown.bands.isEmpty())
    }

    @Test
    fun data_valuesAsPublished_withTheirLevels() = runTest(mainDispatcher.dispatcher.scheduler) {
        val shown = shownPropagation(current())
        assertEquals(PropagationState.Status.CURRENT, shown.status)
        assertEquals(IndexUi("93", FAIR), shown.solarFlux) // 90–119
        assertEquals(IndexUi("0", GOOD), shown.kIndex) // whole number, as N0NBH publishes it
        assertEquals(IndexUi("3", GOOD), shown.aIndex)
        assertEquals(BandGroup.entries.toList(), shown.bands.map { it.group }) // all four, in order
        assertEquals("15:00", shown.updated) // N0NBH's update time, UTC
    }

    @Test
    fun withLocator_dayUntilSunsetAtTheStation_thenNight() = runTest(mainDispatcher.dispatcher.scheduler) {
        settings.setLocator("KN46dw") // sunset 18:49 local, it is 18:42:31 now
        val viewModel = createViewModel()
        subscribe(viewModel)
        propagation.value = current()
        runCurrent()
        val day = viewModel.uiState.value.propagation
        assertTrue(day.isDay)
        assertEquals(false, day.dayNightByClock)
        assertEquals(listOf(FAIR, GOOD, FAIR, POOR), day.bands.map { it.now }) // the day column

        advance(Duration.ofMinutes(7)) // 18:49:31, after sunset
        val night = viewModel.uiState.value.propagation
        assertEquals(false, night.isDay)
        assertEquals(listOf(GOOD, GOOD, POOR, POOR), night.bands.map { it.now }) // the night column
        assertEquals(BandUi(BandGroup.BANDS_80_40, now = GOOD, day = FAIR, night = GOOD), night.bands.first())
    }

    @Test
    fun withoutLocator_dayNightByTheClock() = runTest(mainDispatcher.dispatcher.scheduler) {
        val shown = shownPropagation(current()) // 18:42 local: after 18:00
        assertEquals(false, shown.isDay)
        assertTrue(shown.dayNightByClock)
        assertEquals(listOf(GOOD, GOOD, POOR, POOR), shown.bands.map { it.now })
    }

    @Test
    fun staleData_keptAndMarked_withItsDate() = runTest(mainDispatcher.dispatcher.scheduler) {
        val old = n0nbh.copy(updated = Instant.parse("2026-09-29T21:00:00Z"))
        val shown = shownPropagation(PropagationState(PropagationState.Status.STALE, old, START.minus(Duration.ofHours(18))))
        assertEquals(PropagationState.Status.STALE, shown.status)
        assertEquals(IndexUi("93", FAIR), shown.solarFlux) // the last valid value, not a new one
        val updated = shown.updated!!
        assertTrue("another day shows the date: $updated", updated.startsWith("29 ") && updated.endsWith("21:00"))
    }

    @Test
    fun unavailable_nothingToShow() = runTest(mainDispatcher.dispatcher.scheduler) {
        val shown = shownPropagation(PropagationState(PropagationState.Status.UNAVAILABLE))
        assertEquals(PropagationState.Status.UNAVAILABLE, shown.status)
        assertNull(shown.solarFlux)
        assertNull(shown.kIndex)
        assertNull(shown.aIndex)
        assertTrue(shown.bands.isEmpty())
    }

    @Test
    fun missingValues_stayMissing() = runTest(mainDispatcher.dispatcher.scheduler) {
        val partial = n0nbh.copy(kIndex = null, bands = mapOf(BandGroup.BANDS_30_20 to BandCondition(GOOD, null)))
        val shown = shownPropagation(current(partial))
        assertNull(shown.kIndex)
        assertEquals(4, shown.bands.size)
        assertEquals(listOf(null, null, null, null), shown.bands.map { it.now }) // night now, and no night value
        assertEquals(GOOD, shown.bands[1].day)
    }
}
