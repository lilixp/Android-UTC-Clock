package io.github.lilixp.utcradioclock

import android.app.Application
import android.content.Context
import io.github.lilixp.utcradioclock.data.location.AndroidLocationProvider
import io.github.lilixp.utcradioclock.data.location.LocationProvider
import io.github.lilixp.utcradioclock.data.location.PositionRepository
import io.github.lilixp.utcradioclock.data.location.SharedPreferencesLastPositionStore
import io.github.lilixp.utcradioclock.data.propagation.HttpClient
import io.github.lilixp.utcradioclock.data.propagation.PropagationRepository
import io.github.lilixp.utcradioclock.data.propagation.SharedPreferencesPropagationCache
import io.github.lilixp.utcradioclock.data.propagation.UrlConnectionHttpClient
import io.github.lilixp.utcradioclock.data.settings.SettingsRepository
import io.github.lilixp.utcradioclock.data.settings.SharedPreferencesSettingsRepository
import io.github.lilixp.utcradioclock.data.time.ClockRepository
import io.github.lilixp.utcradioclock.data.time.TimeProvider

/** The app's shared objects, created once. Plain constructor injection: no DI framework needed yet. */
class AppContainer(
    context: Context,
    timeProvider: TimeProvider = TimeProvider(),
    /** The Internet; tests pass a fake, so they never go online. */
    http: HttpClient = UrlConnectionHttpClient(userAgent = USER_AGENT),
    /** The phone's location; tests pass a fake, so they never use the real GPS. */
    locations: LocationProvider = AndroidLocationProvider(context),
) {
    val clockRepository = ClockRepository(timeProvider)
    val locationProvider: LocationProvider = locations
    val settingsRepository: SettingsRepository = SharedPreferencesSettingsRepository(context)
    val positionRepository = PositionRepository(
        settingsRepository,
        locationProvider,
        SharedPreferencesLastPositionStore(context),
        timeProvider,
    )
    val propagationRepository = PropagationRepository(http, SharedPreferencesPropagationCache(context), timeProvider)

    private companion object {
        /** Says who is asking, as feed owners like to know (N0NBH asks for at most one request an hour). */
        const val USER_AGENT = "UTCRadioClock/2.0 (Android; ham radio clock by ER1PL)"
    }
}

open class RadioClockApplication : Application() {
    val container: AppContainer by lazy { createContainer() }

    /** Tests replace it (e.g. with a fixed clock) through a subclass of this application. */
    protected open fun createContainer() = AppContainer(this)
}
