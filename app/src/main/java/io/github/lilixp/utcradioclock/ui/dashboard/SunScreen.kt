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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

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
    // No screen title (the tab says it): the first card starts with the date, as the Location screen
    // starts with the locator; the cards, labels, tiles and colours are the other screens' own
    Column(Modifier.fillMaxWidth().testTag(SunTags.CONTENT), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (sun.status !in listOf(SunStatus.NORMAL, SunStatus.MIDNIGHT_SUN, SunStatus.POLAR_NIGHT)) {
            ScreenCard {
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
        ScreenCard {
            presentation?.let {
                Text(it.date, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag(SunTags.CONTEXT))
                val name = stringResource(when (it.source) {
                    SunPositionSource.MANUAL -> R.string.sun_source_manual
                    SunPositionSource.BACKUP_LOCATOR -> R.string.sun_source_backup
                    SunPositionSource.GPS -> R.string.sun_source_gps
                    SunPositionSource.LAST_GPS -> R.string.sun_source_last_gps
                })
                val locatorCentre = it.source == SunPositionSource.MANUAL || it.source == SunPositionSource.BACKUP_LOCATOR
                SourceChip(
                    text = if (locatorCentre) stringResource(R.string.location_square_centre, name) else name,
                    old = it.source == SunPositionSource.LAST_GPS,
                    textModifier = Modifier.testTag(SunTags.SOURCE),
                )
            }
            SunEventPair(sun)
        }
        ScreenCard {
            CardLabel(R.drawable.ic_sun, stringResource(R.string.sun_station_day))
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
                modifier = Modifier.fillMaxWidth().testTag(SunTags.COUNTDOWN))
        }
        ScreenCard(Modifier.testTag(SunTags.DETAILS)) {
            CardLabel(R.drawable.ic_info, stringResource(R.string.sun_details))
            val details = listOf(
                R.string.sun_day_length_label to (presentation?.let { sunDuration(it.dayLength, false) } ?: sun.dayLength),
                R.string.sun_noon_label to (presentation?.noon ?: sun.solarNoon),
                R.string.sun_morning_twilight to (presentation?.morningTwilight ?: if (sun.civilDawn != null && sun.sunrise != null)
                    "${sun.civilDawn}–${sun.sunrise}" else null),
                R.string.sun_evening_twilight to (presentation?.eveningTwilight ?: if (sun.sunset != null && sun.civilDusk != null)
                    "${sun.sunset}–${sun.civilDusk}" else null),
            )
            // Two per row; one per row with very large text, so long words ("dimineața") are not broken
            val perRow = if (LocalDensity.current.fontScale >= 1.5f) 1 else 2
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (pair in details.chunked(perRow)) {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((label, value) in pair) SunDetail(label, value, Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
            presentation?.let {
                Text(stringResource(R.string.sun_zone_note, it.zone), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().testTag(SunTags.ZONE))
            }
        }
    }
}

/** A small tile inside a card, a shade apart from it: Răsărit / Apus and the details. */
@Composable
private fun InnerTile(
    modifier: Modifier,
    centered: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
    }
}

@Composable
private fun SunEventPair(sun: SunUiState) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val contentWidth = with(LocalDensity.current) { ((maxWidth - 8.dp) / 2 - 20.dp).roundToPx() }
        val measurer = rememberTextMeasurer()
        val values = listOf(sun.sunrise, sun.sunset).map { it ?: stringResource(R.string.not_available) }
        val base = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold,
            fontFeatureSettings = TABULAR_DIGITS)
        // Measure both values at the actual font scale and use one shared size. Explicit line height
        // avoids intrinsic-height ambiguities of automatic text sizing inside equal-height tiles.
        val font = (26 downTo 16).firstOrNull { size -> values.all {
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
    // Centred, Lilian's choice: the two times read as a pair
    InnerTile(modifier.testTag(if (rising) SunTags.SUNRISE else SunTags.SUNSET), centered = true) {
        // The name goes under the icon when both do not fit (very large text), never broken in two
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)) {
            SunEventIcon(rising, Modifier.size(18.dp).align(Alignment.CenterVertically))
            Text(stringResource(if (rising) R.string.sun_sunrise_label else R.string.sun_sunset_label),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterVertically))
        }
        Text(value ?: stringResource(R.string.not_available), style = timeStyle, textAlign = TextAlign.Center)
        date?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center)
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

/** One detail as a tile: its name, small, then its value; a long value (another day's date) wraps. */
@Composable
private fun SunDetail(@StringRes label: Int, value: String?, modifier: Modifier) {
    InnerTile(modifier) {
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value ?: stringResource(R.string.not_available),
            style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = TABULAR_DIGITS), fontWeight = FontWeight.SemiBold)
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

/** The sun of now on the day band: eight short rays and a disc, with a dark outline that shows on every phase. */
private fun DrawScope.drawSun(center: Offset) {
    val ray = SUN_RAY_WIDTH.toPx()
    val inner = SUN_RAY_INNER.toPx()
    val outer = SUN_RAY_OUTER.toPx()
    for (i in 0 until 8) {
        val angle = i * PI / 4
        val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
        drawLine(SunOutline, center + direction * inner, center + direction * outer, ray, StrokeCap.Round)
    }
    drawCircle(SunFill, SUN_DISC.toPx(), center)
    drawCircle(SunOutline, SUN_DISC.toPx(), center, style = Stroke(SUN_DISC_OUTLINE.toPx()))
}

/** The sun's size: it fits the 20 dp band, rays included. */
private val SUN_DISC = 4.6.dp
private val SUN_DISC_OUTLINE = 1.2.dp
private val SUN_RAY_INNER = 6.5.dp
private val SUN_RAY_OUTER = 9.dp
private val SUN_RAY_WIDTH = 1.6.dp
private val SunFill = Color(0xFFFFC22E)
private val SunOutline = Color(0xFF3B2A00)

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
            if (data.markerPhase == DayPhase.DAY) {
                // By day, now is a small stylised sun inside the band; at twilight and at night the white line
                val radius = SUN_RAY_OUTER.toPx()
                drawSun(Offset((data.marker * size.width).coerceIn(radius, size.width - radius), size.height / 2))
            } else {
                val x = (data.marker * size.width).coerceIn(1.dp.toPx(), size.width - 1.dp.toPx())
                drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
                drawCircle(day, 3.dp.toPx(), Offset(x, 3.dp.toPx()))
            }
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
