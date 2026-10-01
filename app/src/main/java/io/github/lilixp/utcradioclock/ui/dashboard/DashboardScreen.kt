package io.github.lilixp.utcradioclock.ui.dashboard

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lilixp.utcradioclock.R
import io.github.lilixp.utcradioclock.domain.model.PositionOrigin
import io.github.lilixp.utcradioclock.ui.theme.UTCRadioClockTheme

/** Test tags for the parts the tests look for. */
object DashboardTags {
    const val DATE = "utc_date"
    const val UTC_TIME = "utc_time"
    const val LOCAL_TIME = "local_time"
    const val CALLSIGN = "callsign"
    const val LOCATOR = "locator"
    const val LOCATION_SOURCE = "location_source"
    const val LOCATION_STATUS = "location_status"
    const val LOCATION_ACTION = "location_action"
}

// Digits of equal width, so the time does not shift sideways as the seconds change
private const val TABULAR_DIGITS = "tnum"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    /** The location permission was refused for good ("Don't ask again"). */
    locationPermissionBlocked: Boolean = false,
    onLocationAction: (LocationAction) -> Unit = {},
    onOpenSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.open_settings))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                // The date on the left, the station (callsign and locator) discreetly on the right
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = state.utcDate,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).testTag(DashboardTags.DATE),
                    )
                    StationBadge(state.callsign, state.locator)
                }
            }
            item { UtcCard(state) }
            item { LocalCard(state) }
            item { SunCard(state.sun) }
            item { LocationCard(state.location, state.locator, locationPermissionBlocked, onLocationAction) }
            item { PropagationCard(state.propagation) }
        }
    }
}

/**
 * Sunrise, sunset, solar noon, day length and civil twilight in local time, or a message when there is
 * no position (no or no valid locator). On polar days a sentence replaces sunrise and sunset.
 */
@Composable
private fun SunCard(sun: SunUiState) {
    InfoCard(R.string.section_sun) {
        when (sun.status) {
            SunStatus.NO_LOCATOR, SunStatus.NO_POSITION, SunStatus.INVALID_LOCATOR -> {
                Text(stringResource(R.string.sun_no_location), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = when (sun.status) {
                        SunStatus.NO_LOCATOR -> stringResource(R.string.sun_enter_locator)
                        SunStatus.NO_POSITION -> stringResource(R.string.sun_no_position)
                        else -> stringResource(R.string.sun_invalid_locator, sun.locator.orEmpty())
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SunStatus.NORMAL, SunStatus.MIDNIGHT_SUN, SunStatus.POLAR_NIGHT -> {
                if (sun.status == SunStatus.NORMAL) {
                    InfoLine(R.string.sunrise, sun.sunrise)
                    InfoLine(R.string.sunset, sun.sunset)
                } else {
                    val polar = if (sun.status == SunStatus.MIDNIGHT_SUN) R.string.sun_midnight_sun else R.string.sun_polar_night
                    Text(stringResource(polar), style = MaterialTheme.typography.bodyLarge)
                }
                InfoLine(R.string.solar_noon, sun.solarNoon)
                InfoLine(R.string.day_length, sun.dayLength)
                if (sun.civilDawn != null || sun.civilDusk != null) {
                    val dash = stringResource(R.string.not_available)
                    Text(
                        text = stringResource(R.string.civil_twilight, sun.civilDawn ?: dash, sun.civilDusk ?: dash),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

/** Callsign above locator, right-aligned; each is left out when empty. */
@Composable
private fun StationBadge(callsign: String?, locator: String?) {
    Column(horizontalAlignment = Alignment.End) {
        callsign?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                modifier = Modifier.testTag(DashboardTags.CALLSIGN),
            )
        }
        locator?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.testTag(DashboardTags.LOCATOR),
            )
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
internal fun InfoCard(@StringRes title: Int, content: @Composable () -> Unit) {
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
internal fun InfoLine(@StringRes format: Int, value: String?) {
    Text(
        text = stringResource(format, value ?: stringResource(R.string.not_available)),
        style = MaterialTheme.typography.bodyLarge,
    )
}

private val PreviewState = DashboardUiState(
    utcDate = "30 septembrie 2026",
    utcTime = "15:42:31",
    localTime = "18:42:31",
    localDate = null,
    timeZone = "Europe/Chisinau · UTC+03:00",
    callsign = "ER1PL",
    sun = SunUiState(
        status = SunStatus.NORMAL,
        sunrise = "07:04",
        sunset = "18:49",
        solarNoon = "12:57",
        dayLength = "11h 44m",
        civilDawn = "06:33",
        civilDusk = "19:19",
        locator = "KN46dw",
    ),
    location = LocationUiState(
        latitude = "46,9375° N",
        longitude = "28,2917° E",
        origin = PositionOrigin.LOCATOR,
    ),
    locator = "KN46dw",
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
