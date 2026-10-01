package io.github.lilixp.utcradioclock.data.propagation

import androidx.test.core.app.ApplicationProvider
import io.github.lilixp.utcradioclock.testing.REAL_FEED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

/** The real SharedPreferences cache; a new object over the same storage is like a restart. */
@RunWith(RobolectricTestRunner::class)
class SharedPreferencesPropagationCacheTest {

    private fun restart() = SharedPreferencesPropagationCache(ApplicationProvider.getApplicationContext())

    @Test
    fun emptyAtFirst() {
        assertNull(restart().load())
    }

    @Test
    fun savedFeedSurvivesARestart() {
        val feed = CachedFeed(REAL_FEED, Instant.parse("2026-10-01T05:31:12Z"))
        restart().save(feed)
        assertEquals(feed, restart().load())
    }

    @Test
    fun aNewerFeedReplacesTheOlder() {
        restart().save(CachedFeed("old", Instant.parse("2026-10-01T04:00:00Z")))
        restart().save(CachedFeed(REAL_FEED, Instant.parse("2026-10-01T05:31:12Z")))
        assertEquals(Instant.parse("2026-10-01T05:31:12Z"), restart().load()!!.fetchedAt)
    }
}
