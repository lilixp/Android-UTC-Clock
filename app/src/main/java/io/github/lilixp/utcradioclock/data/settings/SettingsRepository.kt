package io.github.lilixp.utcradioclock.data.settings

import android.content.Context
import androidx.core.content.edit
import io.github.lilixp.utcradioclock.domain.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** User settings that survive a restart. */
interface SettingsRepository {
    val themeMode: StateFlow<ThemeMode>
    fun setThemeMode(mode: ThemeMode)
}

/** Settings kept in SharedPreferences (a handful of small values, read once at start). */
class SharedPreferencesSettingsRepository(context: Context) : SettingsRepository {

    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        ThemeMode.entries.firstOrNull { it.name == preferences.getString(KEY_THEME_MODE, null) }
            ?: ThemeMode.SYSTEM,
    )
    override val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    override fun setThemeMode(mode: ThemeMode) {
        preferences.edit { putString(KEY_THEME_MODE, mode.name) }
        _themeMode.value = mode
    }

    private companion object {
        const val FILE_NAME = "settings" // also named in the backup rules (res/xml)
        const val KEY_THEME_MODE = "theme_mode"
    }
}
