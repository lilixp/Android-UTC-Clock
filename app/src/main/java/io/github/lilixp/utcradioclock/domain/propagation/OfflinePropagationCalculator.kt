package io.github.lilixp.utcradioclock.domain.propagation

import io.github.lilixp.utcradioclock.domain.model.BandEstimate
import io.github.lilixp.utcradioclock.domain.model.ConditionLevel
import io.github.lilixp.utcradioclock.domain.model.ConditionLevel.FAIR
import io.github.lilixp.utcradioclock.domain.model.ConditionLevel.GOOD
import io.github.lilixp.utcradioclock.domain.model.ConditionLevel.POOR
import io.github.lilixp.utcradioclock.domain.model.DayPhase
import io.github.lilixp.utcradioclock.domain.model.DayPhase.DAY
import io.github.lilixp.utcradioclock.domain.model.DayPhase.NIGHT
import io.github.lilixp.utcradioclock.domain.model.DayPhase.TWILIGHT
import io.github.lilixp.utcradioclock.domain.model.HfBand

/**
 * An offline estimate for each of the ten HF bands (160–10 m), made on the phone: a simple rule of
 * thumb from day, twilight or night at the station ([DayPhase], from the same [io.github.lilixp.utcradioclock.domain.model.SolarDay]
 * as everything else), the solar flux (SFI) and the K index, both as N0NBH published them. It is not
 * N0NBH data and is never shown as such.
 *
 * Nothing is invented: without the phase or K the estimate is unknown (null) for every band, and without
 * SFI it is unknown for the bands whose rule needs SFI at that time of day.
 *
 * The rules (Lilian's, kept as written):
 * - low bands (160–40 m) are open at night and twilight, absorbed by day;
 * - 30 m is open nearly always, by day with SFI ≥ 90;
 * - 20 m is open by day and twilight, at night depending on SFI (≥ 120 good, ≥ 90 fair);
 * - 17–10 m need the Sun and enough flux (thresholds 90–140 below);
 * - geomagnetic activity: K 4–6 lowers every band by one level, K ≥ 7 makes every band poor.
 */
object OfflinePropagationCalculator {

    /** The ten bands in order (160 m first), each with its estimated level or null. */
    fun estimate(phase: DayPhase?, solarFlux: Double?, kIndex: Double?): List<BandEstimate> =
        HfBand.entries.map { BandEstimate(it, level(it, phase, solarFlux, kIndex)) }

    /** One band; null when the data it needs is missing. */
    fun level(band: HfBand, phase: DayPhase?, solarFlux: Double?, kIndex: Double?): ConditionLevel? {
        if (phase == null || kIndex == null) return null
        if (kIndex >= SEVERE_K) return POOR // a strong geomagnetic storm closes everything, whatever the flux
        val base = baseLevel(band, phase, solarFlux) ?: return null
        return if (kIndex >= ACTIVE_K) base.lower() else base
    }

    private fun baseLevel(band: HfBand, phase: DayPhase, sfi: Double?): ConditionLevel? {
        /** The level for the first threshold that SFI reaches, else [otherwise]; null without SFI. */
        fun bySfi(vararg thresholds: Pair<Double, ConditionLevel>, otherwise: ConditionLevel): ConditionLevel? =
            sfi?.let { flux -> thresholds.firstOrNull { flux >= it.first }?.second ?: otherwise }

        return when (band) {
            HfBand.BAND_160M -> when (phase) {
                NIGHT -> GOOD
                TWILIGHT -> FAIR
                DAY -> POOR
            }
            HfBand.BAND_80M -> when (phase) {
                NIGHT, TWILIGHT -> GOOD
                DAY -> POOR
            }
            HfBand.BAND_60M, HfBand.BAND_40M -> when (phase) {
                NIGHT, TWILIGHT -> GOOD
                DAY -> FAIR
            }
            HfBand.BAND_30M -> when (phase) {
                NIGHT, TWILIGHT -> GOOD
                DAY -> bySfi(90.0 to GOOD, otherwise = FAIR)
            }
            HfBand.BAND_20M -> when (phase) {
                DAY, TWILIGHT -> GOOD
                NIGHT -> bySfi(120.0 to GOOD, 90.0 to FAIR, otherwise = POOR)
            }
            HfBand.BAND_17M -> when (phase) {
                DAY -> bySfi(90.0 to GOOD, otherwise = FAIR)
                TWILIGHT -> FAIR
                NIGHT -> bySfi(140.0 to FAIR, otherwise = POOR)
            }
            HfBand.BAND_15M -> when (phase) {
                DAY -> bySfi(90.0 to GOOD, otherwise = FAIR)
                TWILIGHT -> bySfi(100.0 to FAIR, otherwise = POOR)
                NIGHT -> POOR
            }
            HfBand.BAND_12M -> when (phase) {
                DAY -> bySfi(110.0 to GOOD, 90.0 to FAIR, otherwise = POOR)
                TWILIGHT, NIGHT -> POOR
            }
            HfBand.BAND_10M -> when (phase) {
                DAY -> bySfi(120.0 to GOOD, 95.0 to FAIR, otherwise = POOR)
                TWILIGHT, NIGHT -> POOR
            }
        }
    }

    private fun ConditionLevel.lower(): ConditionLevel = when (this) {
        GOOD -> FAIR
        FAIR, POOR -> POOR
    }

    /** K from which bands are one level worse (NOAA: 4 active, 5 and 6 minor/moderate storm). */
    private const val ACTIVE_K = 4.0

    /** K from which every band is poor (NOAA: 7 strong storm). */
    private const val SEVERE_K = 7.0
}
