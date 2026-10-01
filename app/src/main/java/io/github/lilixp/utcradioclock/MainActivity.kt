package io.github.lilixp.utcradioclock

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.lilixp.utcradioclock.domain.model.PositionSource
import io.github.lilixp.utcradioclock.ui.dashboard.AppTab
import io.github.lilixp.utcradioclock.ui.dashboard.DashboardScreen
import io.github.lilixp.utcradioclock.ui.dashboard.LocationAction
import io.github.lilixp.utcradioclock.ui.dashboard.DashboardViewModel
import io.github.lilixp.utcradioclock.ui.settings.SettingsScreen
import io.github.lilixp.utcradioclock.ui.settings.SettingsViewModel
import io.github.lilixp.utcradioclock.ui.theme.UTCRadioClockTheme
import io.github.lilixp.utcradioclock.ui.theme.isDark
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val container get() = (application as RadioClockApplication).container

    /**
     * Android said no without showing its dialog ("Don't ask again", or refused twice): only the app's
     * page in the system settings can allow location now, so the LOCATION card offers that instead.
     */
    private var locationBlocked by mutableStateOf(false)

    /** Android's own permission dialog; precise and approximate are asked together, the user picks. */
    private val locationPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val granted = result.values.any { it }
            locationBlocked = !granted && LOCATION_PERMISSIONS.none(::shouldShowRequestPermissionRationale)
            container.positionRepository.refreshNow()
        }

    /** Asked only when the user wants the automatic position, never at start. */
    private fun requestLocation() {
        if (container.locationProvider.hasPermission()) {
            container.positionRepository.refreshNow()
        } else {
            locationPermission.launch(LOCATION_PERMISSIONS)
        }
    }

    private fun onLocationAction(action: LocationAction) {
        when (action) {
            LocationAction.REQUEST_PERMISSION -> requestLocation()
            LocationAction.OPEN_APP_SETTINGS -> open(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
            )
            LocationAction.OPEN_LOCATION_SETTINGS -> open(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }
    }

    /** A system settings page; the user decides there, the app changes nothing itself. */
    private fun open(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // Some devices have no such page; the card keeps saying what is missing
        }
    }

    override fun onResume() {
        super.onResume()
        // Back from the system settings (permission, location turned on) or from another app: look again
        container.positionRepository.refreshNow()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_LOCATION_BLOCKED, locationBlocked)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        locationBlocked = savedInstanceState?.getBoolean(KEY_LOCATION_BLOCKED) == true
        val factory = viewModelFactory {
            initializer {
                DashboardViewModel(
                    clock = container.clockRepository,
                    settings = container.settingsRepository,
                    positions = container.positionRepository,
                    propagation = container.propagationRepository.updates(),
                ) { Locale.getDefault() }
            }
            initializer { SettingsViewModel(container.settingsRepository) }
        }
        setContent {
            // The theme is a setting of the whole app, so it comes from the Settings ViewModel
            val settings: SettingsViewModel = viewModel(factory = factory)
            val themeMode by settings.themeMode.collectAsStateWithLifecycle()
            val darkTheme = themeMode.isDark(isSystemInDarkTheme())
            // Settings over the four sections; a flag and the selected section are enough (no navigation
            // library). Both are kept here, above the two screens, so the section survives Settings, a
            // rotation and the app being stopped in the background.
            var showSettings by rememberSaveable { mutableStateOf(false) }
            var selectedTab by rememberSaveable { mutableStateOf(AppTab.CLOCK) }
            // Status and navigation bar icons must follow the app theme, also when it overrides the system one
            DisposableEffect(darkTheme) {
                val style = if (darkTheme) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            UTCRadioClockTheme(darkTheme = darkTheme) {
                if (showSettings) {
                    val station by settings.station.collectAsStateWithLifecycle()
                    val positionSource by settings.positionSource.collectAsStateWithLifecycle()
                    BackHandler { showSettings = false }
                    SettingsScreen(
                        station = station,
                        themeMode = themeMode,
                        onCallsignChange = settings::setCallsign,
                        onLocatorChange = settings::setLocator,
                        onThemeModeChange = settings::setThemeMode,
                        onBack = { showSettings = false },
                        appVersion = BuildConfig.VERSION_NAME,
                        positionSource = positionSource,
                        onPositionSourceChange = { source ->
                            settings.setPositionSource(source)
                            if (source == PositionSource.AUTOMATIC) requestLocation()
                        },
                    )
                } else {
                    // Back from Sun, Location or Propagation goes to the Clock; from the Clock it leaves the app
                    BackHandler(enabled = selectedTab != AppTab.CLOCK) { selectedTab = AppTab.CLOCK }
                    // One ViewModel for the four sections, collected only while they are shown: the clock,
                    // GPS and N0NBH run once, whatever the section, and not behind Settings
                    val dashboard: DashboardViewModel = viewModel(factory = factory)
                    val state by dashboard.uiState.collectAsStateWithLifecycle()
                    DashboardScreen(
                        state = state,
                        selectedTab = selectedTab,
                        onSelectTab = { selectedTab = it },
                        onOpenSettings = { showSettings = true },
                        locationPermissionBlocked = locationBlocked,
                        onLocationAction = ::onLocationAction,
                    )
                }
            }
        }
    }

    private companion object {
        val LOCATION_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        const val KEY_LOCATION_BLOCKED = "location_blocked"
    }
}
