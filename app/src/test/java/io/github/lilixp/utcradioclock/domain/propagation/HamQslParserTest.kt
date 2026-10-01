package io.github.lilixp.utcradioclock.domain.propagation

import io.github.lilixp.utcradioclock.domain.model.BandCondition
import io.github.lilixp.utcradioclock.domain.model.BandGroup
import io.github.lilixp.utcradioclock.domain.model.ConditionLevel.FAIR
import io.github.lilixp.utcradioclock.domain.model.ConditionLevel.GOOD
import io.github.lilixp.utcradioclock.domain.model.ConditionLevel.POOR
import io.github.lilixp.utcradioclock.testing.REAL_FEED
import io.github.lilixp.utcradioclock.testing.feed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

/** The XML reader runs on Android's XmlPullParser, which Robolectric provides on the PC. */
@RunWith(RobolectricTestRunner::class)
class HamQslParserTest {

    @Test
    fun realFeed_allValues() {
        val data = HamQslParser.parse(REAL_FEED)!!
        assertEquals(Instant.parse("2026-10-01T05:29:00Z"), data.updated)
        assertEquals(93.0, data.solarFlux!!, 0.0)
        assertEquals(0.0, data.kIndex!!, 0.0) // " 0": spaces around values are normal in this feed
        assertEquals(3.0, data.aIndex!!, 0.0)
        assertEquals(
            mapOf(
                BandGroup.BANDS_80_40 to BandCondition(GOOD, GOOD),
                BandGroup.BANDS_30_20 to BandCondition(GOOD, GOOD),
                BandGroup.BANDS_17_15 to BandCondition(FAIR, FAIR),
                BandGroup.BANDS_12_10 to BandCondition(POOR, POOR),
            ),
            data.bands,
        )
    }

    @Test
    fun dayAndNightAreReadSeparately() {
        val data = HamQslParser.parse(feed())!!
        assertEquals(BandCondition(FAIR, GOOD), data.bands[BandGroup.BANDS_80_40])
        assertEquals(BandCondition(FAIR, POOR), data.bands[BandGroup.BANDS_17_15])
    }

    @Test
    fun decimalValuesAreKeptAsPublished() {
        val data = HamQslParser.parse(feed(solarFlux = "152.4", kIndex = "2.33", aIndex = "12"))!!
        assertEquals(152.4, data.solarFlux!!, 0.0)
        assertEquals(2.33, data.kIndex!!, 0.0)
    }

    @Test
    fun noReportAndEmptyValuesBecomeNull_notZero() {
        val data = HamQslParser.parse(feed(kIndex = "No Report", aIndex = "", updated = "unknown"))!!
        assertNull(data.kIndex)
        assertNull(data.aIndex)
        assertNull(data.updated)
        assertEquals(93.0, data.solarFlux!!, 0.0)
    }

    @Test
    fun incompleteBands_onlyWhatIsThere() {
        val data = HamQslParser.parse(
            feed(bands = """<band name="30m-20m" time="day">Good</band><band name="12m-10m" time="night">Closed</band>"""),
        )!!
        assertEquals(mapOf(BandGroup.BANDS_30_20 to BandCondition(GOOD, null)), data.bands)
    }

    @Test
    fun feedWithNoValuesAtAll_isNotData() {
        assertNull(HamQslParser.parse(feed(solarFlux = "", kIndex = "", aIndex = "", bands = "")))
    }

    @Test
    fun invalidResponses_areNotData() {
        assertNull(HamQslParser.parse(""))
        assertNull(HamQslParser.parse("<html><body>503 Service Unavailable</body></html>"))
        assertNull(HamQslParser.parse("<solar><solardata><solarflux>93")) // cut off
        assertNull(HamQslParser.parse("{\"sfi\": 93}"))
    }
}
