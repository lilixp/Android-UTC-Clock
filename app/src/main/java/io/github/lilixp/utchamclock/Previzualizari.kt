package io.github.lilixp.utchamclock

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import java.time.Instant

/*
 * Previzualizări pentru Android Studio: deschide acest fișier și alege „Split” sau „Design”
 * (dreapta sus) ca să vezi ecranele fără telefon sau emulator. Nu intră în aplicația instalată.
 */

private val EXEMPLU = StareCeas(
    acum = Instant.parse("2026-09-29T09:41:27Z"),
    solar = DateSolare(sfi = 96, k = 1.67, a = 6),
    benzi = Benzi(zi = listOf(1, 0, 1, 2), noapte = listOf(0, 0, 1, 2)),
    eroareCeas = 0.01,
    meteo = Meteo(temperatura = 17.1, cod = 1, vant = 15.9),
)

@Composable
private fun Ceas(tema: String, noapte: Boolean = false) {
    val setari = Setari(indicativ = "ER1PL", locator = "KN46dw", tema = tema, modNoapte = noapte, limba = "ro")
    val t = Teme.activa(setari)
    TemaAplicatie(t) { EcranCeas(EXEMPLU, setari, t, Texte.RO, laSetari = {}) }
}

private const val VERTICAL = "spec:width=411dp,height=891dp"
private const val ORIZONTAL = "spec:width=411dp,height=891dp,orientation=landscape"

@Preview(name = "VFD verde", device = VERTICAL, showSystemUi = true)
@Composable private fun VfdVerde() = Ceas(Teme.VFD_VERDE)

@Preview(name = "Transceiver FT-891", device = VERTICAL, showSystemUi = true)
@Composable private fun Ft891() = Ceas(Teme.FT891)

@Preview(name = "Minimal modern", device = VERTICAL, showSystemUi = true)
@Composable private fun Minimal() = Ceas(Teme.MINIMAL)

@Preview(name = "Mod noapte", device = VERTICAL, showSystemUi = true)
@Composable private fun Noapte() = Ceas(Teme.VFD_VERDE, noapte = true)

@Preview(name = "Orizontal – VFD verde", device = ORIZONTAL, showSystemUi = true)
@Composable private fun OrizontalVfd() = Ceas(Teme.VFD_VERDE)

@Preview(name = "Orizontal – FT-891", device = ORIZONTAL, showSystemUi = true)
@Composable private fun OrizontalFt891() = Ceas(Teme.FT891)

@Preview(name = "Setări", device = VERTICAL, showSystemUi = true)
@Composable private fun EcranulSetari() {
    val setari = Setari(indicativ = "ER1PL", locator = "KN46dw", limba = "ro")
    TemaAplicatie(Teme.activa(setari)) { EcranSetari(setari, Texte.RO, laSchimbare = {}, laInapoi = {}) }
}
