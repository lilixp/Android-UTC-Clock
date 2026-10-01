package io.github.lilixp.utcradioclock.data.propagation

import android.content.Context
import androidx.core.content.edit
import java.time.Instant

/** The last valid N0NBH feed and when the phone downloaded it (UTC). */
data class CachedFeed(val xml: String, val fetchedAt: Instant)

/** Keeps the last valid feed, so the card has something to show at start and without Internet. */
interface PropagationCache {
    fun load(): CachedFeed?
    fun save(feed: CachedFeed)
}

/**
 * The cache in its own SharedPreferences file (about 2 KB). It is left out of the backup rules on
 * purpose: these are short-lived data that are downloaded again anyway.
 */
class SharedPreferencesPropagationCache(context: Context) : PropagationCache {

    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun load(): CachedFeed? {
        val xml = preferences.getString(KEY_XML, null) ?: return null
        val fetchedAt = preferences.getLong(KEY_FETCHED_AT, -1).takeIf { it >= 0 } ?: return null
        return CachedFeed(xml, Instant.ofEpochMilli(fetchedAt))
    }

    override fun save(feed: CachedFeed) {
        preferences.edit {
            putString(KEY_XML, feed.xml)
            putLong(KEY_FETCHED_AT, feed.fetchedAt.toEpochMilli())
        }
    }

    private companion object {
        const val FILE_NAME = "propagation_cache"
        const val KEY_XML = "xml"
        const val KEY_FETCHED_AT = "fetched_at"
    }
}
