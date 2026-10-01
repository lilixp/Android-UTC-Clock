package io.github.lilixp.utcradioclock.ui.dashboard

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.lilixp.utcradioclock.R
import io.github.lilixp.utcradioclock.data.propagation.PropagationState
import io.github.lilixp.utcradioclock.domain.model.BandEstimate
import io.github.lilixp.utcradioclock.domain.model.BandGroup
import io.github.lilixp.utcradioclock.domain.model.ConditionLevel
import io.github.lilixp.utcradioclock.domain.model.DayPhase
import io.github.lilixp.utcradioclock.domain.model.HfBand
import io.github.lilixp.utcradioclock.ui.theme.LocalConditionColors

/** Test tags of the PROPAGATION card. */
object PropagationTags {
    const val SFI = "propagation_sfi"
    const val K = "propagation_k"
    const val A = "propagation_a"
    const val ESTIMATE_TITLE = "estimate_title"
    fun band(group: BandGroup) = "propagation_band_${group.range}"
    fun estimate(band: HfBand) = "estimate_band_${band.meters}"
}

/** What the card's one dialog shows: an index (SFI, K, A), a N0NBH band group or an estimated band. */
private const val DIALOG_SFI = "SFI"
private const val DIALOG_K = "K"
private const val DIALOG_A = "A"

private val BoxShape = RoundedCornerShape(8.dp)

/**
 * N0NBH's data first: SFI, K and A on the first row, the four band groups on the second, each in its
 * colour, and when N0NBH updated them. Then, apart and marked as such, the offline estimate for the ten
 * HF bands. A tap on an index or a band explains it. Without data: a clear message, never invented values.
 */
@Composable
internal fun PropagationCard(propagation: PropagationUiState) {
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
            PropagationState.Status.CURRENT, PropagationState.Status.STALE -> PropagationData(propagation)
        }
    }
}

@Composable
private fun PropagationData(propagation: PropagationUiState) {
    // One dialog at a time, kept through a rotation: an index, a band group (its name) or a band (its name)
    var openDialog by rememberSaveable { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            IndexBox(R.string.index_sfi, propagation.solarFlux, PropagationTags.SFI, Modifier.weight(1f)) {
                openDialog = DIALOG_SFI
            }
            IndexBox(R.string.index_k, propagation.kIndex, PropagationTags.K, Modifier.weight(1f)) {
                openDialog = DIALOG_K
            }
            IndexBox(R.string.index_a, propagation.aIndex, PropagationTags.A, Modifier.weight(1f)) {
                openDialog = DIALOG_A
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (band in propagation.bands) {
                BandBox(band, Modifier.weight(1f)) { openDialog = band.group.name }
            }
        }
        // Only when N0NBH last updated the data (UTC); the source is named in the band dialog and in
        // Settings → About the app
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
        if (propagation.estimate.isNotEmpty()) {
            Estimate(propagation.estimate, Modifier.padding(top = 10.dp)) { openDialog = it.name }
        }
    }
    val close = { openDialog = null }
    when (openDialog) {
        null -> Unit
        DIALOG_SFI -> InfoDialog(R.string.propagation_sfi_title, R.string.propagation_sfi_dialog, close)
        DIALOG_K -> InfoDialog(R.string.propagation_k_title, R.string.propagation_k_dialog, close)
        DIALOG_A -> InfoDialog(R.string.propagation_a_title, R.string.propagation_a_dialog, close)
        else -> {
            propagation.bands.firstOrNull { it.group.name == openDialog }?.let { band ->
                BandDialog(band, propagation.isDay, propagation.dayNightByClock, close)
            }
            propagation.estimate.firstOrNull { it.band.name == openDialog }?.let { estimate ->
                EstimateDialog(estimate, propagation.phase, close)
            }
        }
    }
}

/**
 * The offline estimate, under its own title so it is not taken for N0NBH data: the ten bands in two
 * rows of five, each in the colour of its estimated level (neutral when unknown).
 */
@Composable
private fun Estimate(estimate: List<BandEstimate>, modifier: Modifier, onOpen: (HfBand) -> Unit) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.estimate_title),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(PropagationTags.ESTIMATE_TITLE),
        )
        for (row in estimate.chunked(BANDS_PER_ROW)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (item in row) EstimateBox(item, Modifier.weight(1f)) { onOpen(item.band) }
            }
        }
    }
}

/** A box such as "SFI 93", coloured by its level; "SFI —" in the neutral colour when not reported. A tap explains it. */
@Composable
private fun IndexBox(@StringRes format: Int, index: IndexUi?, tag: String, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalConditionColors.current.of(index?.level)
    Surface(
        shape = BoxShape,
        color = colors.container,
        contentColor = colors.content,
        modifier = modifier.testTag(tag).clickable(role = Role.Button, onClick = onClick),
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

/** A band group box ("80-40m") in the colour of its condition now; a tap opens the explanation. */
@Composable
private fun BandBox(band: BandUi, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalConditionColors.current.of(band.now)
    val description = stringResource(R.string.band_description, band.group.label, levelName(band.now))
    Surface(
        shape = BoxShape,
        color = colors.container,
        contentColor = colors.content,
        modifier = modifier
            .testTag(PropagationTags.band(band.group))
            .clickable(role = Role.Button, onClick = onClick)
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

/** An estimated band box ("20m") in the colour of its estimated level; a tap opens the explanation. */
@Composable
private fun EstimateBox(item: BandEstimate, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalConditionColors.current.of(item.level)
    val description = stringResource(R.string.band_description, item.band.label, estimateLevelName(item.level))
    Surface(
        shape = BoxShape,
        color = colors.container,
        contentColor = colors.content,
        modifier = modifier
            .testTag(PropagationTags.estimate(item.band))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        Text(
            text = item.band.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
}

/** What SFI, K or A means. */
@Composable
private fun InfoDialog(@StringRes title: Int, @StringRes text: Int, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(text), style = MaterialTheme.typography.bodyMedium) },
    )
}

/**
 * "20 m · 14.0–14.35 MHz / Estimare: Bun / Acum e zi la stație. / what the band is like / where the
 * estimate comes from" — or why there is none.
 */
@Composable
private fun EstimateDialog(estimate: BandEstimate, phase: DayPhase?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
        title = { Text(stringResource(R.string.hf_band_title, estimate.band.meters, estimate.band.frequencies)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.estimate_level, estimateLevelName(estimate.level)),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(
                        when (phase) {
                            DayPhase.DAY -> R.string.band_now_day
                            DayPhase.TWILIGHT -> R.string.band_now_twilight
                            DayPhase.NIGHT -> R.string.band_now_night
                            null -> R.string.estimate_no_position
                        },
                    ),
                )
                if (estimate.level == null && phase != null) Text(stringResource(R.string.estimate_no_data))
                Text(stringResource(bandExplanation(estimate.band)), style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = stringResource(R.string.estimate_source),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

/** "80-40 m / Ziua: Mediu • Noaptea: Bun / Condiții calculate de N0NBH (hamqsl.com)." */
@Composable
private fun BandDialog(band: BandUi, isDay: Boolean, dayNightByClock: Boolean, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
        title = { Text(stringResource(R.string.band_title, band.group.range)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.band_day_night, levelName(band.day), levelName(band.night)),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(stringResource(if (isDay) R.string.band_now_day else R.string.band_now_night))
                if (dayNightByClock) Text(stringResource(R.string.band_day_by_clock))
                Text(stringResource(R.string.propagation_source))
            }
        },
    )
}

/** An estimated level, or "Unknown" when the data it needs is missing. */
@Composable
private fun estimateLevelName(level: ConditionLevel?): String =
    if (level == null) stringResource(R.string.level_unknown) else levelName(level)

private fun bandExplanation(band: HfBand): Int = when (band) {
    HfBand.BAND_160M -> R.string.band_160m_dialog
    HfBand.BAND_80M -> R.string.band_80m_dialog
    HfBand.BAND_60M -> R.string.band_60m_dialog
    HfBand.BAND_40M -> R.string.band_40m_dialog
    HfBand.BAND_30M -> R.string.band_30m_dialog
    HfBand.BAND_20M -> R.string.band_20m_dialog
    HfBand.BAND_17M -> R.string.band_17m_dialog
    HfBand.BAND_15M -> R.string.band_15m_dialog
    HfBand.BAND_12M -> R.string.band_12m_dialog
    HfBand.BAND_10M -> R.string.band_10m_dialog
}

/** Five per row: "160m" still fits at the phone's large text size. */
private const val BANDS_PER_ROW = 5

@Composable
private fun levelName(level: ConditionLevel?): String = stringResource(
    when (level) {
        ConditionLevel.GOOD -> R.string.level_good
        ConditionLevel.FAIR -> R.string.level_fair
        ConditionLevel.POOR -> R.string.level_poor
        null -> R.string.not_available
    },
)
