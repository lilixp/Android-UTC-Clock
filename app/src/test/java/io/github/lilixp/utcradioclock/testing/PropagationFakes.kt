package io.github.lilixp.utcradioclock.testing

import io.github.lilixp.utcradioclock.data.propagation.CachedFeed
import io.github.lilixp.utcradioclock.data.propagation.HttpClient
import io.github.lilixp.utcradioclock.data.propagation.PropagationCache
import java.io.IOException

/**
 * The real N0NBH feed as downloaded on 1 October 2026 at 05:29 UTC (app/src/test/resources/hamqsl):
 * SFI 93, A 3, K 0; 80-40m and 30-20m Good, 17-15m Fair, 12-10m Poor, by day and by night.
 */
val REAL_FEED: String by lazy {
    val stream = checkNotNull(PropagationFakes::class.java.getResourceAsStream("/hamqsl/solarxml-2026-10-01.xml"))
    stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
}

/** A feed built in the test, for values the real one does not have. */
fun feed(
    solarFlux: String = "93",
    kIndex: String = " 0",
    aIndex: String = " 3",
    bands: String = """
        <band name="80m-40m" time="day">Fair</band>
        <band name="30m-20m" time="day">Good</band>
        <band name="17m-15m" time="day">Fair</band>
        <band name="12m-10m" time="day">Poor</band>
        <band name="80m-40m" time="night">Good</band>
        <band name="30m-20m" time="night">Good</band>
        <band name="17m-15m" time="night">Poor</band>
        <band name="12m-10m" time="night">Poor</band>
    """,
    updated: String = " 30 Sep 2026 1500 GMT",
) = """<?xml version="1.0" encoding="UTF-8" ?>
<solar><solardata>
  <source url="http://www.hamqsl.com/solar.html">N0NBH</source>
  <updated>$updated</updated>
  <solarflux>$solarFlux</solarflux><aindex>$aIndex</aindex><kindex>$kIndex</kindex>
  <calculatedconditions>$bands</calculatedconditions>
</solardata></solar>"""

/** An Internet that answers from a list: a text is a response, an exception is thrown. Counts calls. */
class FakeHttp(vararg answers: Any) : HttpClient {
    private val queue = ArrayDeque(answers.toList())
    var calls = 0
        private set
    var lastUrl: String? = null
        private set

    override fun get(url: String): String {
        calls++
        lastUrl = url
        return when (val answer = queue.removeFirstOrNull() ?: IOException("no network")) {
            is String -> answer
            is Exception -> throw answer
            else -> error("unexpected answer $answer")
        }
    }
}

/** No Internet at all. */
val NO_INTERNET = HttpClient { throw IOException("Unable to resolve host \"www.hamqsl.com\"") }

class InMemoryPropagationCache(var feed: CachedFeed? = null) : PropagationCache {
    var saves = 0
        private set

    override fun load() = feed

    override fun save(feed: CachedFeed) {
        saves++
        this.feed = feed
    }
}

private object PropagationFakes
