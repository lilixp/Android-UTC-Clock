package io.github.lilixp.utchamclock

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * Calcule fără interfață: locator, indicativ, răsărit/apus, greyline și condiții pe benzi.
 * Aceleași reguli ca în versiunea de Windows (calcule.py), ca cele două să arate la fel.
 */
object Calcule {
    val GREYLINE: Duration = Duration.ofMinutes(30) // înainte/după răsărit și apus
    val BENZI = listOf("80-40", "30-20", "17-15", "12-10")

    // Nivelul fiecărei valori: 0 = bun, 1 = mediu, 2 = slab
    fun nivelSfi(v: Int) = if (v >= 120) 0 else if (v >= 90) 1 else 2
    fun nivelK(v: Double) = if (v < 3) 0 else if (v < 5) 1 else 2
    fun nivelA(v: Int) = if (v < 10) 0 else if (v < 30) 1 else 2
    const val PRAG_SINCRONIZAT = 0.5 // secunde: sub atât, ceasul telefonului e „Sincronizat”
    fun nivelDt(v: Double) = abs(v).let { if (it < 0.5) 0 else if (it < 1) 1 else 2 } // FT8 cere sub 1 s

    // Pregătite o singură dată: locatorul se verifică de câteva ori la fiecare secundă
    private val FORMA_INDICATIV = Regex("[A-Z0-9/]{3,12}")
    private val FORMA_LOCATOR = Regex("[A-R]{2}[0-9]{2}([A-X]{2})?")

    /** Litere, cifre și /, cu cel puțin o literă și o cifră (ex. YO3ABC, ER1XYZ/P). */
    fun indicativValid(text: String): Boolean {
        val t = text.uppercase()
        return FORMA_INDICATIV.matches(t) && t.any { it.isDigit() } && t.any { it in 'A'..'Z' }
    }

    fun locatorValid(text: String) = FORMA_LOCATOR.matches(text.uppercase())

    /** Convenția: primele patru caractere mari, ultimele două mici (ex. KN46dw). */
    fun normalizeazaLocator(text: String) = text.take(4).uppercase() + text.drop(4).lowercase()

    /** (latitudine, longitudine) pentru centrul pătratului Maidenhead. */
    fun locatorInCoordonate(locator: String): Pair<Double, Double> {
        val loc = locator.uppercase()
        var lon = (loc[0] - 'A') * 20.0 - 180 + (loc[2] - '0') * 2
        var lat = (loc[1] - 'A') * 10.0 - 90 + (loc[3] - '0')
        if (loc.length == 6) {
            lon += (loc[4] - 'A') * 5 / 60.0 + 2.5 / 60
            lat += (loc[5] - 'A') * 2.5 / 60 + 1.25 / 60
        } else {
            lon += 1
            lat += 0.5
        }
        return lat to lon
    }

    /**
     * Răsăritul și apusul (UTC) pentru ziua dată, după ecuația răsăritului (NOAA/Wikipedia).
     * Întoarce null în zilele polare fără răsărit sau apus.
     */
    fun rasaritApus(zi: LocalDate, lat: Double, lon: Double): Pair<Instant, Instant>? {
        val n = ChronoUnit.DAYS.between(LocalDate.of(2000, 1, 1), zi).toDouble()
        val jMedie = n - lon / 360
        val m = Math.toRadians((357.5291 + 0.98560028 * jMedie) % 360)
        val c = 1.9148 * sin(m) + 0.02 * sin(2 * m) + 0.0003 * sin(3 * m)
        val lambda = Math.toRadians((Math.toDegrees(m) + c + 180 + 102.9372) % 360)
        val jTranzit = 2451545 + jMedie + 0.0053 * sin(m) - 0.0069 * sin(2 * lambda)
        val sinDecl = sin(lambda) * sin(Math.toRadians(23.4397))
        val cosDecl = cos(asin(sinDecl))
        val phi = Math.toRadians(lat)
        val cosOmega = (sin(Math.toRadians(-0.833)) - sin(phi) * sinDecl) / (cos(phi) * cosDecl)
        if (abs(cosOmega) > 1) return null
        val omega = Math.toDegrees(acos(cosOmega))

        // Ziua iuliană 2451545 = 1 ianuarie 2000, ora 12:00 UTC
        fun dinJulian(j: Double): Instant =
            Instant.parse("2000-01-01T12:00:00Z").plusMillis(((j - 2451545) * 86_400_000).roundToLong())

        return dinJulian(jTranzit - omega / 360) to dinJulian(jTranzit + omega / 360)
    }

    fun eGreyline(acum: Instant, rasarit: Instant?, apus: Instant?): Boolean {
        if (rasarit == null || apus == null) return false
        val pana = minOf(Duration.between(rasarit, acum).abs(), Duration.between(apus, acum).abs())
        return pana <= GREYLINE
    }

    /** Zi sau noapte la QTH. Fără locator (rasarit=null), ziua e între 6 și 18 ora locală. */
    fun eZi(acum: Instant, rasarit: Instant?, apus: Instant?, zona: ZoneId): Boolean {
        if (rasarit != null && apus != null) return !acum.isBefore(rasarit) && !acum.isAfter(apus)
        return acum.atZone(zona).hour in 6..17
    }

    /** Zi sau noapte acum la QTH-ul dat prin locator (fără locator valid: între 6 și 18 ora locală). */
    fun eZiLaQth(acum: Instant, locator: String, zona: ZoneId): Boolean {
        val soare = locator.takeIf { locatorValid(it) }?.let {
            val (lat, lon) = locatorInCoordonate(it)
            rasaritApus(acum.atZone(zona).toLocalDate(), lat, lon)
        }
        return eZi(acum, soare?.first, soare?.second, zona)
    }

    /** Fereastra greyline (30 de minute înainte și după răsărit sau apus). */
    data class Greyline(val inceput: Instant, val sfarsit: Instant, val laRasarit: Boolean)

    /** Greyline-ul în curs sau următorul la QTH; null fără locator valid sau în zilele polare. */
    fun urmatorulGreyline(acum: Instant, locator: String, zona: ZoneId): Greyline? {
        if (!locatorValid(locator)) return null
        val (lat, lon) = locatorInCoordonate(locator)
        val azi = acum.atZone(zona).toLocalDate()
        // Și ziua de ieri: aproape de miezul nopții, apusul de ieri poate fi încă în curs
        for (zi in listOf(azi.minusDays(1), azi, azi.plusDays(1), azi.plusDays(2))) {
            val (rasarit, apus) = rasaritApus(zi, lat, lon) ?: continue
            for ((moment, laRasarit) in listOf(rasarit to true, apus to false)) {
                if (acum.isBefore(moment + GREYLINE)) return Greyline(moment - GREYLINE, moment + GREYLINE, laRasarit)
            }
        }
        return null
    }

    /**
     * Grupul vremii după codul WMO de la Open-Meteo: 0 senin, 1 parțial noros, 2 înnorat, 3 ceață,
     * 4 burniță, 5 ploaie, 6 ploaie înghețată, 7 ninsoare, 8 averse, 9 averse de ninsoare, 10 furtună.
     */
    fun grupVreme(cod: Int): Int? = when (cod) {
        0 -> 0
        1, 2 -> 1
        3 -> 2
        45, 48 -> 3
        51, 53, 55 -> 4
        61, 63, 65 -> 5
        56, 57, 66, 67 -> 6
        71, 73, 75, 77 -> 7
        80, 81, 82 -> 8
        85, 86 -> 9
        95, 96, 99 -> 10
        else -> null
    }

    /**
     * Estimare simplă a propagării pe HF (0 = bun, 1 = mediu, 2 = slab) pentru fiecare grup din BENZI.
     * Ziua, benzile înalte depind de fluxul solar, iar cele joase sunt atenuate de stratul D;
     * noaptea, benzile joase merg bine, iar cele înalte se închid. Furtunile (K mare) înrăutățesc tot.
     */
    fun conditiiBenzi(sfi: Int, k: Double, zi: Boolean): List<Int> {
        val niveluri = if (zi) listOf(
            1,
            if (sfi >= 90) 0 else 1,
            if (sfi >= 120) 0 else if (sfi >= 90) 1 else 2,
            if (sfi >= 150) 0 else if (sfi >= 110) 1 else 2,
        ) else listOf(
            0,
            if (sfi >= 120) 0 else if (sfi >= 80) 1 else 2,
            if (sfi >= 150) 1 else 2,
            if (sfi >= 200) 1 else 2,
        )
        val penalizare = if (k >= 6) 2 else if (k >= 4) 1 else 0
        return niveluri.map { minOf(2, it + penalizare) }
    }
}
