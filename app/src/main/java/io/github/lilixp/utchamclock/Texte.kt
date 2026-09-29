package io.github.lilixp.utchamclock

import java.time.ZonedDateTime
import java.util.Locale

/**
 * Textele aplicației în română și engleză. Limba se schimbă din Setări, fără repornire,
 * de aceea textele stau aici și nu în resursele Android (strings.xml).
 */
data class Texte(
    val luni: List<String>,
    val niveluri: List<String>,
    val numeTeme: Map<String, String>,
    val numeAranjari: Map<String, String>,

    val setari: String,
    val inapoi: String,
    val indicativ: String,
    val exempluIndicativ: String,
    val indicativInvalid: String,
    val locator: String,
    val exempluLocator: String,
    val locatorInvalid: String,
    val salveaza: String,
    val salvat: String,
    val tema: String,
    val aranjare: String,
    val modNoapte: String,
    val modNoapteDescriere: String,
    val afisare: String,
    val secunde: String,
    val clipire: String,
    val clipireDescriere: String,
    val ceasStatie: String,
    val ecranAprins: String,
    val ecranAprinsDescriere: String,
    val noapteAutomata: String,
    val noapteAutomataDescriere: String,
    val limba: String,
    val limbaTelefon: String,
    val despre: String,
    val textDespre: String,

    val qthLipsa: String,
    val sincronizat: String,
    val copiat: String,
    val atingePentruExplicatii: String,
    val faraDate: String,
    val faraConexiune: String,
    val benziFaraDate: String,
    val ziua: String,
    val noaptea: String,
    val explicatii: Map<String, Pair<String, List<String>>>,
    val ceasFaraConexiune: String,
    val ceasInainte: String,
    val ceasInUrma: String,
    val ceasEroare: String, // %1$s = secunde, %2$s = înainte/în urmă
    val greyline: String,
    val greylineAcum: String, // %s = cât mai durează
    val greylineRasarit: String, // %s = cât mai e până începe
    val greylineApus: String,
    val vremea: String,
    val vremeaDescriere: String,
    val vreme: List<String>, // după Calcule.grupVreme
    val explicatieVreme: String, // %s = locatorul
    val ok: String,
) {
    fun data(moment: ZonedDateTime) = "${moment.dayOfMonth} ${luni[moment.monthValue - 1]} ${moment.year}"

    fun explicatieCeas(dt: Double?): String =
        if (dt == null) ceasFaraConexiune
        else String.format(ceasEroare, "%.2f".format(Locale.US, kotlin.math.abs(dt)), if (dt > 0) ceasInainte else ceasInUrma)

    companion object {
        val RO = Texte(
            luni = listOf("ianuarie", "februarie", "martie", "aprilie", "mai", "iunie", "iulie",
                "august", "septembrie", "octombrie", "noiembrie", "decembrie"),
            niveluri = listOf("Bun", "Mediu", "Slab"),
            numeTeme = mapOf(Teme.VFD_VERDE to "VFD verde", Teme.FT891 to "Transceiver FT-891",
                Teme.MINIMAL to "Minimal modern", Teme.LCD_REFLEXIV to "LCD reflexiv", Teme.VFD_ALBASTRU to "VFD albastru",
                Teme.ZI_LUMINOASA to "Zi luminoasă"),
            numeAranjari = mapOf(Aranjari.CLASIC to "Clasic", Aranjari.CARDURI to "Carduri",
                Aranjari.PANOU to "Panou transceiver"),

            setari = "Setări",
            inapoi = "Înapoi",
            indicativ = "Indicativ",
            exempluIndicativ = "ex. YO3ABC",
            indicativInvalid = "Indicativ invalid. Folosește litere, cifre și /, ex. YO3ABC.",
            locator = "Locator QTH",
            exempluLocator = "ex. KN46dw",
            locatorInvalid = "Locator invalid. Folosește 4 sau 6 caractere, ex. KN46 sau KN46dw.",
            salveaza = "Salvează",
            salvat = "Salvat",
            tema = "Culori",
            aranjare = "Aranjare",
            modNoapte = "Mod noapte",
            modNoapteDescriere = "Roșu slab pe negru, pentru camera stației întunecată",
            afisare = "Afișare",
            secunde = "Secunde",
            clipire = "Două puncte care clipesc",
            clipireDescriere = "Între ore și minute, ca la un ceas LED",
            ceasStatie = "Ceas de stație",
            ecranAprins = "Ecran mereu aprins",
            ecranAprinsDescriere = "Cât timp aplicația e deschisă",
            noapteAutomata = "Mod noapte automat",
            noapteAutomataDescriere = "Între apus și răsărit la QTH (fără locator: 18:00–06:00)",
            limba = "Limbă",
            limbaTelefon = "Ca telefonul",
            despre = "Despre",
            textDespre = "UTC Ham Clock pentru Android, versiunea %s\n\n" +
                "Ceas pentru radioamatori: ora UTC și locală, răsărit, apus, greyline, " +
                "indici solari și verificarea orei.\n\n" +
                "Autor: Lilian Putină, ER1PL\n" +
                "Realizat cu ajutorul AI: Claude Code (Anthropic)\n\n" +
                "Font DSEG de Keshikan (licență SIL OFL)\n" +
                "Indici solari: NOAA Space Weather Prediction Center\n" +
                "Condiții pe benzi: Paul Herrman, N0NBH – hamqsl.com\n" +
                "Vremea: Open-Meteo.com (CC BY 4.0)\n" +
                "Ora exactă: pool.ntp.org",

            qthLipsa = "QTH: setează locatorul",
            sincronizat = "Sincronizat",
            copiat = "Copiat: %s",
            atingePentruExplicatii = "Atinge ora UTC ca s-o copiezi; atinge o valoare pentru explicații.",
            faraDate = "Date indisponibile momentan",
            faraConexiune = "Fără conexiune: valoare de la ultima actualizare",
            benziFaraDate = "Condițiile apar după descărcarea datelor",
            ziua = "Ziua",
            noaptea = "Noaptea",
            explicatii = mapOf(
                "sfi" to ("Flux solar (SFI)" to listOf("Condiții bune pe benzile înalte (10–20 m)",
                    "Condiții moderate, mai ales pe 20 m și mai jos", "Benzi înalte slabe, încearcă 40–80 m")),
                "k" to ("Indicele K planetar" to listOf("Câmp geomagnetic liniștit, condiții bune pe HF",
                    "Câmp agitat, mai mult zgomot și fading", "Furtună geomagnetică: HF perturbat, posibilă aurora")),
                "a" to ("Indicele A" to listOf("Zi liniștită geomagnetic", "Zi agitată geomagnetic",
                    "Zi cu furtună geomagnetică")),
            ),
            ceasFaraConexiune = "Nu am putut verifica ora (fără conexiune la pool.ntp.org)",
            ceasInainte = "înainte",
            ceasInUrma = "în urmă",
            ceasEroare = "Ceasul telefonului e cu %1\$s s %2\$s față de ora exactă.\n" +
                "Pentru FT8/FT4 trebuie sub 1 s. Telefonul își sincronizează ora singur " +
                "(Setări → Dată și oră → automat).",
            greyline = "Greyline: 30 de minute în jurul răsăritului și apusului, când DX-ul pe benzile " +
                "joase merge adesea neobișnuit de bine.",
            greylineAcum = "Greyline acum, încă %s",
            greylineRasarit = "Greyline la răsărit în %s",
            greylineApus = "Greyline la apus în %s",
            vremea = "Vremea la QTH",
            vremeaDescriere = "Temperatura, cerul și vântul acum, după locator",
            vreme = listOf("senin", "parțial noros", "înnorat", "ceață", "burniță", "ploaie", "ploaie înghețată",
                "ninsoare", "averse", "averse de ninsoare", "furtună"),
            explicatieVreme = "Vremea de acum la QTH %s, de la Open-Meteo.com. Se actualizează la fiecare 30 de minute.",
            ok = "OK",
        )

        val EN = Texte(
            luni = listOf("January", "February", "March", "April", "May", "June", "July",
                "August", "September", "October", "November", "December"),
            niveluri = listOf("Good", "Fair", "Poor"),
            numeTeme = mapOf(Teme.VFD_VERDE to "Green VFD", Teme.FT891 to "Transceiver FT-891",
                Teme.MINIMAL to "Modern minimal", Teme.LCD_REFLEXIV to "Reflective LCD", Teme.VFD_ALBASTRU to "Blue VFD",
                Teme.ZI_LUMINOASA to "Daylight"),
            numeAranjari = mapOf(Aranjari.CLASIC to "Classic", Aranjari.CARDURI to "Cards",
                Aranjari.PANOU to "Transceiver panel"),

            setari = "Settings",
            inapoi = "Back",
            indicativ = "Callsign",
            exempluIndicativ = "e.g. W1ABC",
            indicativInvalid = "Invalid callsign. Use letters, digits and /, e.g. W1ABC.",
            locator = "QTH locator",
            exempluLocator = "e.g. FN31pr",
            locatorInvalid = "Invalid locator. Use 4 or 6 characters, e.g. FN31 or FN31pr.",
            salveaza = "Save",
            salvat = "Saved",
            tema = "Colors",
            aranjare = "Layout",
            modNoapte = "Night mode",
            modNoapteDescriere = "Dim red on black, for a dark shack",
            afisare = "Display",
            secunde = "Seconds",
            clipire = "Blinking colon",
            clipireDescriere = "Between hours and minutes, like an LED clock",
            ceasStatie = "Station clock",
            ecranAprins = "Keep screen on",
            ecranAprinsDescriere = "While the app is open",
            noapteAutomata = "Automatic night mode",
            noapteAutomataDescriere = "Between sunset and sunrise at your QTH (no locator: 18:00–06:00)",
            limba = "Language",
            limbaTelefon = "Same as phone",
            despre = "About",
            textDespre = "UTC Ham Clock for Android, version %s\n\n" +
                "A clock for radio amateurs: UTC and local time, sunrise, sunset, greyline, " +
                "solar indices and a time check.\n\n" +
                "Author: Lilian Putină, ER1PL\n" +
                "Built with AI assistance: Claude Code (Anthropic)\n\n" +
                "DSEG font by Keshikan (SIL OFL license)\n" +
                "Solar indices: NOAA Space Weather Prediction Center\n" +
                "Band conditions: Paul Herrman, N0NBH – hamqsl.com\n" +
                "Weather: Open-Meteo.com (CC BY 4.0)\n" +
                "Exact time: pool.ntp.org",

            qthLipsa = "QTH: set your locator",
            sincronizat = "Synced",
            copiat = "Copied: %s",
            atingePentruExplicatii = "Tap the UTC time to copy it; tap a value for an explanation.",
            faraDate = "Data not available yet",
            faraConexiune = "Offline: value from the last update",
            benziFaraDate = "Conditions appear once the data is downloaded",
            ziua = "Day",
            noaptea = "Night",
            explicatii = mapOf(
                "sfi" to ("Solar flux (SFI)" to listOf("Good conditions on the high bands (10–20 m)",
                    "Moderate conditions, mostly 20 m and below", "High bands poor, try 40–80 m")),
                "k" to ("Planetary K index" to listOf("Quiet geomagnetic field, good HF conditions",
                    "Unsettled field, more noise and fading", "Geomagnetic storm: disturbed HF, possible aurora")),
                "a" to ("A index" to listOf("Quiet geomagnetic day", "Unsettled geomagnetic day",
                    "Geomagnetic storm day")),
            ),
            ceasFaraConexiune = "Could not check the time (no connection to pool.ntp.org)",
            ceasInainte = "ahead of",
            ceasInUrma = "behind",
            ceasEroare = "The phone clock is %1\$s s %2\$s exact time.\n" +
                "FT8/FT4 needs less than 1 s. The phone syncs its time by itself " +
                "(Settings → Date and time → automatic).",
            greyline = "Greyline: 30 minutes around sunrise and sunset, when low-band DX is often " +
                "unusually good.",
            greylineAcum = "Greyline now, %s left",
            greylineRasarit = "Sunrise greyline in %s",
            greylineApus = "Sunset greyline in %s",
            vremea = "Weather at QTH",
            vremeaDescriere = "Current temperature, sky and wind, from your locator",
            vreme = listOf("clear", "partly cloudy", "overcast", "fog", "drizzle", "rain", "freezing rain",
                "snow", "showers", "snow showers", "thunderstorm"),
            explicatieVreme = "Current weather at QTH %s, from Open-Meteo.com. Updated every 30 minutes.",
            ok = "OK",
        )

        /** „auto” urmează limba telefonului: română dacă telefonul e în română, altfel engleză. */
        fun pentru(limba: String): Texte = when (limba) {
            "ro" -> RO
            "en" -> EN
            else -> if (Locale.getDefault().language == "ro") RO else EN
        }
    }
}
