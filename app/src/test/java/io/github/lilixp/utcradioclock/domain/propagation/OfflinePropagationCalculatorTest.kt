package io.github.lilixp.utcradioclock.domain.propagation

import io.github.lilixp.utcradioclock.domain.location.Maidenhead
import io.github.lilixp.utcradioclock.domain.model.ConditionLevel
import io.github.lilixp.utcradioclock.domain.model.ConditionLevel.FAIR
import io.github.lilixp.utcradioclock.domain.model.ConditionLevel.GOOD
import io.github.lilixp.utcradioclock.domain.model.ConditionLevel.POOR
import io.github.lilixp.utcradioclock.domain.model.DayPhase
import io.github.lilixp.utcradioclock.domain.model.DayPhase.DAY
import io.github.lilixp.utcradioclock.domain.model.DayPhase.NIGHT
import io.github.lilixp.utcradioclock.domain.model.DayPhase.TWILIGHT
import io.github.lilixp.utcradioclock.domain.model.HfBand
import io.github.lilixp.utcradioclock.domain.solar.SolarCalculator
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * The offline estimate for the ten HF bands: Lilian's rules (from the recovered version), fed with
 * N0NBH's SFI and K, and "unknown" (null) whenever the data a band needs is missing.
 */
class OfflinePropagationCalculatorTest {

    /** The ten levels, 160 m first. */
    private fun levels(phase: DayPhase?, sfi: Double?, k: Double?): List<ConditionLevel?> =
        OfflinePropagationCalculator.estimate(phase, sfi, k).map { it.level }

    private fun level(band: HfBand, phase: DayPhase, sfi: Double?, k: Double? = 1.0) =
        OfflinePropagationCalculator.level(band, phase, sfi, k)

    @Test
    fun theTenBands_inOrder() {
        val bands = OfflinePropagationCalculator.estimate(DAY, 120.0, 1.0).map { it.band }
        assertEquals(HfBand.entries.toList(), bands)
        assertEquals(listOf(160, 80, 60, 40, 30, 20, 17, 15, 12, 10), bands.map { it.meters })
        assertEquals("160m", HfBand.BAND_160M.label)
    }

    //                                            160m  80m   60m   40m   30m   20m   17m   15m   12m   10m

    @Test
    fun day_highFlux() = assertEquals(listOf(POOR, POOR, FAIR, FAIR, GOOD, GOOD, GOOD, GOOD, GOOD, GOOD), levels(DAY, 150.0, 1.0))

    @Test
    fun day_lowFlux() = assertEquals(listOf(POOR, POOR, FAIR, FAIR, FAIR, GOOD, FAIR, FAIR, POOR, POOR), levels(DAY, 70.0, 1.0))

    @Test
    fun night_highFlux() = assertEquals(listOf(GOOD, GOOD, GOOD, GOOD, GOOD, GOOD, FAIR, POOR, POOR, POOR), levels(NIGHT, 150.0, 1.0))

    @Test
    fun night_lowFlux() = assertEquals(listOf(GOOD, GOOD, GOOD, GOOD, GOOD, POOR, POOR, POOR, POOR, POOR), levels(NIGHT, 70.0, 1.0))

    @Test
    fun twilight_highFlux() = assertEquals(listOf(FAIR, GOOD, GOOD, GOOD, GOOD, GOOD, FAIR, FAIR, POOR, POOR), levels(TWILIGHT, 150.0, 1.0))

    @Test
    fun twilight_lowFlux() = assertEquals(listOf(FAIR, GOOD, GOOD, GOOD, GOOD, GOOD, FAIR, POOR, POOR, POOR), levels(TWILIGHT, 80.0, 1.0))

    // ---- Missing data: unknown, never estimated with made-up values ----

    @Test
    fun noSfi_byDay_onlyTheBandsThatNeedItAreUnknown() =
        assertEquals(listOf(POOR, POOR, FAIR, FAIR, null, GOOD, null, null, null, null), levels(DAY, null, 1.0))

    @Test
    fun noSfi_atNight_only20And17AreUnknown() =
        assertEquals(listOf(GOOD, GOOD, GOOD, GOOD, GOOD, null, null, POOR, POOR, POOR), levels(NIGHT, null, 1.0))

    @Test
    fun noSfi_atTwilight_only15IsUnknown() =
        assertEquals(listOf(FAIR, GOOD, GOOD, GOOD, GOOD, GOOD, FAIR, null, POOR, POOR), levels(TWILIGHT, null, 1.0))

    @Test
    fun noK_everyBandUnknown() {
        for (phase in DayPhase.entries) assertEquals(List(10) { null }, levels(phase, 150.0, null))
    }

    @Test
    fun noPhase_everyBandUnknown() = assertEquals(List(10) { null }, levels(null, 150.0, 1.0))

    @Test
    fun nothingAtAll_everyBandUnknown() = assertEquals(List(10) { null }, levels(null, null, null))

    // ---- K: one level lower from 4, everything poor from 7 ----

    @Test
    fun k3_noChange_k4_oneLevelLower() {
        assertEquals(levels(DAY, 150.0, 0.0), levels(DAY, 150.0, 3.0))
        assertEquals(listOf(POOR, POOR, POOR, POOR, FAIR, FAIR, FAIR, FAIR, FAIR, FAIR), levels(DAY, 150.0, 4.0))
        assertEquals(levels(DAY, 150.0, 4.0), levels(DAY, 150.0, 6.0))
    }

    @Test
    fun k7_everyBandPoor_evenWithoutSfi() {
        for (phase in DayPhase.entries) {
            assertEquals(List(10) { POOR }, levels(phase, 150.0, 7.0))
            assertEquals(List(10) { POOR }, levels(phase, null, 9.0))
        }
    }

    @Test
    fun k4_withoutSfi_unknownStaysUnknown() =
        assertEquals(listOf(POOR, POOR, POOR, POOR, null, FAIR, null, null, null, null), levels(DAY, null, 4.0))

    // ---- The limits of every SFI threshold ----

    @Test
    fun sfiThresholds() {
        assertEquals(FAIR, level(HfBand.BAND_30M, DAY, 89.9)); assertEquals(GOOD, level(HfBand.BAND_30M, DAY, 90.0))
        assertEquals(POOR, level(HfBand.BAND_20M, NIGHT, 89.9)); assertEquals(FAIR, level(HfBand.BAND_20M, NIGHT, 90.0))
        assertEquals(FAIR, level(HfBand.BAND_20M, NIGHT, 119.9)); assertEquals(GOOD, level(HfBand.BAND_20M, NIGHT, 120.0))
        assertEquals(FAIR, level(HfBand.BAND_17M, DAY, 89.9)); assertEquals(GOOD, level(HfBand.BAND_17M, DAY, 90.0))
        assertEquals(POOR, level(HfBand.BAND_17M, NIGHT, 139.9)); assertEquals(FAIR, level(HfBand.BAND_17M, NIGHT, 140.0))
        assertEquals(FAIR, level(HfBand.BAND_15M, DAY, 89.9)); assertEquals(GOOD, level(HfBand.BAND_15M, DAY, 90.0))
        assertEquals(POOR, level(HfBand.BAND_15M, TWILIGHT, 99.9)); assertEquals(FAIR, level(HfBand.BAND_15M, TWILIGHT, 100.0))
        assertEquals(POOR, level(HfBand.BAND_12M, DAY, 89.9)); assertEquals(FAIR, level(HfBand.BAND_12M, DAY, 90.0))
        assertEquals(FAIR, level(HfBand.BAND_12M, DAY, 109.9)); assertEquals(GOOD, level(HfBand.BAND_12M, DAY, 110.0))
        assertEquals(POOR, level(HfBand.BAND_10M, DAY, 94.9)); assertEquals(FAIR, level(HfBand.BAND_10M, DAY, 95.0))
        assertEquals(FAIR, level(HfBand.BAND_10M, DAY, 119.9)); assertEquals(GOOD, level(HfBand.BAND_10M, DAY, 120.0))
    }

    @Test
    fun sfiNeverMattersForTheLowBands() {
        for (band in listOf(HfBand.BAND_160M, HfBand.BAND_80M, HfBand.BAND_60M, HfBand.BAND_40M)) {
            for (phase in DayPhase.entries) assertEquals(level(band, phase, 60.0), level(band, phase, 300.0))
        }
    }

    // ---- With a real day at the station ----

    @Test
    fun realDayAtKn46dw_with1October2026Data() {
        val day = SolarCalculator.calculate(LocalDate.of(2026, 10, 1), ZoneId.of("Europe/Chisinau"), Maidenhead.toPosition("KN46dw")!!)
        // N0NBH, 1 October 2026: SFI 93, K 0
        val noon = levels(day.phaseAt(day.solarNoon), 93.0, 0.0)
        assertEquals(listOf(POOR, POOR, FAIR, FAIR, GOOD, GOOD, GOOD, GOOD, FAIR, POOR), noon)
        val evening = levels(day.phaseAt(day.sunset!!.plusSeconds(600)), 93.0, 0.0)
        assertEquals(listOf(FAIR, GOOD, GOOD, GOOD, GOOD, GOOD, FAIR, POOR, POOR, POOR), evening)
        val night = levels(day.phaseAt(day.civilDusk!!.plusSeconds(7200)), 93.0, 0.0)
        assertEquals(listOf(GOOD, GOOD, GOOD, GOOD, GOOD, FAIR, POOR, POOR, POOR, POOR), night)
    }
}
