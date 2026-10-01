package io.github.lilixp.utcradioclock.domain.propagation

import io.github.lilixp.utcradioclock.domain.model.ConditionLevel.FAIR
import io.github.lilixp.utcradioclock.domain.model.ConditionLevel.GOOD
import io.github.lilixp.utcradioclock.domain.model.ConditionLevel.POOR
import org.junit.Assert.assertEquals
import org.junit.Test

class IndexScalesTest {

    @Test
    fun kIndex_noaaGeomagneticStormScale() {
        for (k in listOf(0.0, 1.0, 3.0, 3.67)) assertEquals("K $k", GOOD, IndexScales.kIndex(k))
        assertEquals(FAIR, IndexScales.kIndex(4.0)) // active
        for (k in listOf(5.0, 6.0, 9.0)) assertEquals("K $k", POOR, IndexScales.kIndex(k)) // G1 and above
    }

    @Test
    fun aIndex_noaaDailyCategories() {
        for (a in listOf(0.0, 7.0, 15.0)) assertEquals("A $a", GOOD, IndexScales.aIndex(a)) // quiet, unsettled
        for (a in listOf(16.0, 29.0)) assertEquals("A $a", FAIR, IndexScales.aIndex(a)) // active
        for (a in listOf(30.0, 50.0, 100.0)) assertEquals("A $a", POOR, IndexScales.aIndex(a)) // storm
    }

    @Test
    fun solarFlux_usualHamThresholds() {
        for (sfi in listOf(120.0, 150.0, 250.0)) assertEquals("SFI $sfi", GOOD, IndexScales.solarFlux(sfi))
        for (sfi in listOf(90.0, 93.0, 119.0)) assertEquals("SFI $sfi", FAIR, IndexScales.solarFlux(sfi))
        for (sfi in listOf(65.0, 89.0)) assertEquals("SFI $sfi", POOR, IndexScales.solarFlux(sfi))
    }
}
