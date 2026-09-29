package io.github.lilixp.utchamclock

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.nio.ByteBuffer

/** Indicii solari; valorile rămân și când descărcarea eșuează, doar se marchează ca vechi. */
data class DateSolare(val sfi: Int? = null, val k: Double? = null, val a: Int? = null, val eroare: Boolean = false)

/** Condițiile pe benzi (0 = bun, 1 = mediu, 2 = slab) pentru fiecare grup din Calcule.BENZI. */
data class Benzi(val zi: List<Int>, val noapte: List<Int>)

/**
 * Date din internet: indici solari (NOAA), condiții pe benzi (N0NBH) și ora exactă (NTP).
 * Citirea răspunsurilor e separată de descărcare, ca să poată fi testată fără conexiune.
 */
object Retea {
    private const val URL_FLUX = "https://services.swpc.noaa.gov/json/f107_cm_flux.json"
    private const val URL_K = "https://services.swpc.noaa.gov/products/noaa-planetary-k-index.json"
    // Condițiile pe benzi calculate de Paul Herrman, N0NBH (aceleași grupuri ca în Calcule.BENZI)
    private const val URL_BENZI = "https://www.hamqsl.com/solarxml.php"
    private const val SERVER_NTP = "pool.ntp.org"
    const val DIFERENTA_EPOCA_NTP = 2208988800L // NTP numără secundele de la 1 ianuarie 1900
    const val INTERVAL_MS = 30 * 60 * 1000L // cât de des se reîmprospătează datele
    const val PAUZA_MINIMA_MS = 5 * 60 * 1000L // la revenirea în aplicație, nu mai des de atât

    private val NIVEL_N0NBH = mapOf("Good" to 0, "Fair" to 1, "Poor" to 2)

    private fun cerere(url: String): String {
        val conexiune = URL(url).openConnection() as HttpURLConnection
        conexiune.connectTimeout = 15_000
        conexiune.readTimeout = 15_000
        conexiune.setRequestProperty("User-Agent", "UTCHamClock-Android/" + BuildConfig.VERSION_NAME)
        return try {
            conexiune.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conexiune.disconnect()
        }
    }

    // Numerele NOAA pot veni și în notație științifică (ex. 9.4e+001 = 94)
    private const val NUMAR = "(-?[0-9.]+(?:[eE][-+]?[0-9]+)?)"

    /** Fluxul solar: fișierul NOAA are cea mai nouă valoare prima. */
    fun parseazaFlux(json: String): Int =
        Regex("\"flux\"\\s*:\\s*$NUMAR").find(json)!!.groupValues[1].toDouble().let { Math.round(it).toInt() }

    /** Indicele K și indicele A: fișierul NOAA are cea mai nouă valoare ultima. */
    fun parseazaK(json: String): Pair<Double, Int> {
        val k = Regex("\"Kp\"\\s*:\\s*$NUMAR").findAll(json).last().groupValues[1].toDouble()
        val a = Regex("\"a_running\"\\s*:\\s*$NUMAR").findAll(json).last().groupValues[1].toDouble()
        return k to a.toInt()
    }

    /** Condițiile din XML-ul N0NBH, sau null dacă lipsește vreo bandă. */
    fun parseazaBenzi(xml: String): Benzi? {
        val zi = MutableList<Int?>(Calcule.BENZI.size) { null }
        val noapte = MutableList<Int?>(Calcule.BENZI.size) { null }
        val banda = Regex("<band name=\"(\\d+)m-(\\d+)m\" time=\"(day|night)\">\\s*(Good|Fair|Poor)\\s*</band>")
        for (gasit in banda.findAll(xml)) {
            val (de, pana, moment, nivel) = gasit.destructured
            val index = Calcule.BENZI.indexOf("$de-$pana")
            if (index < 0) continue
            (if (moment == "day") zi else noapte)[index] = NIVEL_N0NBH.getValue(nivel)
        }
        if (null in zi || null in noapte) return null
        return Benzi(zi.map { it!! }, noapte.map { it!! })
    }

    suspend fun descarcaSolar(anterior: DateSolare): DateSolare = withContext(Dispatchers.IO) {
        try {
            val sfi = parseazaFlux(cerere(URL_FLUX))
            val (k, a) = parseazaK(cerere(URL_K))
            DateSolare(sfi, k, a, eroare = false)
        } catch (e: Exception) {
            anterior.copy(eroare = true)
        }
    }

    suspend fun descarcaBenzi(): Benzi? = withContext(Dispatchers.IO) {
        try {
            parseazaBenzi(cerere(URL_BENZI))
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Cât greșește ceasul telefonului (secunde; pozitiv = înainte), din răspunsul SNTP și momentele
     * locale de trimitere și primire. Media celor două sensuri elimină întârzierea rețelei.
     */
    fun calculeazaEroareNtp(raspuns: ByteArray, tTrimis: Double, tPrimit: Double): Double {
        val b = ByteBuffer.wrap(raspuns, 32, 16)
        fun moment(): Double {
            val secunde = b.int.toLong() and 0xFFFFFFFFL
            val fractiune = b.int.toLong() and 0xFFFFFFFFL
            return secunde - DIFERENTA_EPOCA_NTP + fractiune / 4294967296.0
        }
        val serverRx = moment()
        val serverTx = moment()
        return ((tTrimis - serverRx) + (tPrimit - serverTx)) / 2
    }

    suspend fun masoaraEroareCeas(): Double? = withContext(Dispatchers.IO) {
        try {
            DatagramSocket().use { s ->
                s.soTimeout = 5_000
                val cerere = ByteArray(48).also { it[0] = 0x1b } // cerere SNTP, versiunea 3
                val adresa = InetAddress.getByName(SERVER_NTP)
                val tTrimis = System.currentTimeMillis() / 1000.0
                s.send(DatagramPacket(cerere, cerere.size, adresa, 123))
                val raspuns = DatagramPacket(ByteArray(48), 48)
                s.receive(raspuns)
                val tPrimit = System.currentTimeMillis() / 1000.0
                calculeazaEroareNtp(raspuns.data, tTrimis, tPrimit)
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Condițiile pentru zi sau noapte: de la N0NBH dacă sunt, altfel estimarea proprie din SFI și K. */
    fun nivelurileBenzilor(zi: Boolean, benzi: Benzi?, solar: DateSolare): List<Int>? = when {
        benzi != null -> if (zi) benzi.zi else benzi.noapte
        solar.sfi != null && solar.k != null -> Calcule.conditiiBenzi(solar.sfi, solar.k, zi)
        else -> null
    }
}
