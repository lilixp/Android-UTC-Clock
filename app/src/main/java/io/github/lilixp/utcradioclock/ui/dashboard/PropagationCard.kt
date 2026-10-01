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
import io.github.lilixp.utcradioclock.domain.model.BandGroup
import io.github.lilixp.utcradioclock.domain.model.ConditionLevel
import io.github.lilixp.utcradioclock.ui.theme.LocalConditionColors

/** Test tags of the PROPAGATION card. */
object PropagationTags {
    const val SFI = "propagation_sfi"
    const val K = "propagation_k"
    const val A = "propagation_a"
    fun band(group: BandGroup) = "propagation_band_${group.range}"
}

private val BoxShape = RoundedCornerShape(8.dp)

/**
 * SFI, K and A on the first row, the four band groups on the second, each in its colour; a tap on a
 * band explains it. Without data: a clear message, never invented values.
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
    var openBand by rememberSaveable { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            IndexBox(R.string.index_sfi, propagation.solarFlux, PropagationTags.SFI, Modifier.weight(1f))
            IndexBox(R.string.index_k, propagation.kIndex, PropagationTags.K, Modifier.weight(1f))
            IndexBox(R.string.index_a, propagation.aIndex, PropagationTags.A, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (band in propagation.bands) {
                BandBox(band, Modifier.weight(1f)) { openBand = band.group.name }
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
    }
    propagation.bands.firstOrNull { it.group.name == openBand }?.let { band ->
        BandDialog(band, propagation.isDay, propagation.dayNightByClock) { openBand = null }
    }
}

/** A box such as "SFI 93", coloured by its level; "SFI —" in the neutral colour when not reported. */
@Composable
private fun IndexBox(@StringRes format: Int, index: IndexUi?, tag: String, modifier: Modifier) {
    val colors = LocalConditionColors.current.of(index?.level)
    Surface(shape = BoxShape, color = colors.container, contentColor = colors.content, modifier = modifier.testTag(tag)) {
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

@Composable
private fun levelName(level: ConditionLevel?): String = stringResource(
    when (level) {
        ConditionLevel.GOOD -> R.string.level_good
        ConditionLevel.FAIR -> R.string.level_fair
        ConditionLevel.POOR -> R.string.level_poor
        null -> R.string.not_available
    },
)
