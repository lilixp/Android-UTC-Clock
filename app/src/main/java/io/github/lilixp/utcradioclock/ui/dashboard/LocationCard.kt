package io.github.lilixp.utcradioclock.ui.dashboard

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.github.lilixp.utcradioclock.R
import io.github.lilixp.utcradioclock.domain.model.GpsStatus
import io.github.lilixp.utcradioclock.domain.model.PositionOrigin

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
 * Latitude, longitude and QTH locator of the position in use, where it came from (GPS with its time and
 * accuracy, or the locator from Settings), and in automatic mode what keeps GPS from giving a position,
 * with a button that helps when there is something the user can do.
 */
@Composable
internal fun LocationCard(
    location: LocationUiState,
    locator: String?,
    /** The permission was refused for good: only the system settings can allow it now. */
    permissionBlocked: Boolean,
    onAction: (LocationAction) -> Unit,
) {
    InfoCard(R.string.section_location) {
        InfoLine(R.string.latitude, location.latitude)
        InfoLine(R.string.longitude, location.longitude)
        InfoLine(R.string.qth, locator)
        val source = when (location.origin) {
            PositionOrigin.GPS -> listOfNotNull(
                stringResource(if (location.approximate) R.string.location_gps_approximate else R.string.location_gps),
                location.fixTime,
                location.accuracy,
            ).joinToString(" · ")
            PositionOrigin.LOCATOR -> stringResource(R.string.location_from_locator)
            PositionOrigin.NONE -> stringResource(R.string.sun_no_location)
        }
        Text(
            text = source,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(DashboardTags.LOCATION_SOURCE),
        )
        if (location.automatic) GpsProblem(location, permissionBlocked, onAction)
    }
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
        modifier = Modifier.testTag(DashboardTags.LOCATION_STATUS),
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
