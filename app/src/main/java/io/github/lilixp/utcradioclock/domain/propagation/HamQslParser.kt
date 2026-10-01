package io.github.lilixp.utcradioclock.domain.propagation

import android.util.Xml
import io.github.lilixp.utcradioclock.domain.model.BandCondition
import io.github.lilixp.utcradioclock.domain.model.BandGroup
import io.github.lilixp.utcradioclock.domain.model.ConditionLevel
import io.github.lilixp.utcradioclock.domain.model.SolarConditions
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.StringReader
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Reads the N0NBH XML feed (https://www.hamqsl.com/solarxml.php), e.g.
 *
 * ```
 * <solar><solardata>
 *   <updated> 01 Oct 2026 0529 GMT</updated>
 *   <solarflux>93</solarflux> <aindex> 3</aindex> <kindex> 0</kindex>
 *   <calculatedconditions>
 *     <band name="80m-40m" time="day">Good</band> … <band name="12m-10m" time="night">Poor</band>
 *   </calculatedconditions>
 * </solardata></solar>
 * ```
 *
 * Values may have spaces around them or say "No Report"; such values become null. The DOCTYPE is not
 * processed, so the XML cannot pull in anything else.
 */
object HamQslParser {

    private val updatedFormat = DateTimeFormatter.ofPattern("dd MMM yyyy HHmm", Locale.ENGLISH)

    /** The data, or null if [xml] is not an N0NBH feed or carries none of SFI, K, A and the bands. */
    fun parse(xml: String): SolarConditions? {
        val values = mutableMapOf<String, String>()
        val day = mutableMapOf<BandGroup, ConditionLevel?>()
        val night = mutableMapOf<BandGroup, ConditionLevel?>()
        var isSolarFeed = false
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xml))
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                when (parser.name) {
                    "solardata" -> isSolarFeed = true
                    "band" -> {
                        val group = BandGroup.entries.firstOrNull { it.sourceName == parser.getAttributeValue(null, "name") }
                        val time = parser.getAttributeValue(null, "time")
                        val level = level(parser.nextText())
                        if (group != null) {
                            when (time) {
                                "day" -> day[group] = level
                                "night" -> night[group] = level
                            }
                        }
                    }
                    "updated", "solarflux", "kindex", "aindex" -> values[parser.name] = parser.nextText().trim()
                }
            }
        } catch (_: XmlPullParserException) {
            return null
        } catch (_: IOException) {
            return null
        }
        if (!isSolarFeed) return null
        val bands = BandGroup.entries
            .filter { day[it] != null || night[it] != null }
            .associateWith { BandCondition(day[it], night[it]) }
        val conditions = SolarConditions(
            updated = values["updated"]?.let(::updatedInstant),
            solarFlux = values["solarflux"]?.let(::number),
            kIndex = values["kindex"]?.let(::number),
            aIndex = values["aindex"]?.let(::number),
            bands = bands,
        )
        val empty = conditions.solarFlux == null && conditions.kIndex == null && conditions.aIndex == null && bands.isEmpty()
        return conditions.takeUnless { empty }
    }

    private fun level(text: String): ConditionLevel? = when (text.trim()) {
        "Good" -> ConditionLevel.GOOD
        "Fair" -> ConditionLevel.FAIR
        "Poor" -> ConditionLevel.POOR
        else -> null
    }

    /** A finite number, or null for "", "No Report" and the like. */
    private fun number(text: String): Double? = text.toDoubleOrNull()?.takeIf { it.isFinite() }

    /** " 01 Oct 2026 0529 GMT" → 2026-10-01T05:29:00Z, or null if the format is different. */
    private fun updatedInstant(text: String): Instant? = try {
        LocalDateTime.parse(text.removeSuffix("GMT").trim(), updatedFormat).toInstant(ZoneOffset.UTC)
    } catch (_: DateTimeParseException) {
        null
    }
}
