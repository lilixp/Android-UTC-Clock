package io.github.lilixp.utcradioclock.ui.settings

import androidx.lifecycle.ViewModel
import io.github.lilixp.utcradioclock.data.settings.SettingsRepository
import io.github.lilixp.utcradioclock.domain.model.PositionSource
import io.github.lilixp.utcradioclock.domain.model.StationIdentity
import io.github.lilixp.utcradioclock.domain.model.ThemeMode
import kotlinx.coroutines.flow.StateFlow

/** The Settings screen: callsign, locator, position source and theme, each saved as soon as it changes. */
class SettingsViewModel(private val settings: SettingsRepository) : ViewModel() {

    val station: StateFlow<StationIdentity> = settings.station
    val themeMode: StateFlow<ThemeMode> = settings.themeMode
    val positionSource: StateFlow<PositionSource> = settings.positionSource

    fun setCallsign(callsign: String) = settings.setCallsign(callsign)

    fun setLocator(locator: String) = settings.setLocator(locator)

    fun setPositionSource(source: PositionSource) = settings.setPositionSource(source)

    fun setThemeMode(mode: ThemeMode) = settings.setThemeMode(mode)
}
