package io.github.lilixp.utcradioclock.ui.settings

import androidx.annotation.StringRes
import io.github.lilixp.utcradioclock.ui.dashboard.CardLabel
import io.github.lilixp.utcradioclock.ui.dashboard.CARD_PADDING
import androidx.compose.ui.text.style.TextAlign
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.lilixp.utcradioclock.R
import io.github.lilixp.utcradioclock.domain.model.PositionSource
import io.github.lilixp.utcradioclock.domain.model.StationIdentity
import io.github.lilixp.utcradioclock.domain.model.ThemeMode
import io.github.lilixp.utcradioclock.ui.theme.UTCRadioClockTheme

object SettingsTags {
    const val CALLSIGN_FIELD = "callsign_field"
    const val LOCATOR_FIELD = "locator_field"
    const val ABOUT_PROPAGATION = "about_propagation"
    const val POSITION_HINT = "position_hint"
}

/**
 * Station (callsign, Maidenhead locator, where the position comes from), appearance (theme) and about the app (version, author,
 * where the propagation data come from). Every change is saved at once
 * (the screen says so), so there is nothing to lose when going back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    station: StationIdentity,
    themeMode: ThemeMode,
    onCallsignChange: (String) -> Unit,
    onLocatorChange: (String) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit,
    /** Shown in About the app, e.g. "2.0.0". */
    appVersion: String = "",
    positionSource: PositionSource = PositionSource.MANUAL,
    /** Choosing [PositionSource.AUTOMATIC] is also when the app asks for the location permission. */
    onPositionSourceChange: (PositionSource) -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsCard(R.drawable.ic_antenna, R.string.section_station) {
                OutlinedTextField(
                    value = station.callsign,
                    onValueChange = onCallsignChange,
                    label = { Text(stringResource(R.string.callsign)) },
                    supportingText = {
                        val max = StationIdentity.MAX_CALLSIGN_LENGTH
                        Text(pluralStringResource(R.plurals.callsign_hint, max, max))
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth().testTag(SettingsTags.CALLSIGN_FIELD),
                )
                OutlinedTextField(
                    value = station.locator,
                    onValueChange = onLocatorChange,
                    label = { Text(stringResource(R.string.locator)) },
                    supportingText = { Text(stringResource(R.string.locator_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth().testTag(SettingsTags.LOCATOR_FIELD),
                )
                PositionSourceSelector(positionSource, onPositionSourceChange)
            }
            SettingsCard(R.drawable.ic_palette, R.string.section_appearance) {
                ThemeSelector(themeMode, onThemeModeChange)
            }
            SettingsCard(R.drawable.ic_info, R.string.section_about) {
                Text(
                    text = "${stringResource(R.string.app_name)} · ${stringResource(R.string.about_version, appVersion)}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(stringResource(R.string.about_author), style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = stringResource(R.string.about_propagation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(SettingsTags.ABOUT_PROPAGATION),
                )
            }
            Text(
                text = stringResource(R.string.settings_saved_automatically),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** A Settings card: its label with an icon, as on the app's screens (same card, same padding). */
@Composable
private fun SettingsCard(@DrawableRes icon: Int, @StringRes title: Int, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(CARD_PADDING),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CardLabel(icon, stringResource(title))
            content()
        }
    }
}

/** System / Light / Dark, the same control that used to be on the dashboard. */
@Composable
private fun ThemeSelector(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val options = listOf(
        ThemeMode.SYSTEM to R.string.theme_system,
        ThemeMode.LIGHT to R.string.theme_light,
        ThemeMode.DARK to R.string.theme_dark,
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (mode, label) ->
            SegmentedButton(
                selected = mode == selected,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                // No check mark: with the phone's large text it pushed "Întunecat" past the edge;
                // the chosen option is still shown by its filled background
                icon = {},
            ) {
                Text(stringResource(label))
            }
        }
    }
}

/** Manual (the locator) / Automatic (GPS), with a line on what the choice means. */
@Composable
private fun PositionSourceSelector(selected: PositionSource, onSelect: (PositionSource) -> Unit) {
    // A small label over the choice, like the tiles' titles on the app's screens
    Text(
        text = stringResource(R.string.position_source),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val options = listOf(
        PositionSource.MANUAL to R.string.position_manual,
        PositionSource.AUTOMATIC to R.string.position_automatic,
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (source, label) ->
            SegmentedButton(
                selected = source == selected,
                onClick = { onSelect(source) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                icon = {}, // as for the theme: no check mark, so the labels fit with large text
            ) {
                Text(stringResource(label))
            }
        }
    }
    Text(
        text = stringResource(
            if (selected == PositionSource.AUTOMATIC) R.string.position_automatic_hint else R.string.position_manual_hint,
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag(SettingsTags.POSITION_HINT),
    )
}

@Preview(showBackground = true)
@Composable
private fun SettingsPreview() {
    UTCRadioClockTheme(darkTheme = false) {
        SettingsScreen(StationIdentity("ER1PL", "KN46dw"), ThemeMode.SYSTEM, {}, {}, {}, {}, appVersion = "2.0.0")
    }
}
