package io.github.lilixp.utcradioclock

import android.app.Application
import android.content.Context
import io.github.lilixp.utcradioclock.data.settings.SettingsRepository
import io.github.lilixp.utcradioclock.data.settings.SharedPreferencesSettingsRepository
import io.github.lilixp.utcradioclock.data.time.ClockRepository
import io.github.lilixp.utcradioclock.data.time.SystemTimeSource

/** The app's shared objects, created once. Plain constructor injection: no DI framework needed yet. */
class AppContainer(context: Context) {
    val clockRepository = ClockRepository(SystemTimeSource)
    val settingsRepository: SettingsRepository = SharedPreferencesSettingsRepository(context)
}

class RadioClockApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
