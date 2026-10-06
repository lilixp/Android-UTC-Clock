package io.github.lilixp.utcradioclock.ui.dashboard

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lilixp.utcradioclock.R
import io.github.lilixp.utcradioclock.domain.model.DayPhase
import java.time.Duration
import kotlin.math.roundToInt

object SunTags {
    const val CONTENT = "sun_content"
    const val CONTEXT = "sun_context"
    const val SOURCE = "sun_source"
    const val SUNRISE = "sun_sunrise"
    const val SUNSET = "sun_sunset"
    const val BAND = "sun_day_band"
    const val MARKER_LABEL = "sun_marker_label"
    const val AXIS_START = "sun_axis_start"
    const val AXIS_MIDDAY = "sun_axis_midday"
    const val AXIS_END = "sun_axis_end"
    const val COUNTDOWN = "sun_countdown"
    const val DETAILS = "sun_details"
    const val ZONE = "sun_zone_note"
}

@Composable
internal fun SunScreen(sun: SunUiState) {
    val presentation = sun.presentation
    Column(Modifier.fillMaxWidth().testTag(SunTags.CONTENT), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SunTitle(R.string.section_sun)
            presentation?.let {
                Text(listOfNotNull(it.locator, it.date).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(SunTags.CONTEXT))
                Text(stringResource(when (it.source) {
                    SunPositionSource.MANUAL -> R.string.sun_source_manual
                    SunPositionSource.BACKUP_LOCATOR -> R.string.sun_source_backup
                    SunPositionSource.GPS -> R.string.sun_source_gps
                    SunPositionSource.LAST_GPS -> R.string.sun_source_last_gps
                }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(SunTags.SOURCE))
            }
        }
        if (sun.status !in listOf(SunStatus.NORMAL, SunStatus.MIDNIGHT_SUN, SunStatus.POLAR_NIGHT)) {
            SunSurface {
                Text(stringResource(R.string.sun_no_location), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(when (sun.status) {
                    SunStatus.NO_LOCATOR -> R.string.sun_enter_locator
                    SunStatus.GPS_NO_PERMISSION -> R.string.sun_no_position
                    SunStatus.GPS_LOCATION_OFF -> R.string.sun_location_off
                    SunStatus.GPS_SEARCHING -> R.string.location_searching
                    SunStatus.GPS_UNAVAILABLE -> R.string.sun_gps_unavailable
                    else -> R.string.sun_invalid_locator
                }, sun.locator.orEmpty()), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }
        SunEventPair(sun)
        SunSurface {
            SunTitle(R.string.sun_station_day)
            presentation?.let { DayBand(it) }
            val polarText = when (sun.status) {
                SunStatus.MIDNIGHT_SUN -> R.string.sun_midnight_sun
                SunStatus.POLAR_NIGHT -> R.string.sun_polar_night
                else -> null
            }
            val countdown = presentation?.countdown
            val message = when {
                polarText != null -> stringResource(polarText)
                countdown != null -> stringResource(if (countdown.event == SunEvent.SUNRISE)
                    R.string.sun_until_sunrise else R.string.sun_until_sunset, sunDuration(countdown.remaining, true))
                else -> stringResource(R.string.sun_next_unavailable)
            }
            Text(message, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().testTag(SunTags.COUNTDOWN))
        }
        SunSurface(Modifier.testTag(SunTags.DETAILS)) {
            SunTitle(R.string.sun_details)
            val rows = listOf(
                R.string.sun_day_length_label to (presentation?.let { sunDuration(it.dayLength, false) } ?: sun.dayLength),
                R.string.sun_noon_label to (presentation?.noon ?: sun.solarNoon),
                R.string.sun_morning_twilight to (presentation?.morningTwilight ?: if (sun.civilDawn != null && sun.sunrise != null)
                    "${sun.civilDawn}–${sun.sunrise}" else null),
                R.string.sun_evening_twilight to (presentation?.eveningTwilight ?: if (sun.sunset != null && sun.civilDusk != null)
                    "${sun.sunset}–${sun.civilDusk}" else null),
            )
            rows.forEachIndexed { index, (label, value) ->
                SunDetail(label, value)
                if (index < rows.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        presentation?.let {
            Text(stringResource(R.string.sun_zone_note, it.zone), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().testTag(SunTags.ZONE))
        }
    }
}

@Composable
private fun SunSurface(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
    }
}

@Composable
private fun SunTitle(@StringRes label: Int) {
    Text(stringResource(label), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.secondary)
}

@Composable
private fun SunEventPair(sun: SunUiState) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val contentWidth = with(LocalDensity.current) { ((maxWidth - 8.dp) / 2 - 16.dp).roundToPx() }
        val measurer = rememberTextMeasurer()
        val values = listOf(sun.sunrise, sun.sunset).map { it ?: stringResource(R.string.not_available) }
        val base = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold,
            fontFeatureSettings = TABULAR_DIGITS)
        // Measure both values at the actual font scale and use one shared size. Explicit line height
        // avoids intrinsic-height ambiguities of automatic text sizing inside equal-height cards.
        val font = (34 downTo 16).firstOrNull { size -> values.all {
            measurer.measure(AnnotatedString(it), base.copy(fontSize = size.sp, lineHeight = (size + 4).sp),
                softWrap = false).size.width <= contentWidth
        } } ?: 16
        val style = base.copy(fontSize = font.sp, lineHeight = (font + 4).sp)
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SunEventCard(true, sun.sunrise, sun.presentation?.sunriseDate, style, Modifier.weight(1f).fillMaxHeight())
            SunEventCard(false, sun.sunset, sun.presentation?.sunsetDate, style, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun SunEventCard(rising: Boolean, value: String?, date: String?, timeStyle: TextStyle, modifier: Modifier) {
    Card(modifier.testTag(if (rising) SunTags.SUNRISE else SunTags.SUNSET), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SunEventIcon(rising, Modifier.size(24.dp).align(Alignment.CenterVertically))
                Text(stringResource(if (rising) R.string.sun_sunrise_label else R.string.sun_sunset_label),
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary, modifier = Modifier.align(Alignment.CenterVertically))
            }
            Text(value ?: stringResource(R.string.not_available), style = timeStyle,
                color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth())
            date?.let { Text(it, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center) }
        }
    }
}

/** Local vector drawing, with distinct up/down arrows; no global icon or palette changes. */
@Composable
private fun SunEventIcon(rising: Boolean, modifier: Modifier) {
    val tint = MaterialTheme.colorScheme.secondary
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()
        drawLine(tint, Offset(w * .05f, h * .62f), Offset(w * .95f, h * .62f), stroke)
        drawArc(tint, 180f, 180f, false, Offset(w * .28f, h * .32f), Size(w * .44f, h * .5f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
        for ((a, b) in listOf(Offset(.5f, .04f) to Offset(.5f, .18f), Offset(.12f, .2f) to Offset(.22f, .3f),
            Offset(.88f, .2f) to Offset(.78f, .3f))) drawLine(tint, Offset(w*a.x,h*a.y), Offset(w*b.x,h*b.y), stroke)
        val tip = if (rising) .72f else .98f
        val tail = if (rising) .98f else .72f
        drawLine(tint, Offset(w*.5f,h*tail), Offset(w*.5f,h*tip), stroke)
        val wing = if (rising) tip+.1f else tip-.1f
        drawLine(tint, Offset(w*.39f,h*wing), Offset(w*.5f,h*tip), stroke)
        drawLine(tint, Offset(w*.61f,h*wing), Offset(w*.5f,h*tip), stroke)
    }
}

@Composable
private fun SunDetail(@StringRes label: Int, value: String?) {
    val text = value ?: stringResource(R.string.not_available)
    val stacked = LocalDensity.current.fontScale >= 1.5f || text.length > 18
    val style = MaterialTheme.typography.bodyMedium
    if (stacked) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(stringResource(label), style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text, style = style.copy(fontFeatureSettings = TABULAR_DIGITS), modifier = Modifier.align(Alignment.End))
        }
    } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), style = style, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(text, style = style.copy(fontFeatureSettings = TABULAR_DIGITS), textAlign = TextAlign.End)
    }
}

@Composable
private fun sunDuration(duration: Duration, countdown: Boolean): String {
    val minutes = if (countdown) duration.seconds / 60 else (duration.seconds + 30) / 60
    if (countdown && minutes < 1) return stringResource(R.string.sun_less_than_minute)
    val hours = minutes / 60
    val rest = minutes % 60
    return if (hours > 0) stringResource(R.string.sun_duration_hours, hours, rest)
        else stringResource(R.string.sun_duration_minutes, rest)
}

@Composable
private fun DayBand(data: SunPresentation) {
    // Local colours, readable in both themes. Text legend and semantics identify every phase.
    val night = MaterialTheme.colorScheme.onSurfaceVariant
    val twilight = Color(0xFFF0B53C)
    val day = Color(0xFF2796A3)
    val unknown = MaterialTheme.colorScheme.outlineVariant
    fun color(phase: DayPhase?) = when (phase) { DayPhase.DAY -> day; DayPhase.TWILIGHT -> twilight; DayPhase.NIGHT -> night; null -> unknown }
    val legend = stringResource(R.string.sun_phase_legend)
    val phaseNames = data.phaseRanges.map { stringResource(when (it.phase) {
        DayPhase.DAY -> R.string.phase_day
        DayPhase.TWILIGHT -> R.string.phase_twilight
        DayPhase.NIGHT -> R.string.phase_night
        null -> R.string.level_unknown
    }) }
    val description = stringResource(R.string.sun_band_description, data.currentTime, legend) + " " +
        data.phaseRanges.mapIndexed { index, range -> "${phaseNames[index]} ${range.start}–${range.end}" }.joinToString("; ")
    val markerDescription = stringResource(R.string.sun_marker_description, data.currentTime)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val availableWidth = constraints.maxWidth
            val density = LocalDensity.current
            val style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = TABULAR_DIGITS, fontWeight = FontWeight.Bold)
            val measured = rememberTextMeasurer().measure(AnnotatedString(data.currentTime), style)
            val labelWidth = with(density) { measured.size.width.toDp() }
            Box(Modifier.fillMaxWidth().height(with(density) { measured.size.height.toDp() })) {
                Text(data.currentTime, style = style, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.offset {
                        IntOffset((data.marker * availableWidth - measured.size.width / 2f).roundToInt()
                            .coerceIn(0, (availableWidth - measured.size.width).coerceAtLeast(0)), 0)
                    }.width(labelWidth).testTag(SunTags.MARKER_LABEL).semantics { contentDescription = markerDescription })
            }
        }
        Canvas(Modifier.fillMaxWidth().height(20.dp).clip(RoundedCornerShape(4.dp)).testTag(SunTags.BAND)
            .semantics { contentDescription = description }) {
            data.segments.forEach { drawRect(color(it.phase), Offset(it.start * size.width, 0f),
                Size((it.end - it.start) * size.width, size.height)) }
            val x = (data.marker * size.width).coerceIn(1.dp.toPx(), size.width - 1.dp.toPx())
            drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
            drawCircle(day, 3.dp.toPx(), Offset(x, 3.dp.toPx()))
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val availableWidth = constraints.maxWidth
            val density = LocalDensity.current
            val style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TABULAR_DIGITS)
            val measurer = rememberTextMeasurer()
            val labels = listOf(Triple("00:00", 0f, SunTags.AXIS_START), Triple("12:00", data.midday, SunTags.AXIS_MIDDAY),
                Triple("24:00", 1f, SunTags.AXIS_END))
            val height = labels.maxOf { measurer.measure(AnnotatedString(it.first), style).size.height }
            Box(Modifier.fillMaxWidth().height(with(density) { height.toDp() })) {
                labels.forEach { (label, fraction, tag) ->
                    val width = measurer.measure(AnnotatedString(label), style).size.width
                    Text(label, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.offset { IntOffset((fraction * availableWidth - width / 2f)
                            .roundToInt().coerceIn(0, (availableWidth - width).coerceAtLeast(0)), 0) }.testTag(tag))
                }
            }
        }
        Text(legend, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}
