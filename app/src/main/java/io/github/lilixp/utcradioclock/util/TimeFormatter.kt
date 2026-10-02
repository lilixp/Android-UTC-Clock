package io.github.lilixp.utcradioclock.util

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Turns times (and the station's coordinates) into the texts shown on screen. Clock times are always
 * 24-hour, as used on the air.
 */
class TimeFormatter(val locale: Locale) {

    /** With the day of the week: "Vineri, 2 octombrie 2026" / "Friday, 2 October 2026". */
    private val dateFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", locale)
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss", locale)
    private val shortTimeFormat = DateTimeFormatter.ofPattern("HH:mm", locale)
    private val shortDateFormat = DateTimeFormatter.ofPattern("d MMM yyyy", locale)

    /**
     * Where the phone's own time zone data keeps the common abbreviations: Android has "EEST" and
     * "CEST" only in British English and "EDT" only in American English (checked on the S24+), so
     * both are tried, in that order; plain English gives "GMT+03:00" for Europe.
     */
    private val abbreviationFormats = listOf(Locale.UK, Locale.US).map { DateTimeFormatter.ofPattern("zzz", it) }

    fun utcTime(instant: Instant): String = timeFormat.format(instant.atZone(ZoneOffset.UTC))

    fun utcDate(instant: Instant): String = date(instant.atZone(ZoneOffset.UTC))

    fun localTime(instant: Instant, zone: ZoneId): String = timeFormat.format(instant.atZone(zone))

    fun localDate(instant: Instant, zone: ZoneId): String = date(instant.atZone(zone))

    /** "UTC+03:00" (the offset at [instant], so it follows summer and winter time), or "UTC". */
    fun zoneOffset(instant: Instant, zone: ZoneId): String {
        val offset = zone.rules.getOffset(instant)
        return if (offset == ZoneOffset.UTC) "UTC" else "UTC${offset.id}"
    }

    /**
     * The zone's common abbreviation at [instant], e.g. "EEST" in summer and "EET" in winter, from the
     * phone's time zone data; null when it has none (it then only knows "GMT+05:30"-like names, e.g. for
     * India or Japan).
     */
    fun zoneAbbreviation(instant: Instant, zone: ZoneId): String? {
        if (zone is ZoneOffset) return null // only an offset ("Z", "+02:00"): the offset says it all
        val time = instant.atZone(zone)
        return abbreviationFormats.asSequence()
            .map { it.format(time) }
            .firstOrNull { name -> !name.startsWith("GMT") && !name.startsWith("UTC") && name.none { it == '+' || it == '-' } }
    }

    /** The zone's name, e.g. "Europe/Chisinau"; null for a zone that is only an offset. */
    fun zoneName(zone: ZoneId): String? = zone.id.takeUnless { zone is ZoneOffset }

    /** A date without the day of the week, short: "25 oct. 2026" / "25 Oct 2026". */
    fun shortDate(date: LocalDate): String = shortDateFormat.format(date)

    /** A wall-clock time without seconds: "04:00". */
    fun hoursMinutes(time: LocalDateTime): String = shortTimeFormat.format(time)

    private fun date(time: ZonedDateTime): String =
        dateFormat.format(time).replaceFirstChar { it.titlecase(locale) } // "vineri, …" → "Vineri, …"

    /**
     * Local time of an event (sunrise, sunset) rounded to the nearest minute, as sunrise tables do
     * (06:58:31 → 06:59), or null when it does not happen.
     */
    fun eventTime(instant: Instant?, zone: ZoneId): String? =
        instant?.let { shortTimeFormat.format(it.roundedToMinute().atZone(zone)) }

    /** E.g. "11h 52m" (rounded to the nearest minute), or null when it is not known. */
    fun duration(duration: Duration?): String? = duration?.let {
        val minutes = (it.seconds + 30).floorDiv(60)
        "%dh %02dm".format(Locale.ROOT, minutes / 60, minutes % 60)
    }

    /**
     * A moment in UTC for "data updated at": "05:29" on the same UTC day as [now], otherwise with the
     * date, "30 sept. 05:29" (in the phone's language), so old data is not mistaken for today's.
     */
    fun utcStamp(instant: Instant, now: Instant): String {
        val time = instant.atZone(ZoneOffset.UTC)
        return if (time.toLocalDate() == now.atZone(ZoneOffset.UTC).toLocalDate()) {
            shortTimeFormat.format(time)
        } else {
            stampFormat.format(time)
        }
    }

    /**
     * A local moment, e.g. when the GPS position was taken: "18:42" on the same local day as [now],
     * otherwise "30 sept. 18:42".
     */
    fun localStamp(instant: Instant, zone: ZoneId, now: Instant): String {
        val time = instant.atZone(zone)
        return if (time.toLocalDate() == now.atZone(zone).toLocalDate()) {
            shortTimeFormat.format(time)
        } else {
            stampFormat.format(time)
        }
    }

    /** "46,9375° N" in Romanian, "46.9375° N" in English: 4 decimals, about 10 m. */
    fun latitude(degrees: Double): String = coordinate(degrees, if (degrees < 0) "S" else "N")

    /** "28,2917° E", "0,1278° W": hemisphere letters as used internationally. */
    fun longitude(degrees: Double): String = coordinate(degrees, if (degrees < 0) "W" else "E")

    /** "±15 m": whole metres. */
    fun accuracy(meters: Float): String = "±%d m".format(locale, meters.roundToInt())

    private fun coordinate(degrees: Double, hemisphere: String) =
        "%.4f° %s".format(locale, abs(degrees), hemisphere)

    /** A value as published, without a useless ".0": 93.0 → "93", 1.5 → "1.5". */
    fun number(value: Double): String =
        if (value == Math.rint(value)) "%d".format(Locale.ROOT, value.toLong()) else value.toString()

    private val stampFormat = DateTimeFormatter.ofPattern("d MMM HH:mm", locale)

    private fun Instant.roundedToMinute(): Instant = plusSeconds(30).truncatedTo(ChronoUnit.MINUTES)
}
