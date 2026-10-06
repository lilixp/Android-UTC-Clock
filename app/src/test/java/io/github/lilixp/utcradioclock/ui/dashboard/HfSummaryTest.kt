package io.github.lilixp.utcradioclock.ui.dashboard

import io.github.lilixp.utcradioclock.data.propagation.PropagationState
import io.github.lilixp.utcradioclock.domain.model.BandGroup
import io.github.lilixp.utcradioclock.domain.model.ConditionLevel
import io.github.lilixp.utcradioclock.domain.model.ConditionLevel.*
import io.github.lilixp.utcradioclock.domain.model.HfBand
import org.junit.Assert.*
import org.junit.Test

class HfSummaryTest {
    private fun state(vararg levels: ConditionLevel?) = PropagationUiState(
        status = PropagationState.Status.CURRENT,
        bands = BandGroup.entries.zip(levels.toList()).map { (group, level) -> BandUi(group, level, level, level) },
    )

    @Test fun allGood_favorableEveryGroupRecommended() {
        val result = summarizeHf(state(GOOD, GOOD, GOOD, GOOD))
        assertEquals(HfSummary.Status.FAVORABLE, result.status)
        assertEquals(GOOD, result.level)
        assertEquals(BandGroup.entries.toList(), result.recommended)
    }

    @Test fun allPoor_noRecommendation() {
        val result = summarizeHf(state(POOR, POOR, POOR, POOR))
        assertEquals(HfSummary.Status.POOR, result.status)
        assertEquals(POOR, result.level)
        assertTrue(result.recommended.isEmpty())
    }

    @Test fun referenceCombination_only3020Recommended() {
        val result = summarizeHf(state(POOR, GOOD, FAIR, POOR))
        assertEquals(HfSummary.Status.MIXED, result.status)
        assertEquals(FAIR, result.level)
        assertEquals(listOf(BandGroup.BANDS_30_20), result.recommended)
        assertEquals(listOf(BandGroup.BANDS_80_40, BandGroup.BANDS_12_10), result.results[POOR])
    }

    @Test fun allFair_mixedCautiousRecommendation() {
        val result = summarizeHf(state(FAIR, FAIR, FAIR, FAIR))
        assertEquals(HfSummary.Status.MIXED, result.status)
        assertEquals(FAIR, result.recommendationLevel)
        assertEquals(BandGroup.entries.toList(), result.recommended)
    }

    @Test fun severalGood_allIncluded() {
        val result = summarizeHf(state(GOOD, FAIR, GOOD, POOR))
        assertEquals(listOf(BandGroup.BANDS_80_40, BandGroup.BANDS_17_15), result.recommended)
    }

    @Test fun partialOnlyReportedResults_noVerdict() {
        val result = summarizeHf(state(GOOD, null, POOR, FAIR))
        assertEquals(HfSummary.Status.INCOMPLETE, result.status)
        assertNull(result.level)
        assertTrue(result.recommended.isEmpty())
        assertEquals(3, result.results.values.sumOf { it.size })
    }

    @Test fun absentGroup_isIncomplete() {
        assertEquals(HfSummary.Status.INCOMPLETE, summarizeHf(state(GOOD, GOOD, GOOD)).status)
    }

    @Test fun allMissing_noInventedResults() {
        val result = summarizeHf(state(null, null, null, null))
        assertEquals(HfSummary.Status.INCOMPLETE, result.status)
        assertTrue(result.results.isEmpty())
        assertTrue(result.recommended.isEmpty())
    }

    @Test fun stale_retainsResultsWithoutRecommendation() {
        val result = summarizeHf(state(GOOD, GOOD, GOOD, GOOD).copy(status = PropagationState.Status.STALE))
        assertEquals(HfSummary.Status.STALE, result.status)
        assertNull(result.level)
        assertEquals(4, result.results[GOOD]!!.size)
        assertTrue(result.recommended.isEmpty())
    }

    @Test fun loading_ignoresAnyRetainedLevels() {
        val result = summarizeHf(state(GOOD, GOOD, GOOD, GOOD).copy(status = PropagationState.Status.LOADING))
        assertEquals(HfSummary.Status.LOADING, result.status)
        assertTrue(result.results.isEmpty())
        assertTrue(result.recommended.isEmpty())
    }

    @Test fun unavailable_ignoresAnyRetainedLevels() {
        val result = summarizeHf(state(POOR, POOR, POOR, POOR).copy(status = PropagationState.Status.UNAVAILABLE))
        assertEquals(HfSummary.Status.UNAVAILABLE, result.status)
        assertTrue(result.results.isEmpty())
        assertTrue(result.recommended.isEmpty())
    }

    @Test fun usesCurrentLevel_notTheOtherPhaseOrIndices() {
        val day = state(GOOD, GOOD, GOOD, GOOD).copy(
            bands = state(GOOD, GOOD, GOOD, GOOD).bands.map { it.copy(night = POOR) },
            solarFlux = IndexUi("0", POOR), kIndex = IndexUi("9", POOR), aIndex = IndexUi("400", POOR),
        )
        assertEquals(HfSummary.Status.FAVORABLE, summarizeHf(day).status)
        val night = day.copy(isDay = false, bands = day.bands.map { it.copy(now = it.night) })
        assertEquals(HfSummary.Status.POOR, summarizeHf(night).status)
    }

    @Test fun offlineEstimatesNeverEnterVerdict() {
        val input = state(GOOD, GOOD, GOOD, GOOD)
        val estimate = listOf(BandPhasesUi(HfBand.BAND_80M, GOOD, GOOD, GOOD))
        val result = summarizeHf(input.copy(bands = input.bands.mapIndexed { i, band ->
            if (i == 0) band.copy(now = null, day = null, night = null, estimate = estimate) else band
        }))
        assertEquals(HfSummary.Status.INCOMPLETE, result.status)
        assertEquals(3, result.results[GOOD]!!.size)
        assertTrue(result.recommended.isEmpty())
    }
}
