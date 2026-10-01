package io.github.lilixp.utcradioclock.data.propagation

import io.github.lilixp.utcradioclock.data.time.TimeProvider
import io.github.lilixp.utcradioclock.domain.model.SolarConditions
import io.github.lilixp.utcradioclock.domain.propagation.HamQslParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant

/** What the PROPAGATION card can show. */
data class PropagationState(
    val status: Status,
    /** The data to show: fresh, or the last valid data from the cache; null when there is none. */
    val conditions: SolarConditions? = null,
    /** When the phone downloaded [conditions] (UTC). */
    val fetchedAt: Instant? = null,
) {
    enum class Status {
        /** No data yet, the first download is running. */
        LOADING,

        /** Data downloaded within the last hour, or older data while a new download is running. */
        CURRENT,

        /** The last download failed: the data shown is older (from the cache). */
        STALE,

        /** The download failed and there is nothing saved: nothing to show. */
        UNAVAILABLE,
    }
}

/**
 * N0NBH solar-terrestrial data (hamqsl.com): the saved copy first, then a fresh one.
 *
 * N0NBH asks feed users to update at most once an hour (the flux changes hourly, the rest every three
 * hours), so the feed is downloaded only when the saved copy is an hour old, then hourly; after a
 * failure it is tried again after 15 minutes. It runs only while someone collects [updates] (the
 * dashboard on screen): nothing is downloaded in the background.
 */
class PropagationRepository(
    private val http: HttpClient,
    private val cache: PropagationCache,
    private val time: TimeProvider,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    fun updates(): Flow<PropagationState> = flow {
        var saved = cache.load()?.let { feed -> HamQslParser.parse(feed.xml)?.let { Saved(it, feed.fetchedAt) } }
        var failing = false // the last download failed: keep saying so while trying again
        while (true) {
            val age = saved?.let { Duration.between(it.fetchedAt, time.now()) }
            if (saved != null && age != null && !age.isNegative && age < REFRESH_INTERVAL) {
                emit(PropagationState(PropagationState.Status.CURRENT, saved.conditions, saved.fetchedAt))
                delay((REFRESH_INTERVAL - age).toMillis())
                continue
            }
            // Show what there is while the new data is on its way
            emit(
                when {
                    saved == null && failing -> PropagationState(PropagationState.Status.UNAVAILABLE)
                    saved == null -> PropagationState(PropagationState.Status.LOADING)
                    failing -> PropagationState(PropagationState.Status.STALE, saved.conditions, saved.fetchedAt)
                    else -> PropagationState(PropagationState.Status.CURRENT, saved.conditions, saved.fetchedAt)
                },
            )
            val fresh = download()
            failing = fresh == null
            if (fresh != null) {
                saved = fresh
                emit(PropagationState(PropagationState.Status.CURRENT, fresh.conditions, fresh.fetchedAt))
                delay(REFRESH_INTERVAL.toMillis())
            } else {
                emit(
                    if (saved == null) {
                        PropagationState(PropagationState.Status.UNAVAILABLE)
                    } else {
                        PropagationState(PropagationState.Status.STALE, saved.conditions, saved.fetchedAt)
                    },
                )
                delay(RETRY_INTERVAL.toMillis())
            }
        }
    }.distinctUntilChanged()

    /**
     * A new valid feed, saved in the cache; null on any failure (no network, timeout, HTTP error, data
     * that is not an N0NBH feed). The cache then stays as it was. Cancellation is passed on.
     */
    private suspend fun download(): Saved? = withContext(io) {
        try {
            val xml = http.get(FEED_URL)
            val conditions = HamQslParser.parse(xml) ?: return@withContext null
            val fetchedAt = time.now()
            cache.save(CachedFeed(xml, fetchedAt))
            Saved(conditions, fetchedAt)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private data class Saved(val conditions: SolarConditions, val fetchedAt: Instant)

    companion object {
        /** N0NBH's XML feed, offered by Paul Herrman, N0NBH, for use in other programs (credit appreciated). */
        const val FEED_URL = "https://www.hamqsl.com/solarxml.php"
        val REFRESH_INTERVAL: Duration = Duration.ofHours(1)
        val RETRY_INTERVAL: Duration = Duration.ofMinutes(15)
    }
}
