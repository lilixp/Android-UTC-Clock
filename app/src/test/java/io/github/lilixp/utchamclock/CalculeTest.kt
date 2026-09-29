package io.github.lilixp.utchamclock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Aceleași verificări ca testele versiunii de Windows (teste/test_calcule.py). */
class CalculeTest {
    private fun aproape(asteptat: String, real: Instant) =
        assertTrue("$real față de $asteptat", Duration.between(Instant.parse(asteptat), real).abs() < Duration.ofMinutes(4))

    @Test fun locatoriValizi() {
        for (text in listOf("KN46", "KN46dw", "kn46DW", "AA00aa", "RR99xx")) assertTrue(text, Calcule.locatorValid(text))
    }

    @Test fun locatoriInvalizi() {
        for (text in listOf("", "KN4", "KN466", "KZ46", "SN46", "KN46dy", "KN46d", "1234"))
            assertFalse(text, Calcule.locatorValid(text))
    }

    @Test fun normalizareLocator() {
        assertEquals("KN46dw", Calcule.normalizeazaLocator("kn46DW"))
        assertEquals("KN46", Calcule.normalizeazaLocator("kn46"))
    }

    @Test fun coordonateCentruPatrat() {
        val (lat, lon) = Calcule.locatorInCoordonate("KN34bk") // București
        assertEquals(44.4375, lat, 1e-4)
        assertEquals(26.125, lon, 1e-4)
        assertEquals(51.5 to 1.0, Calcule.locatorInCoordonate("JO01"))
    }

    @Test fun indicative() {
        for (text in listOf("YO3ABC", "ER1PL", "W1AW", "er1pl", "YO3ABC/P", "DL/ER1PL"))
            assertTrue(text, Calcule.indicativValid(text))
        for (text in listOf("", "AB", "ABCDEF", "123456", "ER1-PL", "ER1 PL", "A".repeat(10) + "12345"))
            assertFalse(text, Calcule.indicativValid(text))
    }

    @Test fun rasaritApusBucurestiEchinoctiu() {
        // Valori de referință (tabele astronomice): răsărit ~04:10 UTC, apus ~16:02 UTC
        val (rasarit, apus) = Calcule.rasaritApus(LocalDate.of(2026, 9, 28), 44.43, 26.10)!!
        aproape("2026-09-28T04:10:00Z", rasarit)
        aproape("2026-09-28T16:02:00Z", apus)
    }

    @Test fun rasaritApusGreenwichSolstitiu() {
        val (rasarit, apus) = Calcule.rasaritApus(LocalDate.of(2026, 6, 21), 51.48, 0.0)!!
        aproape("2026-06-21T03:43:00Z", rasarit)
        aproape("2026-06-21T20:21:00Z", apus)
    }

    @Test fun noaptePolara() {
        assertNull(Calcule.rasaritApus(LocalDate.of(2026, 12, 21), 80.0, 15.0))
    }

    @Test fun greylineSiZi() {
        val rasarit = Instant.parse("2026-09-28T04:10:00Z")
        val apus = Instant.parse("2026-09-28T16:02:00Z")
        assertTrue(Calcule.eGreyline(rasarit.plusSeconds(29 * 60), rasarit, apus))
        assertTrue(Calcule.eGreyline(apus.minusSeconds(30 * 60), rasarit, apus))
        assertFalse(Calcule.eGreyline(rasarit.plusSeconds(31 * 60), rasarit, apus))
        assertFalse(Calcule.eGreyline(rasarit, null, null))
        assertTrue(Calcule.eZi(Instant.parse("2026-09-28T12:00:00Z"), rasarit, apus, ZoneOffset.UTC))
        assertFalse(Calcule.eZi(Instant.parse("2026-09-28T20:00:00Z"), rasarit, apus, ZoneOffset.UTC))
    }

    @Test fun noapteAutomataLaQth() {
        // KN34 (București), 28 septembrie: soarele răsare ~04:10 și apune ~16:02 UTC
        assertTrue(Calcule.eZiLaQth(Instant.parse("2026-09-28T12:00:00Z"), "KN34bk", ZoneOffset.UTC))
        assertFalse(Calcule.eZiLaQth(Instant.parse("2026-09-28T20:00:00Z"), "KN34bk", ZoneOffset.UTC))
        // Fără locator valid: ziua e între 6 și 18 ora locală
        assertTrue(Calcule.eZiLaQth(Instant.parse("2026-09-28T07:00:00Z"), "", ZoneOffset.UTC))
        assertFalse(Calcule.eZiLaQth(Instant.parse("2026-09-28T19:00:00Z"), "KZ99", ZoneOffset.UTC))
    }

    @Test fun benzi() {
        assertEquals(listOf(1, 0, 1, 2), Calcule.conditiiBenzi(96, 1.0, zi = true))
        assertEquals(listOf(0, 1, 2, 2), Calcule.conditiiBenzi(96, 1.0, zi = false))
        assertEquals(listOf(1, 0, 0, 0), Calcule.conditiiBenzi(180, 1.0, zi = true))
        assertEquals(listOf(2, 1, 1, 1), Calcule.conditiiBenzi(180, 4.0, zi = true))
        assertEquals(listOf(2, 2, 2, 2), Calcule.conditiiBenzi(180, 7.0, zi = true))
    }

    @Test fun niveluriIndici() {
        assertEquals(listOf(0, 0, 1, 1, 2, 2), listOf(0.0, 2.9, 3.0, 4.9, 5.0, 9.0).map(Calcule::nivelK))
        assertEquals(listOf(0, 1, 2), listOf(0.1, -0.6, 1.5).map(Calcule::nivelDt))
    }
}
