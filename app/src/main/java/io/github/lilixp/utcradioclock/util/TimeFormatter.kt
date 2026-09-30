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

    private fun Instant.roundedToMinute(): Instant = plusSeconds(30).truncatedTo(ChronoUnit.MINUTES)
}
