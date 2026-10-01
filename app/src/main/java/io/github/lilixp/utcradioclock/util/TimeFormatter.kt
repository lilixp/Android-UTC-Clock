package io.github.lilixp.utcradioclock.util

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Turns times into the texts shown on screen. Clock times are always 24-hour, as used on the air. */
class TimeFormatter(val locale: Locale) {

    private val dateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", locale)
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss", locale)
    private val shortTimeFormat = DateTimeFormatter.ofPattern("HH:mm", locale)

    fun utcTime(instant: Instant): String = timeFormat.format(instant.atZone(ZoneOffset.UTC))

    fun utcDate(instant: Instant): String = dateFormat.format(instant.atZone(ZoneOffset.UTC))

    fun localTime(instant: Instant, zone: ZoneId): String = timeFormat.format(instant.atZone(zone))

    fun localDate(instant: Instant, zone: ZoneId): String = dateFormat.format(instant.atZone(zone))

    /** E.g. "Europe/Chisinau · UTC+03:00" (the offset follows daylight saving time), or just "UTC+02:00"
     *  for a zone that is only an offset. */
    fun timeZone(instant: Instant, zone: ZoneId): String {
        val offset = zone.rules.getOffset(instant)
        val offsetText = if (offset == ZoneOffset.UTC) "UTC" else "UTC${offset.id}"
        return if (zone is ZoneOffset) offsetText else "${zone.id} · $offsetText"
    }

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

    /** A value as published, without a useless ".0": 93.0 → "93", 1.5 → "1.5". */
    fun number(value: Double): String =
        if (value == Math.rint(value)) "%d".format(Locale.ROOT, value.toLong()) else value.toString()

    private val stampFormat = DateTimeFormatter.ofPattern("d MMM HH:mm", locale)

    private fun Instant.roundedToMinute(): Instant = plusSeconds(30).truncatedTo(ChronoUnit.MINUTES)
}
