package io.github.lilixp.utcradioclock.ui.dashboard

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
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
    const val SUMMARY = "hf_summary"
    const val SUMMARY_TITLE = "hf_summary_title"
    const val SUMMARY_RECOMMENDATION = "hf_summary_recommendation"
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

    /** A group's table, Bandă | Zi | Noapte, a band's row in it and a band's cell by day or by night. */
    const val BAND_TABLE = "propagation_band_table"
    fun bandRow(band: HfBand) = "band_row_${band.meters}"
    fun bandCell(band: HfBand, phase: DayPhase) = "band_cell_${band.meters}_${phase.name.lowercase()}"

    /** A phase's column in a group's table (the column of now has a light stripe). */
    fun phaseColumn(phase: DayPhase) = "phase_column_${phase.name.lowercase()}"

    /** A group's offline estimate: only for a group N0NBH has no data for. */
    const val ESTIMATE_TABLE = "propagation_estimate_table"
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

private val TABLE_GAP = 6.dp
private val CELL_GAP = 2.dp
private val CELL_CORNER = 5.dp
private val CellShape = RoundedCornerShape(CELL_CORNER)

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
    HfSummaryCard(summarizeHf(propagation), propagation.updated)
    PropagationInfoCard {
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

/** Compact padding belongs only to Propagation; the shared cards and dashboard bars stay unchanged. */
@Composable
private fun PropagationInfoCard(content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.section_propagation), style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
            content()
        }
    }
}

/** Measures at the actual font scale; rearranges boxes instead of shrinking their text. */
@Composable
private fun AdaptiveBoxes(labels: List<String>, box: @Composable (Int, Modifier) -> Unit) {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val available = constraints.maxWidth
        val density = LocalDensity.current
        val gap = with(density) { 6.dp.roundToPx() }
        val required = (labels.maxOfOrNull {
            measurer.measure(AnnotatedString(it), style, softWrap = false).size.width
        } ?: 0) + with(density) { 4.dp.roundToPx() }
        val columns = when {
            labels.isEmpty() -> 1
            required * labels.size + gap * (labels.size - 1) <= available -> labels.size
            required * 2 + gap <= available -> 2
            else -> 1
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            labels.indices.toList().chunked(columns).forEach { chunk ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    chunk.forEach { box(it, Modifier.weight(1f)) }
                    repeat(columns - chunk.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun HfSummaryCard(summary: HfSummary, updated: String?) {
    val colors = LocalConditionColors.current.of(summary.level)
    val title = stringResource(when (summary.status) {
        HfSummary.Status.FAVORABLE -> R.string.hf_favorable
        HfSummary.Status.MIXED -> R.string.hf_mixed
        HfSummary.Status.POOR -> R.string.hf_poor
        HfSummary.Status.INCOMPLETE -> R.string.hf_incomplete
        HfSummary.Status.STALE -> R.string.hf_stale
        HfSummary.Status.LOADING -> R.string.hf_loading
        HfSummary.Status.UNAVAILABLE -> R.string.hf_unavailable
    })
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.container.copy(alpha = 0.14f).compositeOver(MaterialTheme.colorScheme.surface),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, colors.container.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth().testTag(PropagationTags.SUMMARY),
    ) {
        Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(50), color = colors.container, contentColor = colors.content) {
                    Icon(painterResource(R.drawable.ic_antenna), contentDescription = null,
                        modifier = Modifier.padding(5.dp).size(20.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag(PropagationTags.SUMMARY_TITLE))
                    Text(stringResource(R.string.hf_guidance), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (summary.recommended.isNotEmpty()) {
                val recommendationColors = LocalConditionColors.current.of(summary.recommendationLevel)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.testTag(PropagationTags.SUMMARY_RECOMMENDATION).semantics(mergeDescendants = true) {}) {
                    Text(stringResource(if (summary.recommendationLevel == ConditionLevel.GOOD)
                        R.string.hf_try_now_label else R.string.hf_try_cautiously_label),
                        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterVertically))
                    summary.recommended.forEach { group ->
                        Surface(shape = RoundedCornerShape(6.dp), color = recommendationColors.container,
                            contentColor = recommendationColors.content, modifier = Modifier.align(Alignment.CenterVertically)) {
                            Text(group.displayRange(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                    }
                }
            }
            summary.results.forEach { (level, groups) ->
                val others = groups.filterNot { it in summary.recommended }
                if (others.isNotEmpty()) {
                    Text(stringResource(R.string.hf_group_result, others.joinToString(", ") { it.displayRange() }, levelName(level)),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            when (summary.status) {
                HfSummary.Status.POOR -> Text(stringResource(R.string.hf_poor_hint), style = MaterialTheme.typography.bodyMedium)
                HfSummary.Status.INCOMPLETE -> Text(stringResource(R.string.hf_partial), style = MaterialTheme.typography.bodyMedium)
                HfSummary.Status.STALE -> Text(
                    if (updated == null) stringResource(R.string.hf_stale_hint)
                    else stringResource(R.string.hf_stale_time, updated), style = MaterialTheme.typography.bodyMedium)
                HfSummary.Status.LOADING -> Text(stringResource(R.string.hf_loading_hint), style = MaterialTheme.typography.bodyMedium)
                HfSummary.Status.UNAVAILABLE -> Text(stringResource(R.string.hf_unavailable_hint), style = MaterialTheme.typography.bodyMedium)
                else -> Unit
            }
            Text(stringResource(R.string.hf_disclaimer), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun BandGroup.displayRange() = range.replace('-', '–') + " m"

@Composable
private fun ResponsiveHeader(badge: @Composable () -> Unit, text: @Composable (Modifier) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (LocalDensity.current.fontScale >= 1.5f || maxWidth < 280.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                badge()
                text(Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                badge()
                text(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PropagationData(propagation: PropagationUiState, selected: String?, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val indices = listOf(
                IndexBoxData(SELECTED_SFI, R.string.index_sfi, propagation.solarFlux, PropagationTags.SFI),
                IndexBoxData(SELECTED_K, R.string.index_k, propagation.kIndex, PropagationTags.K),
                IndexBoxData(SELECTED_A, R.string.index_a, propagation.aIndex, PropagationTags.A),
        )
        AdaptiveBoxes(indices.map { stringResource(it.format, it.index?.value ?: "—") }) { i, modifier ->
            val box = indices[i]
            IndexBox(box.format, box.index, box.tag, selected == box.key, modifier) { onSelect(box.key) }
        }
        AdaptiveBoxes(propagation.bands.map { it.group.label }) { i, modifier ->
            val band = propagation.bands[i]
            BandBox(band, selected == band.group.name, modifier) { onSelect(band.group.name) }
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
    val description = stringResource(format, index?.value ?: stringResource(R.string.not_available))
    // Match the unchanged band's single text line, 4 dp vertical padding and 48 dp minimum.
    val density = LocalDensity.current
    val bandTextHeight = rememberTextMeasurer().measure(AnnotatedString("80-40m"),
        MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), softWrap = false).size.height
    val buttonHeight = maxOf(48.dp, with(density) { (bandTextHeight + 2 * 4.dp.roundToPx()).toDp() })
    Surface(
        shape = BoxShape,
        color = colors.container,
        contentColor = colors.content,
        modifier = modifier
            .height(buttonHeight)
            .selectionRing(selected, MaterialTheme.colorScheme.onSurface)
            .testTag(tag)
            .selectable(selected = selected, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        Box(Modifier.padding(horizontal = 2.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
            Text(description, style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = TABULAR_DIGITS),
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
        }
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
            .heightIn(min = 48.dp)
            .selectionRing(selected, MaterialTheme.colorScheme.onSurface)
            .testTag(PropagationTags.band(band.group))
            .selectable(selected = selected, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        Box(Modifier.padding(horizontal = 2.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
            Text(
                text = band.group.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

/**
 * The panel under the card: what the selected box means, from the same data as the box (so it follows
 * every update), or a short hint while nothing is selected. Only an outline, on the page's own colour:
 * part of the screen, not a second card; it grows with its text (large font), nothing is cut.
 */
@Composable
private fun DetailsPanel(propagation: PropagationUiState, selected: String?) {
    val indexSelected = selected == SELECTED_SFI || selected == SELECTED_K || selected == SELECTED_A
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(PropagationTags.PANEL)
            // TalkBack reads the new explanation after a tap
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(modifier = Modifier.padding(horizontal = if (indexSelected) 4.dp else 6.dp,
                vertical = if (indexSelected) 2.dp else 6.dp),
            verticalArrangement = Arrangement.spacedBy(if (indexSelected) 2.dp else 4.dp)) {
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
                        .padding(vertical = 4.dp)
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
    ResponsiveHeader(badge = {
        Surface(shape = RoundedCornerShape(10.dp), color = colors.container, contentColor = colors.content) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium
                    .copy(fontFeatureSettings = TABULAR_DIGITS),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .testTag(PropagationTags.PANEL_VALUE)
                    .widthIn(min = 56.dp)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }) { modifier ->
        if (numeric) {
            // Short index interpretation stays beside the title when it fits; large text can wrap.
            FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.CenterVertically).testTag(PropagationTags.PANEL_TITLE))
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically).testTag(PropagationTags.PANEL_SUBTITLE))
                }
            }
        } else {
            Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
    Text(stringResource(kind.explanation), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 18.sp))
    Column(
        modifier = Modifier.testTag(PropagationTags.REFERENCE_VALUES),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = stringResource(R.string.reference_values),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (LocalDensity.current.fontScale >= 1.5f || maxWidth < 280.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    kind.scale.forEach { ReferenceStep(it, it.level == index?.level, Modifier.fillMaxWidth()) }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    kind.scale.forEach { ReferenceStep(it, it.level == index?.level, Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** One step of the scale: a bar in its colour, its values ("0–3") and what they mean; [current] in bold. */
@Composable
private fun ReferenceStep(step: ReferenceValue, current: Boolean, modifier: Modifier) {
    val weight = if (current) FontWeight.Bold else FontWeight.Normal
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (current) 6.dp else 4.dp)
                .background(LocalConditionColors.current.of(step.level).container, RoundedCornerShape(2.dp)),
        )
        Text(
            text = step.values,
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = TABULAR_DIGITS),
            fontWeight = weight,
        )
        Text(text = stringResource(step.meaning), style = MaterialTheme.typography.bodySmall, fontWeight = weight)
    }
}

/**
 * A band group: its box and N0NBH's level now, then its bands in a compact table, Bandă | Zi | Noapte, in
 * colours only, each band with its own cells. With N0NBH data every band shows the group's level (N0NBH
 * publishes one level for the group: the same colour repeated, not a measurement per band). Only when
 * N0NBH reported nothing for the group: the offline estimate for each band, as the state brings it (the
 * fallback, where two bands may differ), or why there is none.
 */
@Composable
private fun GroupDetails(band: BandUi, propagation: PropagationUiState) {
    PanelHeader(
        value = band.group.label,
        level = band.now,
        title = estimateLevelName(band.now),
        subtitle = null,
        numeric = false,
    )
    // Day or night at the station now (twilight counts as night, as for N0NBH's groups; without a
    // position, by the clock)
    val now = if (propagation.isDay) DayPhase.DAY else DayPhase.NIGHT
    val estimate = band.estimate
    when {
        estimate != null -> Column(
            modifier = Modifier.testTag(PropagationTags.ESTIMATE_TABLE),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.estimate_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BandTable(estimate.map { BandRow(it.band, it.day, it.night) }, now)
            if (estimate.any { it.day == null || it.night == null }) {
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
        // N0NBH's level is for the whole group: each band of it shows that level
        band.day != null || band.night != null -> {
            BandTable(band.group.bands.map { BandRow(it, band.day, band.night) }, now)
            Text(stringResource(R.string.hf_group_evaluation), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // Nothing from N0NBH for this group, and not both SFI and K for an estimate
        else -> Text(
            text = stringResource(R.string.estimate_no_data),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (propagation.dayNightByClock) {
        Text(
            text = stringResource(R.string.band_day_by_clock),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One row of the table: a band and its level by day and by night (null is "Unknown"). */
private class BandRow(val band: HfBand, val day: ConditionLevel?, val night: ConditionLevel?) {
    fun at(phase: DayPhase): ConditionLevel? = if (phase == DayPhase.DAY) day else night
}

/** The table's columns: day and night. */
private val PHASES = listOf(DayPhase.DAY, DayPhase.NIGHT)

/**
 * Bandă | Zi | Noapte: the bands down the first column, then a cell per band in the day and in the night
 * column. The column of [now] has a light stripe behind it and its title in bold.
 */
@Composable
private fun BandTable(rows: List<BandRow>, now: DayPhase) {
    val density = LocalDensity.current
    val heading = stringResource(R.string.estimate_band)
    val gap = if (density.fontScale >= 1.5f) 2.dp else TABLE_GAP
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Fixed 0.8 : 1 : 1 proportions. Both phases use the same integer pixel width;
        // any rounding remainder stays in the band column, regardless of group or level text.
        val phaseWidth = with(density) { ((constraints.maxWidth - 2 * gap.roundToPx()) / 2.8f).toInt().toDp() }
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).testTag(PropagationTags.BAND_TABLE),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            TableColumn(heading, current = false, TextAlign.Start, Modifier.weight(1f)) {
                for (row in rows) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth().testTag(PropagationTags.bandRow(row.band)),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = stringResource(R.string.band_meters, row.band.meters),
                            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = TABULAR_DIGITS),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            for (phase in PHASES) {
                TableColumn(
                    phaseName(phase),
                    current = phase == now,
                    TextAlign.Center,
                    Modifier.width(phaseWidth).testTag(PropagationTags.phaseColumn(phase)),
                ) {
                    for (row in rows) {
                        LevelCell(
                            level = row.at(phase),
                            description = stringResource(
                                R.string.cell_description,
                                stringResource(R.string.band_meters, row.band.meters),
                                phaseName(phase),
                                estimateLevelName(row.at(phase)),
                            ),
                            tag = PropagationTags.bandCell(row.band, phase),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** A column: its title, then its cells filling the height left; the column of now on a light stripe. */
@Composable
private fun TableColumn(
    title: String,
    current: Boolean,
    align: TextAlign,
    modifier: Modifier,
    cells: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(if (current) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent, RoundedCornerShape(6.dp))
            .padding(2.dp),
        verticalArrangement = Arrangement.spacedBy(CELL_GAP),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
            color = if (current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = align,
            modifier = Modifier.fillMaxWidth(),
        )
        Column(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(CELL_GAP), content = cells)
    }
}

/** Colour and readable text together; the accessible description also identifies the band and phase. */
@Composable
private fun LevelCell(level: ConditionLevel?, description: String, tag: String, modifier: Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 28.dp)
            .background(LocalConditionColors.current.of(level).container, CellShape)
            .testTag(tag)
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Text(levelName(level), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
            color = LocalConditionColors.current.of(level).content, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 2.dp))
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
