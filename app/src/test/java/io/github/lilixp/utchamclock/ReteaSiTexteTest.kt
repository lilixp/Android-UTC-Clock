package io.github.lilixp.utchamclock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.time.ZoneOffset
import java.time.ZonedDateTime

/** Citirea datelor din rețea (fără conexiune reală) și textele în cele două limbi. */
class ReteaSiTexteTest {
    private val xmlN0nbh = """<?xml version="1.0"?><solar><solardata>
        <updated> 28 Sep 2026 0927 GMT</updated>
        <calculatedconditions>
        <band name="80m-40m" time="day">Fair</band><band name="30m-20m" time="day">Good</band>
        <band name="17m-15m" time="day">Fair</band><band name="12m-10m" time="day">Poor</band>
        <band name="80m-40m" time="night">Good</band><band name="30m-20m" time="night">Good</band>
        <band name="17m-15m" time="night">Fair</band><band name="12m-10m" time="night">Poor</band>
        </calculatedconditions></solardata></solar>"""

    @Test fun benziN0nbh() {
        val benzi = Retea.parseazaBenzi(xmlN0nbh)!!
        assertEquals(listOf(1, 0, 1, 2), benzi.zi)
        assertEquals(listOf(0, 0, 1, 2), benzi.noapte)
    }

    @Test fun benziIncomplete() {
        assertNull(Retea.parseazaBenzi(xmlN0nbh.replace("""<band name="80m-40m" time="day">Fair</band>""", "")))
    }

    @Test fun indiciNoaa() {
        val flux = """[{"time_tag":"2026-09-27T22:00:00","frequency":2800,"flux":96.4},{"flux":97}]"""
        assertEquals(96, Retea.parseazaFlux(flux))
        val k = """[{"time_tag":"2026-09-28T00:00:00","Kp":0.67,"a_running":3,"station_count":8},
            {"time_tag":"2026-09-28T03:00:00","Kp":1.67,"a_running":6,"station_count":8}]"""
        assertEquals(1.67 to 6, Retea.parseazaK(k))
    }

    @Test fun indiciNoaaInNotatieStiintifica() {
        // Formatul NOAA din septembrie 2026: 9.4e+001 = 94
        val flux = """[{"time_tag":"2026-09-28T22:00:00","frequency":2800,"flux":9.400000000000000e+001}]"""
        assertEquals(94, Retea.parseazaFlux(flux))
        assertEquals(3.33 to 12, Retea.parseazaK("""[{"Kp":3.330000e+000,"a_running":1.2e+001}]"""))
    }

    @Test fun vremeaOpenMeteo() {
        // Răspuns real pentru KN46dw; "current_units" are aceleași chei, dar cu text
        val json = """{"latitude":46.9375,"longitude":28.3125,"current_units":{"time":"iso8601",
            "interval":"seconds","temperature_2m":"°C","weather_code":"wmo code","wind_speed_10m":"km/h"},
            "current":{"time":"2026-09-29T07:45","interval":900,"temperature_2m":17.1,"weather_code":1,
            "wind_speed_10m":15.9}}"""
        assertEquals(Meteo(17.1, 1, 15.9), Retea.parseazaMeteo(json))
    }

    @Test fun nivelurileBenzilorPreferaN0nbh() {
        assertNull(Retea.nivelurileBenzilor(true, null, DateSolare()))
        val solar = DateSolare(96, 1.0, 4)
        assertEquals(listOf(0, 1, 2, 2), Retea.nivelurileBenzilor(false, null, solar))
        assertEquals(listOf(0, 0, 1, 2), Retea.nivelurileBenzilor(false, Retea.parseazaBenzi(xmlN0nbh), solar))
    }

    @Test fun eroareNtp() {
        // Serverul primește și răspunde la 1000,0 s; telefonul a trimis la 1000,4 și a primit la 1000,6,
        // deci ceasul telefonului e cu 0,5 s înainte (întârzierea rețelei, 0,1 s pe sens, se anulează)
        val secunde = (1000 + Retea.DIFERENTA_EPOCA_NTP).toInt()
        val raspuns = ByteBuffer.allocate(48).apply { position(32); putInt(secunde); putInt(0); putInt(secunde); putInt(0) }.array()
        assertEquals(0.5, Retea.calculeazaEroareNtp(raspuns, 1000.4, 1000.6), 1e-6)
    }

    @Test fun texte() {
        val moment = ZonedDateTime.of(2026, 9, 28, 12, 0, 0, 0, ZoneOffset.UTC)
        assertEquals("28 septembrie 2026", Texte.RO.data(moment))
        assertEquals("28 September 2026", Texte.EN.data(moment))
        assertEquals(Texte.EN, Texte.pentru("en"))
        for (t in listOf(Texte.RO, Texte.EN)) {
            assertEquals(12, t.luni.size)
            assertEquals(Teme.TOATE.toSet(), t.numeTeme.keys)
            assertEquals(setOf("sfi", "k", "a"), t.explicatii.keys)
            t.explicatii.values.forEach { assertEquals(3, it.second.size) }
            assertTrue(t.explicatieCeas(1.25).contains("1"))
        }
    }
}
