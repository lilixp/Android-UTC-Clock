package io.github.lilixp.utcradioclock.testing

import io.github.lilixp.utcradioclock.AppContainer
import io.github.lilixp.utcradioclock.RadioClockApplication
import io.github.lilixp.utcradioclock.data.propagation.HttpClient
import io.github.lilixp.utcradioclock.data.time.TimeProvider
import java.time.Clock
import java.time.ZoneOffset

/**
 * The real app for Robolectric tests, with its clock stopped at [START] in Chișinău, so screens show
 * known times instead of the PC's real time, and the real N0NBH feed of 1 October 2026 instead of the
 * Internet. Used with `@Config(application = FixedTimeApplication::class)`.
 */
open class FixedTimeApplication : RadioClockApplication() {
    protected open val http: HttpClient = HttpClient { REAL_FEED }

    override fun createContainer() =
        AppContainer(this, TimeProvider(Clock.fixed(START, ZoneOffset.UTC)) { CHISINAU }, http)
}

/** The same app with no Internet connection and nothing saved. */
class OfflineFixedTimeApplication : FixedTimeApplication() {
    override val http: HttpClient = NO_INTERNET
}
