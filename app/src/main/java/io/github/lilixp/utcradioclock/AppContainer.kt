package io.github.lilixp.utcradioclock

import android.app.Application
import android.content.Context
import io.github.lilixp.utcradioclock.data.settings.SettingsRepository
import io.github.lilixp.utcradioclock.data.settings.SharedPreferencesSettingsRepository
import io.github.lilixp.utcradioclock.data.time.ClockRepository
import io.github.lilixp.utcradioclock.data.time.TimeProvider

/** The app's shared objects, created once. Plain constructor injection: no DI framework needed yet. */
class AppContainer(context: Context, timeProvider: TimeProvider = TimeProvider()) {
    val clockRepository = ClockRepository(timeProvider)
    val settingsRepository: SettingsRepository = SharedPreferencesSettingsRepository(context)
}

open class RadioClockApplication : Application() {
    val container: AppContainer by lazy { createContainer() }

    /** Tests replace it (e.g. with a fixed clock) through a subclass of this application. */
    protected open fun createContainer() = AppContainer(this)
}
