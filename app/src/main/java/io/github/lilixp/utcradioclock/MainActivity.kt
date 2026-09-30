package io.github.lilixp.utcradioclock

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import io.github.lilixp.utcradioclock.domain.model.ThemeMode
import io.github.lilixp.utcradioclock.ui.dashboard.DashboardScreen
import io.github.lilixp.utcradioclock.ui.dashboard.DashboardViewModel
import io.github.lilixp.utcradioclock.ui.settings.SettingsScreen
import io.github.lilixp.utcradioclock.ui.settings.SettingsViewModel
import io.github.lilixp.utcradioclock.ui.theme.UTCRadioClockTheme
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as RadioClockApplication).container
        val factory = viewModelFactory {
            initializer {
                DashboardViewModel(container.clockRepository, container.settingsRepository) { Locale.getDefault() }
            }
            initializer { SettingsViewModel(container.settingsRepository) }
        }
        setContent {
            val dashboard: DashboardViewModel = viewModel(factory = factory)
            val state by dashboard.uiState.collectAsStateWithLifecycle()
            // Two screens only, so a flag is enough; a navigation library can come with more screens
            var showSettings by rememberSaveable { mutableStateOf(false) }
            val darkTheme = when (state.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
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
                    val settings: SettingsViewModel = viewModel(factory = factory)
                    val station by settings.station.collectAsStateWithLifecycle()
                    BackHandler { showSettings = false }
                    SettingsScreen(
                        station = station,
                        onCallsignChange = settings::setCallsign,
                        onLocatorChange = settings::setLocator,
                        onBack = { showSettings = false },
                    )
                } else {
                    DashboardScreen(
                        state = state,
                        onOpenSettings = { showSettings = true },
                        onThemeModeChange = dashboard::setThemeMode,
                    )
                }
            }
        }
    }
}
