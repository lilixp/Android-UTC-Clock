package io.github.lilixp.utchamclock

import android.content.Context

/** Setările aplicației; se salvează imediat ce se schimbă și se păstrează după repornire. */
data class Setari(
    val indicativ: String = "",
    val locator: String = "",
    val tema: String = Teme.VFD_VERDE,
    val modNoapte: Boolean = false,
    val limba: String = "auto", // „auto” = ca telefonul, „ro” sau „en”
) {
    companion object {
        private const val FISIER = "setari"

        fun citeste(context: Context): Setari {
            val p = context.getSharedPreferences(FISIER, Context.MODE_PRIVATE)
            val implicit = Setari()
            return Setari(
                indicativ = p.getString("indicativ", implicit.indicativ)!!,
                locator = p.getString("locator", implicit.locator)!!,
                tema = p.getString("tema", implicit.tema)!!.takeIf { it in Teme.TOATE } ?: implicit.tema,
                modNoapte = p.getBoolean("mod_noapte", implicit.modNoapte),
                limba = p.getString("limba", implicit.limba)!!,
            )
        }

        fun salveaza(context: Context, s: Setari) {
            context.getSharedPreferences(FISIER, Context.MODE_PRIVATE).edit()
                .putString("indicativ", s.indicativ)
                .putString("locator", s.locator)
                .putString("tema", s.tema)
                .putBoolean("mod_noapte", s.modNoapte)
                .putString("limba", s.limba)
                .apply()
        }
    }
}
