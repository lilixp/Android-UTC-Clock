package io.github.lilixp.utcradioclock.ui.dashboard

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lilixp.utcradioclock.R
import io.github.lilixp.utcradioclock.domain.model.ThemeMode
import io.github.lilixp.utcradioclock.ui.theme.UTCRadioClockTheme

/** Test tags for the parts the tests look for. */
object DashboardTags {
    const val DATE = "utc_date"
    const val UTC_TIME = "utc_time"
    const val LOCAL_TIME = "local_time"
}

// Digits of equal width, so the time does not shift sideways as the seconds change
private const val TABULAR_DIGITS = "tnum"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(state: DashboardUiState, onThemeModeChange: (ThemeMode) -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = state.utcDate,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(DashboardTags.DATE),
                )
            }
            item { UtcCard(state) }
            item { LocalCard(state) }
            item {
                InfoCard(R.string.section_sun) {
                    InfoLine(R.string.sunrise, state.sunrise)
                    InfoLine(R.string.sunset, state.sunset)
                    InfoLine(R.string.day_length, state.dayLength)
                }
            }
            item {
                InfoCard(R.string.section_location) {
                    InfoLine(R.string.latitude, state.latitude)
                    InfoLine(R.string.longitude, state.longitude)
                    InfoLine(R.string.qth, state.locator)
                }
            }
            item {
                InfoCard(R.string.section_propagation) {
                    Text(stringResource(R.string.propagation_later), style = MaterialTheme.typography.bodyLarge)
                }
            }
            item {
                InfoCard(R.string.section_appearance) {
                    ThemeSelector(state.themeMode, onThemeModeChange)
                }
            }
        }
    }
}

/** The most visible element: the UTC time, large, in a highlighted card. */
@Composable
private fun UtcCard(state: DashboardUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SectionLabel(stringResource(R.string.label_utc))
            Text(
                text = state.utcTime,
                style = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR_DIGITS),
                maxLines = 1,
                modifier = Modifier.testTag(DashboardTags.UTC_TIME),
            )
        }
    }
}

@Composable
private fun LocalCard(state: DashboardUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SectionLabel(stringResource(R.string.label_local))
            Text(
                text = state.localTime,
                style = TextStyle(
                    fontSize = 40.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFeatureSettings = TABULAR_DIGITS,
                ),
                maxLines = 1,
                modifier = Modifier.testTag(DashboardTags.LOCAL_TIME),
            )
            state.localDate?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text(
                text = state.timeZone,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InfoCard(@StringRes title: Int, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SectionLabel(stringResource(title))
            content()
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.secondary,
    )
}

/** "Sunrise: 06:58", or "Sunrise: —" while the value is not known. */
@Composable
private fun InfoLine(@StringRes format: Int, value: String?) {
    Text(
        text = stringResource(format, value ?: stringResource(R.string.not_available)),
        style = MaterialTheme.typography.bodyLarge,
    )
}

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
            ) {
                Text(stringResource(label))
            }
        }
    }
}

private val PreviewState = DashboardUiState(
    utcDate = "30 septembrie 2026",
    utcTime = "15:42:31",
    localTime = "18:42:31",
    localDate = null,
    timeZone = "Europe/Chisinau · UTC+03:00",
)

@Preview(showBackground = true)
@Composable
private fun DashboardLightPreview() {
    UTCRadioClockTheme(darkTheme = false) { DashboardScreen(PreviewState) {} }
}

@Preview(showBackground = true)
@Composable
private fun DashboardDarkPreview() {
    UTCRadioClockTheme(darkTheme = true) { DashboardScreen(PreviewState) {} }
}
