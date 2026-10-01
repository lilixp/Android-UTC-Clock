package io.github.lilixp.utcradioclock.domain.model

import io.github.lilixp.utcradioclock.domain.location.Maidenhead
import io.github.lilixp.utcradioclock.domain.model.DayPhase.DAY
import io.github.lilixp.utcradioclock.domain.model.DayPhase.NIGHT
import io.github.lilixp.utcradioclock.domain.model.DayPhase.TWILIGHT
import io.github.lilixp.utcradioclock.domain.solar.SolarCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Day, civil twilight or night at the station: the one place the app decides it. */
class SolarDayPhaseTest {

    private fun at(time: String): Instant = Instant.parse("2026-10-01T${time}Z")

    /** Like KN46dw on 1 October 2026 (UTC): dawn 03:35, sunrise 04:05, sunset 15:47, dusk 16:17. */
    private val ordinary = SolarDay(
        date = LocalDate.of(2026, 10, 1),
        solarNoon = at("09:56:00"),
        sunrise = at("04:05:00"),
        sunset = at("15:47:00"),
        civilDawn = at("03:35:00"),
        civilDusk = at("16:17:00"),
        polar = null,
    )

    @Test
    fun ordinaryDay_nightTwilightDayTwilightNight() {
        assertEquals(NIGHT, ordinary.phaseAt(at("00:30:00")))
        assertEquals(NIGHT, ordinary.phaseAt(at("03:34:59")))
        assertEquals(TWILIGHT, ordinary.phaseAt(at("03:35:00"))) // civil dawn
        assertEquals(TWILIGHT, ordinary.phaseAt(at("04:04:59")))
        assertEquals(DAY, ordinary.phaseAt(at("04:05:00"))) // sunrise
        assertEquals(DAY, ordinary.phaseAt(at("09:56:00")))
        assertEquals(DAY, ordinary.phaseAt(at("15:46:59")))
        assertEquals(TWILIGHT, ordinary.phaseAt(at("15:47:00"))) // sunset
        assertEquals(TWILIGHT, ordinary.phaseAt(at("16:16:59")))
        assertEquals(NIGHT, ordinary.phaseAt(at("16:17:00"))) // civil dusk
        assertEquals(NIGHT, ordinary.phaseAt(at("23:59:59")))
    }

    @Test
    fun whiteNight_twilightAllNight() {
        val white = ordinary.copy(civilDawn = null, civilDusk = null)
        assertEquals(TWILIGHT, white.phaseAt(at("00:30:00")))
        assertEquals(DAY, white.phaseAt(at("10:00:00")))
        assertEquals(TWILIGHT, white.phaseAt(at("23:00:00")))
    }

    @Test
    fun onlyAMorningCivilDawn_eveningStaysTwilight() {
        val half = ordinary.copy(civilDusk = null)
        assertEquals(NIGHT, half.phaseAt(at("02:00:00")))
        assertEquals(TWILIGHT, half.phaseAt(at("20:00:00")))
    }

    @Test
    fun midnightSun_alwaysDay() {
        val polarDay = ordinary.copy(sunrise = null, sunset = null, civilDawn = null, civilDusk = null, polar = PolarCondition.MIDNIGHT_SUN)
        for (time in listOf("00:00:00", "09:56:00", "23:59:59")) assertEquals(DAY, polarDay.phaseAt(at(time)))
    }

    @Test
    fun polarNightWithoutTwilight_alwaysNight() {
        val polarNight = ordinary.copy(sunrise = null, sunset = null, civilDawn = null, civilDusk = null, polar = PolarCondition.POLAR_NIGHT)
        for (time in listOf("00:00:00", "09:56:00", "23:59:59")) assertEquals(NIGHT, polarNight.phaseAt(at(time)))
    }

    @Test
    fun polarNightWithTwilightAtNoon() {
        val polarNight = ordinary.copy(
            sunrise = null, sunset = null, civilDawn = at("08:30:00"), civilDusk = at("11:20:00"), polar = PolarCondition.POLAR_NIGHT,
        )
        assertEquals(NIGHT, polarNight.phaseAt(at("08:29:59")))
        assertEquals(TWILIGHT, polarNight.phaseAt(at("09:56:00")))
        assertEquals(NIGHT, polarNight.phaseAt(at("11:20:00")))
    }

    @Test
    fun sunriseWithoutSunset_notDecided() {
        assertNull(ordinary.copy(sunset = null).phaseAt(at("12:00:00")))
        assertNull(ordinary.copy(sunrise = null).phaseAt(at("12:00:00")))
    }

    @Test
    fun realDays_fromTheSolarCalculator() {
        val kn46dw = Maidenhead.toPosition("KN46dw")!!
        val chisinau = ZoneId.of("Europe/Chisinau")
        val day = SolarCalculator.calculate(LocalDate.of(2026, 10, 1), chisinau, kn46dw)
        assertEquals(DAY, day.phaseAt(day.solarNoon))
        assertEquals(TWILIGHT, day.phaseAt(day.sunrise!!.minusSeconds(60)))
        assertEquals(TWILIGHT, day.phaseAt(day.sunset!!.plusSeconds(60)))
        assertEquals(NIGHT, day.phaseAt(day.civilDusk!!.plusSeconds(3600)))

        val svalbard = GeoPosition(78.5, 15.5)
        val oslo = ZoneId.of("Europe/Oslo")
        val june = SolarCalculator.calculate(LocalDate.of(2026, 6, 21), oslo, svalbard)
        assertEquals(DAY, june.phaseAt(june.solarNoon.plusSeconds(12 * 3600)))
        val december = SolarCalculator.calculate(LocalDate.of(2026, 12, 21), oslo, svalbard)
        assertEquals(NIGHT, december.phaseAt(december.solarNoon))
    }
}
