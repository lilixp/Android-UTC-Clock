package io.github.lilixp.utcradioclock.ui.dashboard

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lilixp.utcradioclock.R
import io.github.lilixp.utcradioclock.data.propagation.PropagationState
import io.github.lilixp.utcradioclock.domain.model.BandGroup
import io.github.lilixp.utcradioclock.domain.model.ConditionLevel
import io.github.lilixp.utcradioclock.domain.model.DayPhase
import io.github.lilixp.utcradioclock.domain.model.HfBand
import io.github.lilixp.utcradioclock.domain.propagation.IndexScales
import io.github.lilixp.utcradioclock.ui.theme.LocalConditionColors

/** Test tags of the PROPAGATION card and of the details panel under it. */
object PropagationTags {
    const val SFI = "propagation_sfi"
    const val K = "propagation_k"
    const val A = "propagation_a"
    fun band(group: BandGroup) = "propagation_band_${group.range}"

    /** The panel under the card, which explains the selected box. */
    const val PANEL = "propagation_panel"

    /** "Tap an index or a band for details.", while nothing is selected. */
    const val PANEL_HINT = "propagation_panel_hint"

    /** The selected box's value, large ("1" for K 1, "15m" for 15 m). */
    const val PANEL_VALUE = "propagation_panel_value"
    const val PANEL_TITLE = "propagation_panel_title"
    const val PANEL_SUBTITLE = "propagation_panel_subtitle"

    /** The reference values of SFI, K or A. */
    const val REFERENCE_VALUES = "propagation_reference_values"

    /** A band group's offline estimate: its bands by day, at twilight and at night. */
    const val ESTIMATE_TABLE = "propagation_estimate_table"
    fun estimateRow(band: HfBand) = "estimate_row_${band.meters}"
    fun estimateCell(band: HfBand, phase: DayPhase) = "estimate_${band.meters}_${phase.name.lowercase()}"
}

/** What the panel explains: an index (SFI, K, A), a band group or an estimated band (by its name). */
private const val SELECTED_SFI = "SFI"
private const val SELECTED_K = "K"
private const val SELECTED_A = "A"

/** The boxes' corners, and the selection ring around them: 2 dp wide, 1.5 dp away (inside the 6 dp gaps). */
private val BOX_CORNER = 8.dp
private val SELECTION_RING_WIDTH = 2.dp
private val SELECTION_RING_GAP = 1.5.dp

private val BoxShape = RoundedCornerShape(BOX_CORNER)

/** The estimate table: the band column is a little narrower than the three level columns. */
private const val BAND_COLUMN = 0.8f
private val TABLE_GAP = 6.dp

/**
 * N0NBH's data: SFI, K and A on the first row, the four band groups on the second, each in its colour,
 * and when N0NBH updated them. Without data: a clear message, never invented values. The offline estimate
 * is shown per group, in the panel, when a group is tapped.
 *
 * Under the card, a panel explains the box last tapped (no dialog, nothing to close): it stays until
 * another box is tapped or the screen is left, and shows the current data, so it follows every update.
 */
@Composable
internal fun PropagationCard(propagation: PropagationUiState) {
    // The one selection of this screen: kept through a rotation, forgotten when another section is opened
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val hasData = propagation.status == PropagationState.Status.CURRENT ||
        propagation.status == PropagationState.Status.STALE
    InfoCard(R.string.section_propagation) {
        when (propagation.status) {
            PropagationState.Status.LOADING ->
                Text(stringResource(R.string.propagation_loading), style = MaterialTheme.typography.bodyLarge)
            PropagationState.Status.UNAVAILABLE -> {
                Text(
                    text = stringResource(R.string.propagation_unavailable),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.propagation_unavailable_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PropagationState.Status.CURRENT, PropagationState.Status.STALE ->
                PropagationData(propagation, selected) { selected = it }
        }
    }
    if (hasData) DetailsPanel(propagation, selected)
}

@Composable
private fun PropagationData(propagation: PropagationUiState, selected: String?, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((key, format, index, tag) in listOf(
                IndexBoxData(SELECTED_SFI, R.string.index_sfi, propagation.solarFlux, PropagationTags.SFI),
                IndexBoxData(SELECTED_K, R.string.index_k, propagation.kIndex, PropagationTags.K),
                IndexBoxData(SELECTED_A, R.string.index_a, propagation.aIndex, PropagationTags.A),
            )) {
                IndexBox(format, index, tag, selected == key, Modifier.weight(1f)) { onSelect(key) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (band in propagation.bands) {
                BandBox(band, selected == band.group.name, Modifier.weight(1f)) { onSelect(band.group.name) }
            }
        }
        // Only when N0NBH last updated the data (UTC); the source is named in Settings → About the app
        val updated = propagation.updated
        val stale = propagation.status == PropagationState.Status.STALE
        val footer = when {
            stale && updated != null -> stringResource(R.string.propagation_stale, updated)
            stale -> stringResource(R.string.propagation_stale_no_time)
            updated != null -> stringResource(R.string.propagation_updated, updated)
            else -> null
        }
        footer?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = if (stale) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class IndexBoxData(val key: String, @StringRes val format: Int, val index: IndexUi?, val tag: String)

/**
 * The selected box: a thin ring just outside it, in the text colour of the theme (dark in Light, light in
 * Dark), so it shows on green, yellow and red alike; the box itself keeps its size and colour.
 */
private fun Modifier.selectionRing(selected: Boolean, color: Color): Modifier =
    if (!selected) this else drawBehind {
        val width = SELECTION_RING_WIDTH.toPx()
        val outset = SELECTION_RING_GAP.toPx() + width / 2
        drawRoundRect(
            color = color,
            topLeft = Offset(-outset, -outset),
            size = Size(size.width + 2 * outset, size.height + 2 * outset),
            cornerRadius = CornerRadius(BOX_CORNER.toPx() + outset),
            style = Stroke(width),
        )
    }

/** A box such as "SFI 93", coloured by its level; "SFI —" in the neutral colour when not reported. A tap explains it. */
@Composable
private fun IndexBox(
    @StringRes format: Int,
    index: IndexUi?,
    tag: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalConditionColors.current.of(index?.level)
    Surface(
        shape = BoxShape,
        color = colors.container,
        contentColor = colors.content,
        modifier = modifier
            .selectionRing(selected, MaterialTheme.colorScheme.onSurface)
            .testTag(tag)
            .selectable(selected = selected, role = Role.Button, onClick = onClick),
    ) {
        Text(
            text = stringResource(format, index?.value ?: stringResource(R.string.not_available)),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
}

/** A band group box ("80-40m") in the colour of its condition now; a tap explains it. */
@Composable
private fun BandBox(band: BandUi, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalConditionColors.current.of(band.now)
    val description = stringResource(R.string.band_description, band.group.label, levelName(band.now))
    Surface(
        shape = BoxShape,
        color = colors.container,
        contentColor = colors.content,
        modifier = modifier
            .selectionRing(selected, MaterialTheme.colorScheme.onSurface)
            .testTag(PropagationTags.band(band.group))
            .selectable(selected = selected, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        Text(
            text = band.group.label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
}

/**
 * The panel under the card: what the selected box means, from the same data as the box (so it follows
 * every update), or a short hint while nothing is selected. Only an outline, on the page's own colour:
 * part of the screen, not a second card; it grows with its text (large font), nothing is cut.
 */
@Composable
private fun DetailsPanel(propagation: PropagationUiState, selected: String?) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(PropagationTags.PANEL)
            // TalkBack reads the new explanation after a tap
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val group = propagation.bands.firstOrNull { it.group.name == selected }
            when {
                selected == SELECTED_SFI -> IndexDetails(IndexKind.SFI, propagation.solarFlux)
                selected == SELECTED_K -> IndexDetails(IndexKind.K, propagation.kIndex)
                selected == SELECTED_A -> IndexDetails(IndexKind.A, propagation.aIndex)
                group != null -> GroupDetails(group, propagation)
                else -> Text(
                    text = stringResource(R.string.propagation_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .testTag(PropagationTags.PANEL_HINT),
                )
            }
        }
    }
}

/**
 * The value of the selected box, large, in its colour ("1" for K 1), beside what it is ("Indice K") and,
 * under that, what it means now ("Liniștit").
 */
@Composable
private fun PanelHeader(value: String, level: ConditionLevel?, title: String, subtitle: String?, numeric: Boolean) {
    val colors = LocalConditionColors.current.of(level)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(10.dp), color = colors.container, contentColor = colors.content) {
            Text(
                text = value,
                style = (if (numeric) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge)
                    .copy(fontFeatureSettings = TABULAR_DIGITS),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .testTag(PropagationTags.PANEL_VALUE)
                    .widthIn(min = 56.dp)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag(PropagationTags.PANEL_TITLE),
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(PropagationTags.PANEL_SUBTITLE),
                )
            }
        }
    }
}

/** One step of an index's scale: its values ("0–3"), the level (colour) it gives and what it means. */
private class ReferenceValue(val values: String, val level: ConditionLevel, @StringRes val meaning: Int)

/** "0–3" for 0 up to (not including) 4, "4" for 4 alone: whole numbers, as N0NBH publishes them. */
private fun valuesFrom(from: Int, until: Int) = if (until - from == 1) "$from" else "$from–${until - 1}"

/**
 * SFI, K and A: their title, explanation and scale. The scale is [IndexScales]' own, the one that colours
 * the boxes, so the reference values and the colours never disagree.
 */
private enum class IndexKind(@StringRes val title: Int, @StringRes val explanation: Int, val scale: List<ReferenceValue>) {
    SFI(
        R.string.propagation_sfi_title,
        R.string.propagation_sfi_dialog,
        listOf(
            ReferenceValue("< ${IndexScales.SFI_FAIR}", ConditionLevel.POOR, R.string.level_poor),
            ReferenceValue(valuesFrom(IndexScales.SFI_FAIR, IndexScales.SFI_GOOD), ConditionLevel.FAIR, R.string.level_fair),
            ReferenceValue("≥ ${IndexScales.SFI_GOOD}", ConditionLevel.GOOD, R.string.sfi_good),
        ),
    ),
    K(
        R.string.propagation_k_title,
        R.string.propagation_k_dialog,
        listOf(
            ReferenceValue(valuesFrom(0, IndexScales.K_ACTIVE), ConditionLevel.GOOD, R.string.index_k_quiet),
            ReferenceValue(valuesFrom(IndexScales.K_ACTIVE, IndexScales.K_STORM), ConditionLevel.FAIR, R.string.index_active),
            ReferenceValue("≥ ${IndexScales.K_STORM}", ConditionLevel.POOR, R.string.index_storm),
        ),
    ),
    A(
        R.string.propagation_a_title,
        R.string.propagation_a_dialog,
        listOf(
            ReferenceValue(valuesFrom(0, IndexScales.A_ACTIVE), ConditionLevel.GOOD, R.string.index_a_quiet),
            ReferenceValue(valuesFrom(IndexScales.A_ACTIVE, IndexScales.A_STORM), ConditionLevel.FAIR, R.string.index_active),
            ReferenceValue("≥ ${IndexScales.A_STORM}", ConditionLevel.POOR, R.string.index_storm),
        ),
    ),
    ;

    /** What a level means for this index, e.g. "Liniștit" for a good K. */
    @StringRes
    fun meaning(level: ConditionLevel): Int = scale.first { it.level == level }.meaning
}

/** "93 · Solar Flux Index (SFI) · Mediu", the explanation, then the reference values, the current one in bold. */
@Composable
private fun IndexDetails(kind: IndexKind, index: IndexUi?) {
    PanelHeader(
        value = index?.value ?: stringResource(R.string.not_available),
        level = index?.level,
        title = stringResource(kind.title),
        subtitle = index?.level?.let { stringResource(kind.meaning(it)) },
        numeric = true,
    )
    Text(stringResource(kind.explanation), style = MaterialTheme.typography.bodyMedium)
    Column(
        modifier = Modifier.testTag(PropagationTags.REFERENCE_VALUES),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.reference_values),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Side by side, low to high: the three steps fit under the explanation even with large text
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (step in kind.scale) {
                ReferenceStep(step, current = step.level == index?.level, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** One step of the scale: a bar in its colour, its values ("0–3") and what they mean; [current] in bold. */
@Composable
private fun ReferenceStep(step: ReferenceValue, current: Boolean, modifier: Modifier) {
    val weight = if (current) FontWeight.Bold else FontWeight.Normal
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (current) 6.dp else 4.dp)
                .background(LocalConditionColors.current.of(step.level).container, RoundedCornerShape(2.dp)),
        )
        Text(
            text = step.values,
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = TABULAR_DIGITS),
            fontWeight = weight,
        )
        Text(text = stringResource(step.meaning), style = MaterialTheme.typography.bodySmall, fontWeight = weight)
    }
}

/**
 * A band group: N0NBH's level now, by day and by night (the one of now, day or night at the station, in
 * bold). Only when N0NBH reported nothing for the group: the offline estimate for its own bands, as the
 * state brings it (the fallback), or why there is none.
 */
@Composable
private fun GroupDetails(band: BandUi, propagation: PropagationUiState) {
    val isDay = propagation.isDay
    PanelHeader(
        value = band.group.label,
        level = band.now,
        title = levelName(band.now),
        subtitle = null,
        numeric = false,
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LevelRow(band.day, stringResource(R.string.group_day), levelName(band.day), current = isDay)
        LevelRow(band.night, stringResource(R.string.group_night), levelName(band.night), current = !isDay)
    }
    if (propagation.dayNightByClock) {
        Text(
            text = stringResource(R.string.band_day_by_clock),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    val estimate = band.estimate
    if (estimate != null) {
        EstimateTable(estimate, propagation.phase)
    } else if (band.day == null && band.night == null) {
        // Nothing from N0NBH for this group, and neither SFI nor K for an estimate
        Text(
            text = stringResource(R.string.estimate_no_data),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The table's columns: the three phases of the estimate, in the order of the day. */
private val PHASES = listOf(DayPhase.DAY, DayPhase.TWILIGHT, DayPhase.NIGHT)

/** A band's level at [phase], as the state brings it. */
private fun BandPhasesUi.at(phase: DayPhase): ConditionLevel? = when (phase) {
    DayPhase.DAY -> day
    DayPhase.TWILIGHT -> twilight
    DayPhase.NIGHT -> night
}

/**
 * "Estimare offline": a compact table, Bandă | Zi | Amurg | Noapte, each level in its colour ("Necunoscut"
 * in the neutral one), exactly as the state brings it (calculated in the ViewModel, nothing here); the
 * phase of now at the station ([phase]) in bold, when it is known. Then why some levels are unknown, if
 * any, and where the estimate comes from.
 */
@Composable
private fun EstimateTable(estimate: List<BandPhasesUi>, phase: DayPhase?) {
    Column(
        modifier = Modifier.padding(top = 4.dp).testTag(PropagationTags.ESTIMATE_TABLE),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.estimate_title),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(TABLE_GAP), verticalAlignment = Alignment.CenterVertically) {
            TableHeader(stringResource(R.string.estimate_band), current = false, TextAlign.Start, Modifier.weight(BAND_COLUMN))
            for (column in PHASES) {
                TableHeader(phaseName(column), current = column == phase, TextAlign.Center, Modifier.weight(1f))
            }
        }
        for (row in estimate) {
            val band = row.band
            Row(
                modifier = Modifier.testTag(PropagationTags.estimateRow(band)),
                horizontalArrangement = Arrangement.spacedBy(TABLE_GAP),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.band_meters, band.meters),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = TABULAR_DIGITS),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.weight(BAND_COLUMN),
                )
                for (column in PHASES) {
                    EstimateCell(
                        level = row.at(column),
                        current = column == phase,
                        tag = PropagationTags.estimateCell(band, column),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        if (estimate.any { row -> PHASES.any { row.at(it) == null } }) {
            Text(
                text = stringResource(R.string.estimate_no_data),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.estimate_source),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A column title of the table ("Zi"); the phase of now in bold, in the text colour. */
@Composable
private fun TableHeader(text: String, current: Boolean, align: TextAlign, modifier: Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
        color = if (current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = align,
        modifier = modifier,
    )
}

/**
 * One level in its colour, like the boxes: "Bun", "Mediu", "Slab", or "Necunoscut" in the neutral colour.
 * On one line; a long word ("Necunoscut") shrinks a little at a large text size rather than being cut.
 */
@Composable
private fun EstimateCell(level: ConditionLevel?, current: Boolean, tag: String, modifier: Modifier) {
    val colors = LocalConditionColors.current.of(level)
    Surface(shape = RoundedCornerShape(6.dp), color = colors.container, contentColor = colors.content, modifier = modifier) {
        Text(
            text = estimateLevelName(level),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (current) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = MaterialTheme.typography.labelMedium.fontSize),
            modifier = Modifier.testTag(tag).padding(horizontal = 2.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun phaseName(phase: DayPhase): String = stringResource(
    when (phase) {
        DayPhase.DAY -> R.string.phase_day
        DayPhase.TWILIGHT -> R.string.phase_twilight
        DayPhase.NIGHT -> R.string.phase_night
    },
)

/** A small square in a level's colour, a label ("0–3", "Ziua") and its meaning; [current] in bold. */
@Composable
private fun LevelRow(level: ConditionLevel?, label: String, meaning: String, current: Boolean) {
    val weight = if (current) FontWeight.Bold else FontWeight.Normal
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .background(LocalConditionColors.current.of(level).container, RoundedCornerShape(4.dp)),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = TABULAR_DIGITS),
            fontWeight = weight,
            modifier = Modifier.widthIn(min = 72.dp),
        )
        Text(text = meaning, style = MaterialTheme.typography.bodyMedium, fontWeight = weight, modifier = Modifier.weight(1f))
    }
}

/** An estimated level, or "Unknown" when the data it needs is missing. */
@Composable
private fun estimateLevelName(level: ConditionLevel?): String =
    if (level == null) stringResource(R.string.level_unknown) else levelName(level)


@Composable
private fun levelName(level: ConditionLevel?): String = stringResource(
    when (level) {
        ConditionLevel.GOOD -> R.string.level_good
        ConditionLevel.FAIR -> R.string.level_fair
        ConditionLevel.POOR -> R.string.level_poor
        null -> R.string.not_available
    },
)
