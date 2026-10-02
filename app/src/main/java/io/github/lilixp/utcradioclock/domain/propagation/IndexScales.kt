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
 *
 * The thresholds are public so the Propagation screen's reference values show these very scales.
 */
object IndexScales {

    /** K from which activity is "active" (fair). */
    const val K_ACTIVE = 4

    /** K from which it is a geomagnetic storm (poor). */
    const val K_STORM = 5

    /** A from which activity is "active" (fair). */
    const val A_ACTIVE = 16

    /** A from which it is a storm (poor). */
    const val A_STORM = 30

    /** SFI from which it is fair. */
    const val SFI_FAIR = 90

    /** SFI from which it is good for the high bands. */
    const val SFI_GOOD = 120

    fun kIndex(k: Double): ConditionLevel = when {
        k < K_ACTIVE -> ConditionLevel.GOOD
        k < K_STORM -> ConditionLevel.FAIR
        else -> ConditionLevel.POOR
    }

    fun aIndex(a: Double): ConditionLevel = when {
        a < A_ACTIVE -> ConditionLevel.GOOD
        a < A_STORM -> ConditionLevel.FAIR
        else -> ConditionLevel.POOR
    }

    fun solarFlux(sfi: Double): ConditionLevel = when {
        sfi >= SFI_GOOD -> ConditionLevel.GOOD
        sfi >= SFI_FAIR -> ConditionLevel.FAIR
        else -> ConditionLevel.POOR
    }
}
