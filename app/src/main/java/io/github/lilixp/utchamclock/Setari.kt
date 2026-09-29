package io.github.lilixp.utchamclock

import android.content.Context

/** Setările aplicației; se salvează imediat ce se schimbă și se păstrează după repornire. */
data class Setari(
    val indicativ: String = "",
    val locator: String = "",
    val tema: String = Teme.VFD_VERDE,
    val modNoapte: Boolean = false,
    val limba: String = "auto", // „auto” = ca telefonul, „ro” sau „en”
    val secunde: Boolean = true,
    val clipire: Boolean = false, // două puncte care clipesc între ore și minute
    val ecranAprins: Boolean = false, // ceas de stație: ecranul nu se stinge cât e deschisă aplicația
    val noapteAutomata: Boolean = false, // ceas de stație: mod noapte între apus și răsărit la QTH
    val vremea: Boolean = true, // vremea de acum la QTH, pe ecranul ceasului
) {
    companion object {
        private const val FISIER = "setari"

        fun citeste(context: Context): Setari {
            val p = context.getSharedPreferences(FISIER, Context.MODE_PRIVATE)
            val i = Setari() // valorile implicite
            return Setari(
                indicativ = p.getString("indicativ", i.indicativ)!!,
                locator = p.getString("locator", i.locator)!!,
                tema = p.getString("tema", i.tema)!!.takeIf { it in Teme.TOATE } ?: i.tema,
                modNoapte = p.getBoolean("mod_noapte", i.modNoapte),
                limba = p.getString("limba", i.limba)!!,
                secunde = p.getBoolean("secunde", i.secunde),
                clipire = p.getBoolean("clipire", i.clipire),
                ecranAprins = p.getBoolean("ecran_aprins", i.ecranAprins),
                noapteAutomata = p.getBoolean("noapte_automata", i.noapteAutomata),
                vremea = p.getBoolean("vremea", i.vremea),
            )
        }

        fun salveaza(context: Context, s: Setari) {
            context.getSharedPreferences(FISIER, Context.MODE_PRIVATE).edit()
                .putString("indicativ", s.indicativ)
                .putString("locator", s.locator)
                .putString("tema", s.tema)
                .putBoolean("mod_noapte", s.modNoapte)
                .putString("limba", s.limba)
                .putBoolean("secunde", s.secunde)
                .putBoolean("clipire", s.clipire)
                .putBoolean("ecran_aprins", s.ecranAprins)
                .putBoolean("noapte_automata", s.noapteAutomata)
                .putBoolean("vremea", s.vremea)
                .apply()
        }
    }
}
