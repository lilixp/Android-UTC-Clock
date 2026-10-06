package io.github.lilixp.utcradioclock.ui.dashboard

import io.github.lilixp.utcradioclock.data.propagation.PropagationState
import io.github.lilixp.utcradioclock.domain.model.BandGroup
import io.github.lilixp.utcradioclock.domain.model.ConditionLevel

/** Presentation only: uses the reported current levels, never index values or offline estimates. */
data class HfSummary(
    val status: Status,
    val results: Map<ConditionLevel, List<BandGroup>> = emptyMap(),
    val recommended: List<BandGroup> = emptyList(),
    val recommendationLevel: ConditionLevel? = null,
) {
    enum class Status { FAVORABLE, MIXED, POOR, INCOMPLETE, STALE, LOADING, UNAVAILABLE }

    val level: ConditionLevel?
        get() = when (status) {
            Status.FAVORABLE -> ConditionLevel.GOOD
            Status.MIXED -> ConditionLevel.FAIR
            Status.POOR -> ConditionLevel.POOR
            else -> null
        }
}

fun summarizeHf(state: PropagationUiState): HfSummary {
    if (state.status == PropagationState.Status.LOADING) return HfSummary(HfSummary.Status.LOADING)
    if (state.status == PropagationState.Status.UNAVAILABLE) return HfSummary(HfSummary.Status.UNAVAILABLE)
    val reported = BandGroup.entries.mapNotNull { group ->
        state.bands.firstOrNull { it.group == group && it.estimate == null }
            ?.now?.let { group to it }
    }
    val results = ConditionLevel.entries.associateWith { level ->
        reported.filter { it.second == level }.map { it.first }
    }.filterValues { it.isNotEmpty() }
    val status = when {
        state.status == PropagationState.Status.STALE -> HfSummary.Status.STALE
        reported.size != BandGroup.entries.size -> HfSummary.Status.INCOMPLETE
        reported.all { it.second == ConditionLevel.GOOD } -> HfSummary.Status.FAVORABLE
        reported.all { it.second == ConditionLevel.POOR } -> HfSummary.Status.POOR
        else -> HfSummary.Status.MIXED
    }
    // Partial or stale results are displayed as observations, without a recommendation for now.
    val recommend = when (status) {
        HfSummary.Status.FAVORABLE, HfSummary.Status.MIXED -> when {
            results.containsKey(ConditionLevel.GOOD) -> ConditionLevel.GOOD
            results.containsKey(ConditionLevel.FAIR) -> ConditionLevel.FAIR
            else -> null
        }
        else -> null
    }
    return HfSummary(status, results, results[recommend].orEmpty(), recommend)
}
