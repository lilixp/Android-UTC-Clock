package io.github.lilixp.utcradioclock.domain.solar

import io.github.lilixp.utcradioclock.domain.model.GeoPosition
import io.github.lilixp.utcradioclock.domain.model.PolarCondition
import io.github.lilixp.utcradioclock.domain.model.SolarDay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tan

/**
 * Sunrise, sunset, solar noon and civil twilight, offline, with the NOAA algorithm (NOAA Global
 * Monitoring Laboratory "Solar Calculator", after Jean Meeus, *Astronomical Algorithms*). Accurate to
 * about a minute between the polar circles, pure and deterministic: the same inputs always give the
 * same result, with no clock, network or Android involved.
 *
 * - Sunrise and sunset: the upper edge of the Sun on the horizon, with the usual 0.833° for
 *   atmospheric refraction and the Sun's radius (zenith 90.833°).
 * - Civil twilight: the Sun's centre 6° below the horizon (zenith 96°).
 */
object SolarCalculator {

    private const val SUNRISE_ZENITH = 90.833
    private const val CIVIL_TWILIGHT_ZENITH = 96.0
    private const val MINUTES_PER_DAY = 1440.0
    private const val SECONDS_PER_MINUTE = 60.0

    /**
     * The Sun on local calendar day [date] in time zone [zone] at [position]. The events belong to the
     * solar day whose noon falls on [date], so a sunset just after local midnight (far north in summer)
     * is still that day's sunset. Summer/winter time comes from [zone] itself.
     */
    fun calculate(date: LocalDate, zone: ZoneId, position: GeoPosition): SolarDay {
        // NOAA counts minutes from 0:00 UTC of a day. Take the UTC day whose solar noon is on the local
        // date: usually the same date, one day off only where the zone is far from the longitude.
        var utcDay = date
        var noon = instantOf(utcDay, solarNoonMinutes(utcDay, position.longitude))
        val noonDate = noon.atZone(zone).toLocalDate()
        if (noonDate != date) {
            utcDay = utcDay.plusDays(ChronoUnit.DAYS.between(noonDate, date))
            noon = instantOf(utcDay, solarNoonMinutes(utcDay, position.longitude))
        }

        fun event(zenith: Double, rising: Boolean) =
            eventMinutes(utcDay, position, zenith, rising)?.let { instantOf(utcDay, it) }

        val sunrise = event(SUNRISE_ZENITH, rising = true)
        val sunset = event(SUNRISE_ZENITH, rising = false)
        val polar = if (sunrise == null || sunset == null) polarCondition(utcDay, position) else null
        return SolarDay(
            date = date,
            solarNoon = noon,
            sunrise = sunrise.takeIf { polar == null },
            sunset = sunset.takeIf { polar == null },
            civilDawn = event(CIVIL_TWILIGHT_ZENITH, rising = true),
            civilDusk = event(CIVIL_TWILIGHT_ZENITH, rising = false),
            polar = polar,
        )
    }

    // ---- NOAA formulas; angles in degrees unless the name says otherwise ----

    private fun julianDay(utcDay: LocalDate) = utcDay.toEpochDay() + 2440587.5

    private fun julianCentury(julianDay: Double) = (julianDay - 2451545.0) / 36525.0

    private fun instantOf(utcDay: LocalDate, minutes: Double): Instant =
        utcDay.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds((minutes * SECONDS_PER_MINUTE).roundToLong())

    private fun geomMeanLongSun(t: Double) = (280.46646 + t * (36000.76983 + 0.0003032 * t)).mod(360.0)

    private fun geomMeanAnomalySun(t: Double) = 357.52911 + t * (35999.05029 - 0.0001537 * t)

    private fun eccentricityEarthOrbit(t: Double) = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)

    private fun sunEquationOfCenter(t: Double): Double {
        val m = Math.toRadians(geomMeanAnomalySun(t))
        return sin(m) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
            sin(2 * m) * (0.019993 - 0.000101 * t) + sin(3 * m) * 0.000289
    }

    private fun omega(t: Double) = 125.04 - 1934.136 * t

    private fun sunApparentLong(t: Double): Double {
        val trueLong = geomMeanLongSun(t) + sunEquationOfCenter(t)
        return trueLong - 0.00569 - 0.00478 * sin(Math.toRadians(omega(t)))
    }

    private fun obliquityCorrection(t: Double): Double {
        val seconds = 21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))
        val meanObliquity = 23.0 + (26.0 + seconds / 60.0) / 60.0
        return meanObliquity + 0.00256 * cos(Math.toRadians(omega(t)))
    }

    private fun sunDeclination(t: Double): Double {
        val sinDeclination = sin(Math.toRadians(obliquityCorrection(t))) * sin(Math.toRadians(sunApparentLong(t)))
        return Math.toDegrees(asin(sinDeclination))
    }

    /** Equation of time, in minutes: how far the true Sun is ahead of the mean Sun. */
    private fun equationOfTime(t: Double): Double {
        val epsilon = Math.toRadians(obliquityCorrection(t))
        val l0 = Math.toRadians(geomMeanLongSun(t))
        val e = eccentricityEarthOrbit(t)
        val m = Math.toRadians(geomMeanAnomalySun(t))
        val y = tan(epsilon / 2).let { it * it }
        val radians = y * sin(2 * l0) - 2 * e * sin(m) + 4 * e * y * sin(m) * cos(2 * l0) -
            0.5 * y * y * sin(4 * l0) - 1.25 * e * e * sin(2 * m)
        return 4 * Math.toDegrees(radians)
    }

    /** cos of the hour angle at which the Sun's centre reaches [zenith]; outside −1…1 it never does. */
    private fun cosHourAngle(latitude: Double, declination: Double, zenith: Double): Double {
        val lat = Math.toRadians(latitude)
        val dec = Math.toRadians(declination)
        return cos(Math.toRadians(zenith)) / (cos(lat) * cos(dec)) - tan(lat) * tan(dec)
    }

    /** Solar noon, in minutes after 0:00 UTC of [utcDay]. */
    private fun solarNoonMinutes(utcDay: LocalDate, longitude: Double): Double {
        val jd = julianDay(utcDay)
        val firstGuess = 720 - 4 * longitude - equationOfTime(julianCentury(jd - longitude / 360))
        return 720 - 4 * longitude - equationOfTime(julianCentury(jd + firstGuess / MINUTES_PER_DAY))
    }

    /**
     * The moment the Sun's centre crosses [zenith] rising (morning) or setting (evening), in minutes
     * after 0:00 UTC of [utcDay], or null if it does not happen that day. Computed at solar noon, then
     * once more at the first result, as NOAA does.
     */
    private fun eventMinutes(utcDay: LocalDate, position: GeoPosition, zenith: Double, rising: Boolean): Double? {
        val jd = julianDay(utcDay)
        fun at(t: Double): Double? {
            val cosH = cosHourAngle(position.latitude, sunDeclination(t), zenith)
            if (cosH !in -1.0..1.0) return null
            val hourAngle = Math.toDegrees(acos(cosH)).let { if (rising) it else -it }
            return 720 - 4 * (position.longitude + hourAngle) - equationOfTime(t)
        }
        val noon = solarNoonMinutes(utcDay, position.longitude)
        val firstGuess = at(julianCentury(jd + noon / MINUTES_PER_DAY)) ?: return null
        return at(julianCentury(jd + firstGuess / MINUTES_PER_DAY)) ?: firstGuess
    }

    /** Whether the Sun stays above or below the horizon all day (judged at solar noon). */
    private fun polarCondition(utcDay: LocalDate, position: GeoPosition): PolarCondition {
        val noon = solarNoonMinutes(utcDay, position.longitude)
        val declination = sunDeclination(julianCentury(julianDay(utcDay) + noon / MINUTES_PER_DAY))
        return if (cosHourAngle(position.latitude, declination, SUNRISE_ZENITH) < -1) {
            PolarCondition.MIDNIGHT_SUN
        } else {
            PolarCondition.POLAR_NIGHT
        }
    }
}
