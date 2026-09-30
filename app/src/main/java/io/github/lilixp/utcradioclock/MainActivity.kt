package io.github.lilixp.utcradioclock

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.lilixp.utcradioclock.domain.model.ThemeMode
import io.github.lilixp.utcradioclock.ui.dashboard.DashboardScreen
import io.github.lilixp.utcradioclock.ui.dashboard.DashboardViewModel
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
        }
        setContent {
            val viewModel: DashboardViewModel = viewModel(factory = factory)
            val state by viewModel.uiState.collectAsStateWithLifecycle()
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
                DashboardScreen(state = state, onThemeModeChange = viewModel::setThemeMode)
            }
        }
    }
}
