package io.github.lilixp.utcradioclock.data.location

import io.github.lilixp.utcradioclock.domain.location.Maidenhead
import io.github.lilixp.utcradioclock.domain.model.GeoPosition
import io.github.lilixp.utcradioclock.domain.model.GpsStatus
import io.github.lilixp.utcradioclock.domain.model.PositionOrigin
import io.github.lilixp.utcradioclock.domain.model.PositionSource
import io.github.lilixp.utcradioclock.domain.model.StationIdentity
import io.github.lilixp.utcradioclock.domain.model.StationPosition
import io.github.lilixp.utcradioclock.testing.BOGHICENI
import io.github.lilixp.utcradioclock.testing.FakeLastPositionStore
import io.github.lilixp.utcradioclock.testing.FakeLocationProvider
import io.github.lilixp.utcradioclock.testing.FakeSettingsRepository
import io.github.lilixp.utcradioclock.testing.LONDON
import io.github.lilixp.utcradioclock.testing.START
import io.github.lilixp.utcradioclock.testing.VirtualClock
import io.github.lilixp.utcradioclock.testing.fix
import io.github.lilixp.utcradioclock.testing.timeProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration

/** The station's position from GPS or the locator, on virtual time and a fake phone: never the real GPS. */
@OptIn(ExperimentalCoroutinesApi::class)
class PositionRepositoryTest {

    private val kn46dw = Maidenhead.toPosition("KN46dw")!!
    private val settings = FakeSettingsRepository(
        station = StationIdentity("ER1PL", "KN46dw"),
        source = PositionSource.AUTOMATIC,
    )
    private val phone = FakeLocationProvider()
    private val store = FakeLastPositionStore()

    private fun TestScope.repository() =
        PositionRepository(settings, phone, store, timeProvider(VirtualClock(testScheduler, START)))

    /** Collects [PositionRepository.updates] as the dashboard does; returns the latest state. */
    private fun TestScope.collect(repository: PositionRepository): () -> StationPosition {
        var latest: StationPosition? = null
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.updates().collect { latest = it } }
        runCurrent()
        return { latest!! }
    }

    private fun TestScope.advance(duration: Duration) {
        advanceTimeBy(duration.toMillis())
        runCurrent()
    }

    // ---- Manual ----

    @Test
    fun manual_theLocatorOnly_thePhoneIsNeverAsked() = runTest {
        settings.setPositionSource(PositionSource.MANUAL)
        phone.current = fix(BOGHICENI)
        val state = collect(repository())

        assertEquals(StationPosition(kn46dw, PositionOrigin.LOCATOR, "KN46dw", PositionSource.MANUAL), state())
        advance(Duration.ofHours(2))
        assertEquals(0, phone.requests)
    }

    @Test
    fun manual_withoutLocator_noPosition() = runTest {
        settings.setPositionSource(PositionSource.MANUAL)
        settings.setLocator("")
        val state = collect(repository())
        assertEquals(StationPosition(null, PositionOrigin.NONE, null, PositionSource.MANUAL), state())
    }

    @Test
    fun backToManual_theSavedGpsPositionIsDeleted() = runTest {
        phone.current = fix(BOGHICENI)
        val state = collect(repository())
        assertEquals(PositionOrigin.GPS, state().origin)

        settings.setPositionSource(PositionSource.MANUAL)
        runCurrent()
        assertNull(store.saved)
        assertEquals(PositionOrigin.LOCATOR, state().origin)
        assertEquals(kn46dw, state().position)
    }

    // ---- Automatic ----

    @Test
    fun validPosition_usedWithItsLocator_andSaved() = runTest {
        phone.current = fix(BOGHICENI)
        val state = collect(repository())

        assertEquals(GpsStatus.OK, state().gps)
        assertEquals(PositionOrigin.GPS, state().origin)
        assertEquals(BOGHICENI, state().position)
        assertEquals("KN46dx", state().locator) // not the KN46dw entered in Settings
        assertEquals(fix(BOGHICENI), store.saved) // for the next start, offline or indoors
        assertEquals(1, phone.requests)
    }

    @Test
    fun noPermission_theLocatorIsUsed_andThePhoneIsNotAsked() = runTest {
        phone.permission = false
        phone.current = fix(BOGHICENI)
        store.saved = fix(LONDON) // from before the permission was taken away: not used
        val state = collect(repository())

        assertEquals(GpsStatus.NO_PERMISSION, state().gps)
        assertEquals(PositionOrigin.LOCATOR, state().origin)
        assertEquals(kn46dw, state().position)
        assertEquals(0, phone.requests)
    }

    @Test
    fun noPermissionAndNoLocator_noPositionAtAll() = runTest {
        phone.permission = false
        settings.setLocator("")
        val state = collect(repository())
        assertEquals(StationPosition(null, PositionOrigin.NONE, null, PositionSource.AUTOMATIC, GpsStatus.NO_PERMISSION), state())
    }

    @Test
    fun permissionGrantedLater_refreshNowLooksAtOnce() = runTest {
        phone.permission = false
        phone.current = fix(BOGHICENI)
        val repository = repository()
        val state = collect(repository)
        assertEquals(GpsStatus.NO_PERMISSION, state().gps)

        phone.permission = true // the user allowed it in the dialog
        repository.refreshNow()
        runCurrent()
        assertEquals(GpsStatus.OK, state().gps)
        assertEquals(BOGHICENI, state().position)
    }

    @Test
    fun locationOff_theLocatorIsUsed() = runTest {
        phone.enabled = false
        phone.current = fix(BOGHICENI)
        val state = collect(repository())

        assertEquals(GpsStatus.LOCATION_OFF, state().gps)
        assertEquals(PositionOrigin.LOCATOR, state().origin)
        assertEquals(0, phone.requests)
    }

    @Test
    fun locationOff_theLastSavedPositionIsKept() = runTest {
        phone.enabled = false
        store.saved = fix(BOGHICENI, START.minus(Duration.ofDays(2)))
        val state = collect(repository())

        assertEquals(GpsStatus.LOCATION_OFF, state().gps)
        assertEquals(PositionOrigin.GPS, state().origin) // better than the centre of a locator
        assertEquals(BOGHICENI, state().position)
    }

    @Test
    fun noPositionFromThePhone_theLocatorIsUsed() = runTest {
        phone.current = null
        val state = collect(repository())

        assertEquals(GpsStatus.UNAVAILABLE, state().gps)
        assertEquals(PositionOrigin.LOCATOR, state().origin)
        assertEquals(kn46dw, state().position)
    }

    @Test
    fun thePhoneNeverAnswers_searchingThenUnavailableAfter30Seconds() = runTest {
        phone.hangs = true
        val state = collect(repository())
        assertEquals(GpsStatus.SEARCHING, state().gps)
        assertEquals(PositionOrigin.LOCATOR, state().origin) // usable meanwhile

        advance(Duration.ofSeconds(29))
        assertEquals(GpsStatus.SEARCHING, state().gps)
        advance(Duration.ofSeconds(1))
        assertEquals(GpsStatus.UNAVAILABLE, state().gps)
    }

    @Test
    fun invalidCoordinatesFromThePhone_ignored() = runTest {
        for (bad in listOf(GeoPosition(123.0, 28.0), GeoPosition(46.0, -200.0), GeoPosition(Double.NaN, Double.NaN))) {
            phone.current = fix(bad)
            phone.lastKnown = fix(bad)
            val state = collect(repository())
            assertEquals(GpsStatus.UNAVAILABLE, state().gps)
            assertEquals(kn46dw, state().position)
            assertNull(store.saved)
        }
    }

    @Test
    fun locationServicesFail_noCrash_theLocatorIsUsed() = runTest {
        phone.failure = SecurityException("permission revoked")
        val state = collect(repository())
        assertEquals(GpsStatus.NO_PERMISSION, state().gps)
        assertEquals(kn46dw, state().position)
    }

    // ---- Not using the GPS more than needed ----

    @Test
    fun recentPositionOfAnotherApp_usedWithoutTurningTheGpsOn() = runTest {
        phone.lastKnown = fix(BOGHICENI, START.minus(Duration.ofMinutes(3)))
        val state = collect(repository())
        assertEquals(BOGHICENI, state().position)
        assertEquals(0, phone.requests)
    }

    @Test
    fun oldPositionOfAnotherApp_notUsed_aNewOneIsAsked() = runTest {
        phone.lastKnown = fix(LONDON, START.minus(Duration.ofHours(5)))
        phone.current = fix(BOGHICENI)
        val state = collect(repository())
        assertEquals(BOGHICENI, state().position)
        assertEquals(1, phone.requests)
    }

    @Test
    fun recentSavedPosition_usedAtStart_noNewRequest() = runTest {
        store.saved = fix(BOGHICENI, START.minus(Duration.ofMinutes(5)))
        val repository = repository()
        assertEquals(BOGHICENI, repository.current()) // known before anything is collected
        val state = collect(repository)
        assertEquals(GpsStatus.OK, state().gps)
        assertEquals(0, phone.requests)
    }

    @Test
    fun olderSavedPosition_shownWhileANewOneIsFound() = runTest {
        store.saved = fix(LONDON, START.minus(Duration.ofDays(1)))
        phone.hangs = true
        val state = collect(repository())
        assertEquals(GpsStatus.SEARCHING, state().gps)
        assertEquals(LONDON, state().position)

        advance(Duration.ofSeconds(30)) // no answer: the old position stays
        assertEquals(GpsStatus.UNAVAILABLE, state().gps)
        assertEquals(LONDON, state().position)
    }

    @Test
    fun askedAgainEvery30Minutes_whileShown() = runTest {
        phone.current = fix(BOGHICENI)
        val state = collect(repository())
        assertEquals(1, phone.requests)

        advance(Duration.ofMinutes(29))
        assertEquals(1, phone.requests)
        phone.current = fix(LONDON, START.plus(Duration.ofMinutes(30)))
        advance(Duration.ofMinutes(1))
        assertEquals(2, phone.requests)
        assertEquals(LONDON, state().position)
        assertEquals("IO91wm", state().locator)
    }

    @Test
    fun notShown_thePhoneIsNotAsked() = runTest {
        phone.current = fix(BOGHICENI)
        val repository = repository()
        repository.current()
        repository.refreshNow()
        advance(Duration.ofHours(1))
        assertEquals(0, phone.requests) // only updates() (the dashboard on screen) asks the phone
    }

    // ---- Phase A: only valid locators (4, 6 or 8 characters) ----

    @Test
    fun validLocators_fourSixEightCharacters_arePositions() = runTest {
        settings.setPositionSource(PositionSource.MANUAL)
        val repository = repository()
        val state = collect(repository)
        for (locator in listOf("KN46", "KN46dw", "KN46dw12")) {
            settings.setLocator(locator)
            runCurrent()
            assertEquals(locator, PositionOrigin.LOCATOR, state().origin)
            assertEquals(locator, Maidenhead.toPosition(locator), state().position)
            assertEquals(locator, locator, state().locator)
            assertNull(locator, state().invalidLocator)
        }
    }

    @Test
    fun invalidLocators_neverAPositionOrAQth_butSaidToBeInvalid() = runTest {
        settings.setPositionSource(PositionSource.MANUAL)
        val repository = repository()
        val state = collect(repository)
        for (locator in listOf("KN", "KN4", "KN46d", "KN46dwx", "ZZ99")) {
            settings.setLocator(locator)
            runCurrent()
            val saved = settings.station.value.locator // as Settings keeps it while typing
            assertEquals(
                locator,
                StationPosition(null, PositionOrigin.NONE, null, PositionSource.MANUAL, invalidLocator = saved),
                state(),
            )
            assertNull(locator, repository.current())
        }
    }

    @Test
    fun automatic_invalidLocatorIsNotTheFallback() = runTest {
        settings.setLocator("KN4")
        phone.enabled = false
        val state = collect(repository())
        assertEquals(GpsStatus.LOCATION_OFF, state().gps)
        assertEquals(PositionOrigin.NONE, state().origin)
        assertNull(state().position)
        assertNull(state().locator)
        assertEquals("KN4", state().invalidLocator)
    }

    // ---- Phase A: location off is checked before a recent position is used ----

    @Test
    fun locationOff_withARecentPosition_isStillSaid() = runTest {
        store.saved = fix(BOGHICENI, START.minus(Duration.ofMinutes(3))) // recent: would be used as it is
        phone.enabled = false
        val state = collect(repository())

        assertEquals(GpsStatus.LOCATION_OFF, state().gps) // not hidden behind "OK"
        assertEquals(BOGHICENI, state().position) // the last position is still used
        assertEquals(0, phone.requests)
    }

    @Test
    fun locationOn_withARecentPosition_theTenMinuteRuleStays() = runTest {
        store.saved = fix(BOGHICENI, START.minus(Duration.ofMinutes(9)))
        phone.current = fix(LONDON)
        val state = collect(repository())

        assertEquals(GpsStatus.OK, state().gps)
        assertEquals(BOGHICENI, state().position)
        assertEquals(0, phone.requests) // no new reading
    }

    @Test
    fun locationOn_withAnOldPosition_aNewOneIsAsked() = runTest {
        store.saved = fix(LONDON, START.minus(Duration.ofMinutes(11)))
        phone.current = fix(BOGHICENI)
        val state = collect(repository())

        assertEquals(GpsStatus.OK, state().gps)
        assertEquals(BOGHICENI, state().position)
        assertEquals(1, phone.requests)
    }

    @Test
    fun locationTurnedOffLater_seenAtTheNextLook() = runTest {
        phone.current = fix(BOGHICENI)
        val repository = repository()
        val state = collect(repository)
        assertEquals(GpsStatus.OK, state().gps)

        phone.enabled = false // turned off a minute later: the position is still recent
        advance(Duration.ofMinutes(1))
        repository.refreshNow() // e.g. back in the app
        runCurrent()
        assertEquals(GpsStatus.LOCATION_OFF, state().gps)
        assertEquals(BOGHICENI, state().position)
    }

    // ---- Phase A: approximate <-> exact ----

    @Test
    fun exactToApproximate_theExactPositionIsNotUsedAgain() = runTest {
        store.saved = fix(BOGHICENI, START.minus(Duration.ofMinutes(2))) // exact, recent
        phone.precise = false // only approximate allowed now
        phone.current = fix(LONDON, approximate = true)
        val state = collect(repository())

        assertEquals(1, phone.requests) // a new position, despite the recent one
        assertEquals(LONDON, state().position)
        assertEquals(true, state().fix?.approximate)
        assertEquals(fix(LONDON, approximate = true), store.saved)
    }

    @Test
    fun approximateToExact_theApproximatePositionIsNotUsedAgain() = runTest {
        store.saved = fix(LONDON, START.minus(Duration.ofMinutes(2)), approximate = true)
        phone.precise = true
        phone.current = fix(BOGHICENI)
        val state = collect(repository())

        assertEquals(1, phone.requests)
        assertEquals(BOGHICENI, state().position)
        assertEquals(false, state().fix?.approximate)
    }

    @Test
    fun precisionChangedWhileTheAppRuns_newPositionAtTheNextLook() = runTest {
        phone.current = fix(BOGHICENI)
        val repository = repository()
        val state = collect(repository)
        assertEquals(1, phone.requests)

        phone.precise = false // changed in the Android settings, back in the app a minute later
        phone.current = fix(LONDON, START.plus(Duration.ofMinutes(1)), approximate = true)
        advance(Duration.ofMinutes(1))
        repository.refreshNow()
        runCurrent()
        assertEquals(2, phone.requests)
        assertEquals(LONDON, state().position)
        assertEquals("IO91wm", state().locator)
    }

    @Test
    fun precisionChanged_otherAppsPositionWithTheOldPrecisionIsNotUsedEither() = runTest {
        phone.precise = false
        phone.lastKnown = fix(BOGHICENI, START.minus(Duration.ofMinutes(1))) // exact, from before
        phone.current = fix(LONDON, approximate = true)
        val state = collect(repository())
        assertEquals(1, phone.requests)
        assertEquals(LONDON, state().position)
    }

    @Test
    fun precisionChanged_locationOff_theOldPositionIsNotShown() = runTest {
        store.saved = fix(BOGHICENI, START.minus(Duration.ofMinutes(2))) // exact
        phone.precise = false
        phone.enabled = false
        val state = collect(repository())
        assertEquals(GpsStatus.LOCATION_OFF, state().gps)
        assertEquals(PositionOrigin.LOCATOR, state().origin) // the locator, not the exact position from before
        assertEquals(kn46dw, state().position)
    }

    @Test
    fun precisionChanged_atStart_theOldPositionIsNotTheFirstOneShown() = runTest {
        store.saved = fix(BOGHICENI, START.minus(Duration.ofMinutes(2))) // exact
        phone.precise = false
        assertEquals(kn46dw, repository().current()) // before anything is asked
    }

    @Test
    fun samePrecision_approximate_theTenMinuteRuleStays() = runTest {
        store.saved = fix(LONDON, START.minus(Duration.ofMinutes(2)), approximate = true)
        phone.precise = false
        val state = collect(repository())
        assertEquals(0, phone.requests)
        assertEquals(LONDON, state().position)
    }
}
