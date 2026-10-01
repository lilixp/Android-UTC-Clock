package io.github.lilixp.utcradioclock.domain.model

import java.time.Instant

/**
 * How good conditions are. N0NBH classifies the HF bands with exactly these three words
 * (Good / Fair / Poor); SFI, K and A get the same three levels from documented scales.
 */
enum class ConditionLevel { GOOD, FAIR, POOR }

/**
 * The ten HF amateur bands, for the offline estimate ([BandEstimate]). [label] is what the band box
 * shows ("160m"), [frequencies] the band in MHz (IARU Region 1), as shown in the band explanation.
 */
enum class HfBand(val meters: Int, val frequencies: String) {
    BAND_160M(160, "1.8–2.0 MHz"),
    BAND_80M(80, "3.5–3.8 MHz"),
    BAND_60M(60, "5.3 MHz"),
    BAND_40M(40, "7.0–7.2 MHz"),
    BAND_30M(30, "10.1 MHz"),
    BAND_20M(20, "14.0–14.35 MHz"),
    BAND_17M(17, "18.068–18.168 MHz"),
    BAND_15M(15, "21.0–21.45 MHz"),
    BAND_12M(12, "24.89–24.99 MHz"),
    BAND_10M(10, "28.0–29.7 MHz"),
    ;

    val label: String get() = "${meters}m"
}

/**
 * The offline estimate for one HF band, calculated on the phone (not published by N0NBH);
 * [level] is null ("Unknown") when the data it needs is missing.
 */
data class BandEstimate(val band: HfBand, val level: ConditionLevel?)

/**
 * The four band groups for which N0NBH publishes conditions. [range] is the same in every language
 * ("80-40"), [label] is what the band box shows ("80-40m"), [sourceName] is the name in the feed.
 */
enum class BandGroup(val range: String, val sourceName: String) {
    BANDS_80_40("80-40", "80m-40m"),
    BANDS_30_20("30-20", "30m-20m"),
    BANDS_17_15("17-15", "17m-15m"),
    BANDS_12_10("12-10", "12m-10m"),
    ;

    val label: String get() = "${range}m"
}

/** Conditions of one band group by day and by night, as calculated by N0NBH; null if not reported. */
data class BandCondition(val day: ConditionLevel?, val night: ConditionLevel?)

/**
 * The solar-terrestrial data published by N0NBH (hamqsl.com). Every value is exactly what the source
 * gave, or null when it was missing or "No Report"; nothing is estimated.
 */
data class SolarConditions(
    /** When N0NBH last updated the data (UTC), from the feed itself. */
    val updated: Instant?,
    /** Solar flux index (10.7 cm), e.g. 93. */
    val solarFlux: Double?,
    /** Planetary K index, 0–9 (N0NBH publishes whole numbers). */
    val kIndex: Double?,
    /** Planetary A index. */
    val aIndex: Double?,
    val bands: Map<BandGroup, BandCondition>,
)
