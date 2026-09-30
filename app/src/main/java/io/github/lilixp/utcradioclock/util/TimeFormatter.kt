package io.github.lilixp.utcradioclock.util

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
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

    /** Local time of an event (sunrise, sunset), or null when it is not known. */
    fun eventTime(instant: Instant?, zone: ZoneId): String? = instant?.let { shortTimeFormat.format(it.atZone(zone)) }

    /** E.g. "11:52" for 11 hours 52 minutes, or null when it is not known. */
    fun duration(duration: Duration?): String? =
        duration?.let { "%d:%02d".format(Locale.ROOT, it.toHours(), it.toMinutes() % 60) }
}
