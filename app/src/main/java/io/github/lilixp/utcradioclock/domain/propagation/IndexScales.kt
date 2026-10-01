package io.github.lilixp.utcradioclock.domain.propagation

import io.github.lilixp.utcradioclock.domain.model.ConditionLevel

/**
 * Colours for SFI, K and A. N0NBH classifies only the bands, so these levels come from published scales:
 *
 * - **K** (planetary K index), NOAA Space Weather Scales: Kp 5 is a G1 (minor) geomagnetic storm.
 *   0–3 quiet/unsettled → good, 4 active → fair, 5 and above storm → poor.
 * - **A** (planetary A index), NOAA's daily categories: 0–7 quiet, 8–15 unsettled, 16–29 active,
 *   30 and above storm. Quiet/unsettled → good, active → fair, storm → poor.
 * - **SFI** (10.7 cm solar flux): NOAA has no scale. These are the thresholds hams commonly use and the
 *   ones of UTC Ham Clock v1: 120 and above good for the high bands, 90–119 fair, below 90 poor.
 */
object IndexScales {

    fun kIndex(k: Double): ConditionLevel = when {
        k < 4 -> ConditionLevel.GOOD
        k < 5 -> ConditionLevel.FAIR
        else -> ConditionLevel.POOR
    }

    fun aIndex(a: Double): ConditionLevel = when {
        a < 16 -> ConditionLevel.GOOD
        a < 30 -> ConditionLevel.FAIR
        else -> ConditionLevel.POOR
    }

    fun solarFlux(sfi: Double): ConditionLevel = when {
        sfi >= 120 -> ConditionLevel.GOOD
        sfi >= 90 -> ConditionLevel.FAIR
        else -> ConditionLevel.POOR
    }
}
