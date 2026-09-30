package io.github.lilixp.utcradioclock.ui.settings

import androidx.lifecycle.ViewModel
import io.github.lilixp.utcradioclock.data.settings.SettingsRepository
import io.github.lilixp.utcradioclock.domain.model.StationIdentity
import kotlinx.coroutines.flow.StateFlow

/** The Settings screen: callsign and locator, saved as they are typed. */
class SettingsViewModel(private val settings: SettingsRepository) : ViewModel() {

    val station: StateFlow<StationIdentity> = settings.station

    fun setCallsign(callsign: String) = settings.setCallsign(callsign)

    fun setLocator(locator: String) = settings.setLocator(locator)
}
