package io.github.lilixp.utcradioclock.ui.dashboard

import androidx.annotation.StringRes
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lilixp.utcradioclock.R
import io.github.lilixp.utcradioclock.domain.model.PositionSource
import io.github.lilixp.utcradioclock.domain.model.PositionOrigin
import io.github.lilixp.utcradioclock.ui.theme.UTCRadioClockTheme

/** Test tags for the parts the tests look for. */
object DashboardTags {
    const val DATE = "utc_date"
    const val UTC_TIME = "utc_time"
    const val LOCAL_TIME = "local_time"
    const val LOCAL_LABEL = "local_label"
    const val LOCAL_DATE = "local_date"
    const val ZONE_TILE = "zone_tile"
    const val ZONE_VALUE = "zone_value"
    const val CLOCK_CHANGE_TILE = "clock_change_tile"
    const val CALLSIGN = "callsign"
    const val LOCATOR = "locator"
    const val LOCATION_SOURCE = "location_source"
    const val LOCATION_LOCATOR = "location_locator"
    const val LOCATION_EXTENDED_LOCATOR = "location_extended_locator"
    const val LOCATION_SWITCH = "location_switch"
    const val LOCATION_HOME = "location_home"
    const val LOCATION_LATITUDE = "location_latitude"
    const val LOCATION_LONGITUDE = "location_longitude"
    const val LOCATION_ALTITUDE = "location_altitude"
    const val LOCATION_ACCURACY = "location_accuracy"
    const val LOCATION_DECLINATION = "location_declination"
    const val LOCATION_STATUS = "location_status"
    const val LOCATION_ACTION = "location_action"
}

// Digits of equal width, so the time does not shift sideways as the seconds change
internal const val TABULAR_DIGITS = "tnum"

/**
 * The main screen: the app name with the station (callsign and locator) and the Settings button at the
 * top, the four sections ([AppTab]) at the bottom, and the selected section in between. Each section
 * shows the cards it had on the former single dashboard, unchanged; all of them come from one state.
 * The selected section is kept by the caller, so it survives Settings, a rotation and the app being
 * stopped in the background.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    selectedTab: AppTab = AppTab.CLOCK,
    onSelectTab: (AppTab) -> Unit = {},
    /** The location permission was refused for good ("Don't ask again"). */
    locationPermissionBlocked: Boolean = false,
    onLocationAction: (LocationAction) -> Unit = {},
    /** Manual or Automatic (GPS), chosen on the Location screen: the same setting as in Settings. */
    onPositionSourceChange: (PositionSource) -> Unit = {},
    onOpenSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { TopBarTitle(state.callsign, state.locator) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.open_settings))
                    }
                },
            )
        },
        bottomBar = { TabBar(selectedTab, onSelectTab) },
    ) { padding ->
        // One section at a time; each scrolls on its own (large text, landscape)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (selectedTab) {
                AppTab.CLOCK -> {
                    UtcCard(state)
                    LocalCard(state)
                    ZoneTiles(state.zone)
                }
                AppTab.SUN -> SunScreen(state.sun)
                AppTab.LOCATION -> LocationCard(
                    state.location,
                    state.locator,
                    locationPermissionBlocked,
                    onLocationAction,
                    onPositionSourceChange,
                )
                AppTab.PROPAGATION -> PropagationCard(state.propagation)
            }
        }
    }
}

/** The four sections, the selected one marked; each label is in the phone's language. */
@Composable
private fun TabBar(selectedTab: AppTab, onSelectTab: (AppTab) -> Unit) {
    NavigationBar {
        for (tab in AppTab.entries) {
            NavigationBarItem(
                selected = tab == selectedTab,
                onClick = { onSelectTab(tab) },
                icon = { Icon(painterResource(tab.iconRes), contentDescription = null) },
                label = {
                    // Shrinks only when the word does not fit (large text, narrow phone), never cut
                    Text(
                        text = stringResource(tab.titleRes),
                        maxLines = 1,
                        softWrap = false,
                        autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = LocalTextStyle.current.fontSize),
                    )
                },
                modifier = Modifier.testTag(tab.testTag),
            )
        }
    }
}

/**
 * "UTC Radio Clock" with the station under it, on every section: the callsign, then the locator; each is
 * left out when empty (no line at all without both).
 */
@Composable
private fun TopBarTitle(callsign: String?, locator: String?) {
    Column {
        Text(stringResource(R.string.app_name), maxLines = 1)
        if (callsign != null || locator != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                callsign?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        modifier = Modifier.testTag(DashboardTags.CALLSIGN),
                    )
                }
                locator?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.testTag(DashboardTags.LOCATOR),
                    )
                }
            }
        }
    }
}

/** The most visible element: the UTC time, large, in a highlighted card. */
/** UTC, the main element: the time, large, and the UTC date with the day of the week under it. */
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
            CardLabel(R.drawable.ic_globe, stringResource(R.string.label_utc))
            Text(
                text = state.utcTime,
                style = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR_DIGITS),
                maxLines = 1,
                modifier = Modifier.testTag(DashboardTags.UTC_TIME),
            )
            Text(
                text = state.utcDate,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag(DashboardTags.DATE),
            )
        }
    }
}

/**
 * The local time, smaller, under "LOCAL" (the zone's abbreviation is on the time zone tile, not here);
 * the local date only when it is not the UTC date
 * (e.g. between local midnight and UTC midnight), as in the Windows version.
 */
@Composable
private fun LocalCard(state: DashboardUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CardLabel(
                R.drawable.ic_clock,
                stringResource(R.string.label_local),
                Modifier.testTag(DashboardTags.LOCAL_LABEL),
            )
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
            state.localDate?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag(DashboardTags.LOCAL_DATE),
                )
            }
        }
    }
}

/** "UTC" or "LOCAL" with its icon, in the section label style; [modifier] goes on the text, not on the row. */
@Composable
internal fun CardLabel(@DrawableRes icon: Int, text: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(18.dp),
        )
        SectionLabel(text, modifier)
    }
}

/** Two small tiles under the clocks: the time zone, and the next summer/winter time change. */
@Composable
private fun ZoneTiles(zone: ZoneUi) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Tile(
            title = stringResource(R.string.tile_time_zone),
            value = listOfNotNull(zone.abbreviation, zone.offset).joinToString(" · "),
            details = listOfNotNull(zone.name),
            modifier = Modifier.weight(1f).testTag(DashboardTags.ZONE_TILE),
            valueModifier = Modifier.testTag(DashboardTags.ZONE_VALUE),
        )
        val change = zone.nextChange
        Tile(
            title = stringResource(R.string.tile_clock_change),
            value = change?.date ?: stringResource(R.string.no_clock_change),
            details = if (change != null) {
                listOf(
                    stringResource(R.string.clock_change_times, change.from, change.to),
                    stringResource(if (change.toWinter) R.string.clock_change_to_winter else R.string.clock_change_to_summer),
                )
            } else {
                listOf(stringResource(R.string.no_clock_change_detail))
            },
            modifier = Modifier.weight(1f).testTag(DashboardTags.CLOCK_CHANGE_TILE),
        )
    }
}

/**
 * A small card: a title, one value, and smaller lines under it. The value stays on one line (e.g.
 * "EEST · UTC+03:00") and shrinks only when it does not fit (large text, narrow phone).
 */
@Composable
internal fun Tile(title: String, value: String, details: List<String>, modifier: Modifier, valueModifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxHeight()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = MaterialTheme.typography.titleMedium.fontSize),
                modifier = valueModifier,
            )
            for (line in details) {
                Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
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
    utcDate = "Miercuri, 30 septembrie 2026",
    utcTime = "15:42:31",
    localTime = "18:42:31",
    localDate = null,
    zone = ZoneUi(
        name = "Europe/Chisinau",
        abbreviation = "EEST",
        offset = "UTC+03:00",
        nextChange = ClockChangeUi(date = "25 oct. 2026", from = "04:00", to = "03:00", toWinter = true),
    ),
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
