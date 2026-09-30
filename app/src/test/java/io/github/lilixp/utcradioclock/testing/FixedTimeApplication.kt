package io.github.lilixp.utcradioclock.testing

import io.github.lilixp.utcradioclock.AppContainer
import io.github.lilixp.utcradioclock.RadioClockApplication
import io.github.lilixp.utcradioclock.data.time.TimeProvider
import java.time.Clock
import java.time.ZoneOffset

/**
 * The real app for Robolectric tests, with its clock stopped at [START] in Chișinău, so screens show
 * known times instead of the PC's real time. Used with `@Config(application = FixedTimeApplication::class)`.
 */
class FixedTimeApplication : RadioClockApplication() {
    override fun createContainer() = AppContainer(this, TimeProvider(Clock.fixed(START, ZoneOffset.UTC)) { CHISINAU })
}
