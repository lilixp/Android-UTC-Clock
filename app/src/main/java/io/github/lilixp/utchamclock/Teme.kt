package io.github.lilixp.utchamclock

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * O temă de culori. Pentru fiecare ceas: cifrele aprinse, segmentele stinse (null = fără segmente)
 * și textul secundar (etichete, dată). Culorile sunt aceleași ca în versiunea de Windows.
 */
data class Tema(
    val fundal: Color,
    val utc: Culori,
    val local: Culori,
    val info: Color,
    val evidentiat: Color,
    val niveluri: List<Color>, // bun, mediu, slab
    val font: FontFamily,
    val grosime: FontWeight,
    val fontIndicativ: FontFamily,
    val luminoasa: Boolean, // fundal deschis: bara de sus a telefonului primește iconițe închise
) {
    data class Culori(val aprins: Color, val stins: Color?, val secundar: Color)

    val cuSegmente get() = utc.stins != null

    /** Culoarea elementelor stinse (bare); la temele fără segmente, un amestec slab cu fundalul. */
    val stinsa get() = utc.stins ?: lerp(fundal, utc.aprins, 0.2f)
}

object Teme {
    const val VFD_VERDE = "vfd_verde"
    const val FT891 = "ft891"
    const val MINIMAL = "minimal"
    val TOATE = listOf(VFD_VERDE, FT891, MINIMAL)

    private val dseg7 = FontFamily(Font(R.font.dseg7_classic_bold))
    private val dseg14 = FontFamily(Font(R.font.dseg14_classic_bold))

    private fun c(hex: Long) = Color(0xFF000000 or hex)

    private val teme = mapOf(
        VFD_VERDE to Tema(
            fundal = c(0x0e1a15),
            utc = Tema.Culori(c(0x00ff41), c(0x0c381b), c(0x0a7a2a)),
            local = Tema.Culori(c(0xffa000), c(0x2d2b12), c(0xa86a00)),
            info = c(0x6f8f7f), evidentiat = c(0xffd060),
            niveluri = listOf(c(0x00ff41), c(0xffd060), c(0xff5040)),
            font = dseg7, grosime = FontWeight.Bold, fontIndicativ = dseg14, luminoasa = false,
        ),
        // Ecranul Yaesu FT-891: fond lavandă luminos, caractere bleumarin
        FT891 to Tema(
            fundal = c(0xc9cdf2),
            utc = Tema.Culori(c(0x1b2266), c(0xb8bdea), c(0x3c4488)),
            local = Tema.Culori(c(0x1b2266), c(0xb8bdea), c(0x3c4488)),
            info = c(0x3c4488), evidentiat = c(0xa01848),
            niveluri = listOf(c(0x1b2266), c(0x6a3a9a), c(0xb01818)),
            font = dseg7, grosime = FontWeight.Bold, fontIndicativ = dseg14, luminoasa = true,
        ),
        MINIMAL to Tema(
            fundal = c(0x18181b),
            utc = Tema.Culori(c(0xfafafa), null, c(0xa1a1aa)),
            local = Tema.Culori(c(0xa1a1aa), null, c(0x71717a)),
            info = c(0x71717a), evidentiat = c(0xfacc15),
            niveluri = listOf(c(0x4ade80), c(0xfacc15), c(0xf87171)),
            font = FontFamily.SansSerif, grosime = FontWeight.Light,
            fontIndicativ = FontFamily.SansSerif, luminoasa = false,
        ),
    )

    /** Modul de noapte păstrează fontul temei, dar folosește doar roșu slab pe negru. */
    private fun noapte(t: Tema): Tema {
        val rosu = Tema.Culori(c(0xb01a10), if (t.cuSegmente) c(0x2a0806) else null, c(0x6a150c))
        return t.copy(fundal = Color.Black, utc = rosu, local = rosu, info = c(0x6a150c),
            evidentiat = c(0xb01a10), niveluri = List(3) { c(0xb01a10) }, luminoasa = false)
    }

    /** Tema aleasă, în varianta de noapte dacă `noapte` e adevărat (manual sau automat, după apus). */
    fun activa(tema: String, noapte: Boolean): Tema {
        val t = teme[tema] ?: teme.getValue(VFD_VERDE)
        return if (noapte) noapte(t) else t
    }

    fun activa(setari: Setari): Tema = activa(setari.tema, setari.modNoapte)
}
