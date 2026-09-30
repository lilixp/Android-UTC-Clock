package io.github.lilixp.utcradioclock.data.settings

import android.content.Context
import androidx.core.content.edit
import io.github.lilixp.utcradioclock.domain.model.StationIdentity
import io.github.lilixp.utcradioclock.domain.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** User settings that survive a restart. */
interface SettingsRepository {
    val themeMode: StateFlow<ThemeMode>
    val station: StateFlow<StationIdentity>
    fun setThemeMode(mode: ThemeMode)

    /** Saves the callsign, cleaned by [StationIdentity.normalizeCallsign]. */
    fun setCallsign(callsign: String)

    /** Saves the locator, cleaned by [StationIdentity.normalizeLocator]. */
    fun setLocator(locator: String)
}

/** Settings kept in SharedPreferences (a handful of small values, read once at start). */
class SharedPreferencesSettingsRepository(context: Context) : SettingsRepository {

    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        ThemeMode.entries.firstOrNull { it.name == preferences.getString(KEY_THEME_MODE, null) }
            ?: ThemeMode.SYSTEM,
    )
    override val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    // Saved values pass through the same cleaning, in case the file was changed outside the app
    private val _station = MutableStateFlow(
        StationIdentity(
            callsign = StationIdentity.normalizeCallsign(
                preferences.getString(KEY_CALLSIGN, null) ?: StationIdentity.DEFAULT_CALLSIGN,
            ),
            locator = StationIdentity.normalizeLocator(preferences.getString(KEY_LOCATOR, null).orEmpty()),
        ),
    )
    override val station: StateFlow<StationIdentity> = _station.asStateFlow()

    override fun setThemeMode(mode: ThemeMode) {
        preferences.edit { putString(KEY_THEME_MODE, mode.name) }
        _themeMode.value = mode
    }

    override fun setCallsign(callsign: String) {
        val value = StationIdentity.normalizeCallsign(callsign)
        preferences.edit { putString(KEY_CALLSIGN, value) }
        _station.value = _station.value.copy(callsign = value)
    }

    override fun setLocator(locator: String) {
        val value = StationIdentity.normalizeLocator(locator)
        preferences.edit { putString(KEY_LOCATOR, value) }
        _station.value = _station.value.copy(locator = value)
    }

    private companion object {
        const val FILE_NAME = "settings" // also named in the backup rules (res/xml)
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_CALLSIGN = "callsign"
        const val KEY_LOCATOR = "locator"
    }
}
