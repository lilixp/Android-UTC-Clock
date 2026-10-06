package io.github.lilixp.utcradioclock.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lilixp.utcradioclock.R
import io.github.lilixp.utcradioclock.domain.model.GpsStatus
import io.github.lilixp.utcradioclock.domain.model.PositionOrigin
import io.github.lilixp.utcradioclock.domain.model.PositionSource

/** What the button on the LOCATION card asks the app to do. */
enum class LocationAction {
    /** Show Android's permission dialog. */
    REQUEST_PERMISSION,

    /** The dialog can no longer be shown ("Don't ask again"): open the app's page in the system settings. */
    OPEN_APP_SETTINGS,

    /** Open the system's location settings. */
    OPEN_LOCATION_SETTINGS,
}

/**
 * The LOCATION screen. First the QTH locator, large (with its 8 characters for a GPS position), where the
 * position comes from (with the GPS time in UTC) and the Manual / Automatic (GPS) choice, the same setting
 * as in Settings; in automatic mode, what keeps GPS from giving a position, with a button that helps. When
 * portable, how far and which way home is. Then tiles: latitude and longitude, the GPS height and accuracy,
 * and the magnetic declination for pointing an antenna with a compass.
 */
@Composable
internal fun LocationCard(
    location: LocationUiState,
    locator: String?,
    /** The permission was refused for good: only the system settings can allow it now. */
    permissionBlocked: Boolean,
    onAction: (LocationAction) -> Unit,
    onPositionSourceChange: (PositionSource) -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        InfoCard(R.string.section_location) {
            Text(
                text = locator ?: stringResource(R.string.not_available),
                style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = TABULAR_DIGITS),
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                modifier = Modifier.testTag(DashboardTags.LOCATION_LOCATOR),
            )
            location.extendedLocator?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = TABULAR_DIGITS),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(DashboardTags.LOCATION_EXTENDED_LOCATOR),
                )
            }
            Source(location)
            PositionSourceSwitch(location.automatic, onPositionSourceChange)
            if (location.automatic) GpsProblem(location, permissionBlocked, onAction)
        }
        location.home?.let { home ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.location_home, home.distance, home.locator, home.bearing),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).testTag(DashboardTags.LOCATION_HOME),
                )
            }
        }
        if (location.latitude != null && location.longitude != null) Tiles(location)
    }
}

/** "Poziție din GPS · 15:42 UTC", "Locator manual · centrul pătratului"…, or why there is no position. */
@Composable
private fun Source(location: LocationUiState) {
    val source = location.source
    if (source == null) {
        Text(
            text = location.invalidLocator?.let { stringResource(R.string.sun_invalid_locator, it) }
                ?: stringResource(R.string.sun_no_location),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(DashboardTags.LOCATION_SOURCE),
        )
        return
    }
    val name = stringResource(
        when (source) {
            SunPositionSource.MANUAL -> R.string.sun_source_manual
            SunPositionSource.BACKUP_LOCATOR -> R.string.sun_source_backup
            SunPositionSource.GPS -> R.string.sun_source_gps
            SunPositionSource.LAST_GPS -> R.string.sun_source_last_gps
        },
    )
    val text = when (source) {
        SunPositionSource.GPS, SunPositionSource.LAST_GPS ->
            location.fixTime?.let { stringResource(R.string.location_source_time, name, it) } ?: name
        SunPositionSource.MANUAL, SunPositionSource.BACKUP_LOCATOR -> stringResource(R.string.location_square_centre, name)
    }
    // The last GPS position (location off, GPS silent, too old) is marked, so it is not taken for a new one
    val old = source == SunPositionSource.LAST_GPS
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (old) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
        contentColor = if (old) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.padding(top = 2.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp).testTag(DashboardTags.LOCATION_SOURCE),
        )
    }
}

/** Manual | Automatic (GPS): the same setting as in Settings. */
@Composable
private fun PositionSourceSwitch(automatic: Boolean, onChange: (PositionSource) -> Unit) {
    val options = listOf(
        PositionSource.MANUAL to R.string.position_manual,
        PositionSource.AUTOMATIC to R.string.position_automatic,
    )
    val selected = if (automatic) PositionSource.AUTOMATIC else PositionSource.MANUAL
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag(DashboardTags.LOCATION_SWITCH),
    ) {
        options.forEachIndexed { index, (source, label) ->
            SegmentedButton(
                selected = source == selected,
                onClick = { if (source != selected) onChange(source) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                icon = {}, // as in Settings: no check mark, so the labels fit with large text
            ) {
                // One line: "Automatic (GPS)" shrinks a little with large text rather than breaking in two
                Text(
                    text = stringResource(label),
                    maxLines = 1,
                    softWrap = false,
                    autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = LocalTextStyle.current.fontSize),
                )
            }
        }
    }
}

/** Latitude and longitude; the GPS height and accuracy; the magnetic declination across the whole width. */
@Composable
private fun Tiles(location: LocationUiState) {
    val dash = stringResource(R.string.not_available)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TileRow {
            Tile(
                stringResource(R.string.tile_latitude), location.latitude ?: dash, emptyList(),
                Modifier.weight(1f).testTag(DashboardTags.LOCATION_LATITUDE),
            )
            Tile(
                stringResource(R.string.tile_longitude), location.longitude ?: dash, emptyList(),
                Modifier.weight(1f).testTag(DashboardTags.LOCATION_LONGITUDE),
            )
        }
        if (location.origin == PositionOrigin.GPS) {
            TileRow {
                Tile(
                    stringResource(R.string.tile_altitude), location.altitude ?: dash, listOf(stringResource(R.string.altitude_detail)),
                    Modifier.weight(1f).testTag(DashboardTags.LOCATION_ALTITUDE),
                )
                Tile(
                    stringResource(R.string.tile_accuracy),
                    location.accuracy ?: dash,
                    listOf(stringResource(if (location.approximate) R.string.accuracy_gps_approximate else R.string.accuracy_gps_exact)),
                    Modifier.weight(1f).testTag(DashboardTags.LOCATION_ACCURACY),
                )
            }
        }
        location.declination?.let {
            Tile(
                stringResource(R.string.tile_declination), it, listOf(stringResource(R.string.declination_detail)),
                Modifier.fillMaxWidth().testTag(DashboardTags.LOCATION_DECLINATION),
            )
        }
    }
}

@Composable
private fun TileRow(content: @Composable RowScope.() -> Unit) {
    Row(
        // No intrinsic height: it would measure the tiles' self-shrinking values at their smallest. Tiles of a
        // row have the same lines (title, value, one detail where any has one), so they are as tall anyway.
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** Why there is no (new) GPS position, and the button that can fix it; nothing when all is well. */
@Composable
private fun GpsProblem(location: LocationUiState, permissionBlocked: Boolean, onAction: (LocationAction) -> Unit) {
    val (message, action) = when (location.gps) {
        // Looking again while a position is shown is routine, not worth a line
        GpsStatus.SEARCHING -> if (location.origin == PositionOrigin.GPS) return else R.string.location_searching to null
        GpsStatus.NO_PERMISSION -> if (permissionBlocked) {
            R.string.location_permission_blocked to LocationAction.OPEN_APP_SETTINGS
        } else {
            R.string.location_no_permission to LocationAction.REQUEST_PERMISSION
        }
        GpsStatus.LOCATION_OFF -> R.string.location_off to LocationAction.OPEN_LOCATION_SETTINGS
        GpsStatus.UNAVAILABLE -> R.string.location_unavailable to null
        GpsStatus.OK, null -> return
    }
    val serious = location.gps == GpsStatus.NO_PERMISSION || location.gps == GpsStatus.LOCATION_OFF
    Text(
        text = stringResource(message),
        style = MaterialTheme.typography.bodyMedium,
        color = if (serious) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp).testTag(DashboardTags.LOCATION_STATUS),
    )
    if (action != null) {
        val label = when (action) {
            LocationAction.REQUEST_PERMISSION -> R.string.location_allow
            LocationAction.OPEN_APP_SETTINGS -> R.string.location_open_app_settings
            LocationAction.OPEN_LOCATION_SETTINGS -> R.string.location_turn_on
        }
        TextButton(onClick = { onAction(action) }, modifier = Modifier.testTag(DashboardTags.LOCATION_ACTION)) {
            Text(stringResource(label))
        }
    }
}
