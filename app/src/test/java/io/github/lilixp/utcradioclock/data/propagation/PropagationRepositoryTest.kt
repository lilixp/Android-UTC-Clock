package io.github.lilixp.utcradioclock.data.propagation

import io.github.lilixp.utcradioclock.data.propagation.PropagationState.Status.CURRENT
import io.github.lilixp.utcradioclock.data.propagation.PropagationState.Status.LOADING
import io.github.lilixp.utcradioclock.data.propagation.PropagationState.Status.STALE
import io.github.lilixp.utcradioclock.data.propagation.PropagationState.Status.UNAVAILABLE
import io.github.lilixp.utcradioclock.testing.FakeHttp
import io.github.lilixp.utcradioclock.testing.InMemoryPropagationCache
import io.github.lilixp.utcradioclock.testing.REAL_FEED
import io.github.lilixp.utcradioclock.testing.START
import io.github.lilixp.utcradioclock.testing.VirtualClock
import io.github.lilixp.utcradioclock.testing.feed
import io.github.lilixp.utcradioclock.testing.timeProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.Duration
import java.time.Instant

/**
 * The refresh rules, on a virtual clock (no real time, no real network): cache first, hourly
 * downloads, 15-minute retries, and never an invented value. Robolectric only for the XML reader.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PropagationRepositoryTest {

    private val states = mutableListOf<PropagationState>()

    private fun TestScope.collect(http: FakeHttp, cache: InMemoryPropagationCache) {
        val repository = PropagationRepository(
            http = http,
            cache = cache,
            time = timeProvider(VirtualClock(testScheduler, START)),
            io = StandardTestDispatcher(testScheduler),
        )
        backgroundScope.launch { repository.updates().collect { states += it } }
        runCurrent()
    }

    private fun TestScope.advance(duration: Duration) {
        advanceTimeBy(duration.toMillis())
        runCurrent()
    }

    @Test
    fun firstStart_loadingThenData_savedInTheCache() = runTest {
        val http = FakeHttp(REAL_FEED)
        val cache = InMemoryPropagationCache()
        collect(http, cache)

        assertEquals(listOf(LOADING, CURRENT), states.map { it.status })
        val data = states.last().conditions!!
        assertEquals(93.0, data.solarFlux!!, 0.0)
        assertEquals(Instant.parse("2026-10-01T05:29:00Z"), data.updated) // N0NBH's own time, UTC
        assertEquals(START, states.last().fetchedAt) // when the phone got it, UTC
        assertEquals("https://www.hamqsl.com/solarxml.php", http.lastUrl)
        assertEquals(CachedFeed(REAL_FEED, START), cache.feed)
    }

    @Test
    fun recentCache_shownAtOnce_noDownload() = runTest {
        val http = FakeHttp()
        collect(http, InMemoryPropagationCache(CachedFeed(REAL_FEED, START.minus(Duration.ofMinutes(20)))))

        assertEquals(listOf(CURRENT), states.map { it.status })
        assertEquals(0, http.calls) // N0NBH asks for at most one request an hour

        advance(Duration.ofMinutes(39))
        assertEquals(0, http.calls)
        advance(Duration.ofMinutes(1)) // the cached copy is now an hour old
        assertEquals(1, http.calls)
    }

    @Test
    fun oldCache_shownFirst_thenReplacedByNewData() = runTest {
        val newer = feed(solarFlux = "101")
        collect(FakeHttp(newer), InMemoryPropagationCache(CachedFeed(REAL_FEED, START.minus(Duration.ofHours(5)))))

        assertEquals(listOf(CURRENT, CURRENT), states.map { it.status }) // the saved copy, then the new one
        assertEquals(listOf(93.0, 101.0), states.map { it.conditions!!.solarFlux })
    }

    @Test
    fun noInternet_withCache_staleData_notInvented() = runTest {
        val cache = InMemoryPropagationCache(CachedFeed(REAL_FEED, START.minus(Duration.ofHours(3))))
        collect(FakeHttp(IOException("Unable to resolve host")), cache)

        val last = states.last()
        assertEquals(STALE, last.status)
        assertEquals(93.0, last.conditions!!.solarFlux!!, 0.0) // the last valid data, as saved
        assertEquals(START.minus(Duration.ofHours(3)), last.fetchedAt)
        assertEquals(0, cache.saves)
    }

    @Test
    fun noInternet_noCache_unavailable() = runTest {
        collect(FakeHttp(IOException("no network")), InMemoryPropagationCache())
        assertEquals(listOf(LOADING, UNAVAILABLE), states.map { it.status })
        assertNull(states.last().conditions)
    }

    @Test
    fun timeout_isLikeNoInternet() = runTest {
        collect(FakeHttp(SocketTimeoutException("timeout")), InMemoryPropagationCache())
        assertEquals(UNAVAILABLE, states.last().status)
    }

    @Test
    fun invalidResponse_isAFailure_andTheCacheIsKept() = runTest {
        val cache = InMemoryPropagationCache(CachedFeed(REAL_FEED, START.minus(Duration.ofHours(2))))
        collect(FakeHttp("<html>503 Service Unavailable</html>"), cache)

        assertEquals(STALE, states.last().status)
        assertEquals(REAL_FEED, cache.feed!!.xml)
        assertEquals(0, cache.saves)
    }

    @Test
    fun afterAFailure_retriedIn15Minutes_thenCurrentAgain() = runTest {
        val http = FakeHttp(IOException("no network"), REAL_FEED)
        collect(http, InMemoryPropagationCache())
        assertEquals(UNAVAILABLE, states.last().status)

        advance(Duration.ofMinutes(14))
        assertEquals(1, http.calls)
        advance(Duration.ofMinutes(1))
        assertEquals(2, http.calls)
        assertEquals(CURRENT, states.last().status)
    }

    @Test
    fun retriesKeepSayingStale_noFlicker() = runTest {
        val http = FakeHttp(IOException("a"), IOException("b"), IOException("c"))
        collect(http, InMemoryPropagationCache(CachedFeed(REAL_FEED, START.minus(Duration.ofHours(2)))))
        advance(Duration.ofMinutes(30)) // two more failed attempts
        assertEquals(3, http.calls)
        assertEquals(listOf(CURRENT, STALE), states.map { it.status }) // never back to CURRENT in between
    }

    @Test
    fun whileShown_updatedHourly() = runTest {
        val http = FakeHttp(REAL_FEED, feed(solarFlux = "95"), feed(solarFlux = "97"))
        collect(http, InMemoryPropagationCache())
        advance(Duration.ofHours(2))
        assertEquals(3, http.calls)
        assertEquals(97.0, states.last().conditions!!.solarFlux!!, 0.0)
    }
}
