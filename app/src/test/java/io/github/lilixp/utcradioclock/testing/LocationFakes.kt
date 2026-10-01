package io.github.lilixp.utcradioclock.testing

import io.github.lilixp.utcradioclock.data.location.LastPositionStore
import io.github.lilixp.utcradioclock.data.location.LocationProvider
import io.github.lilixp.utcradioclock.domain.model.GeoPosition
import io.github.lilixp.utcradioclock.domain.model.LocationFix
import kotlinx.coroutines.awaitCancellation
import java.time.Instant

/** Boghiceni, in KN46dw but not at its centre: a GPS position differs from the locator's. */
val BOGHICENI = GeoPosition(46.9612, 28.3041)

/** London (IO91wm): far enough from Chișinău for other sunrise and sunset times. */
val LONDON = GeoPosition(51.5074, -0.1278)

/**
 * The phone's Location Services, as the test says: permission, location on or off, the position the
 * phone already has and the one it finds (null = none, or never answers with [hangs]). Never the real GPS.
 */
class FakeLocationProvider(
    var permission: Boolean = true,
    var enabled: Boolean = true,
    var lastKnown: LocationFix? = null,
    var current: LocationFix? = null,
    /** The phone never answers (indoors): the repository's time limit must end the wait. */
    var hangs: Boolean = false,
    /** Thrown by every call, like a permission revoked while asking. */
    var failure: Exception? = null,
) : LocationProvider {
    /** How often a new position was asked for (each would turn on the GPS). */
    var requests = 0
        private set

    override fun hasPermission(): Boolean {
        failure?.let { throw it }
        return permission
    }

    override fun isLocationEnabled(): Boolean {
        failure?.let { throw it }
        return enabled
    }

    override fun lastKnown(): LocationFix? {
        failure?.let { throw it }
        return lastKnown
    }

    override suspend fun currentLocation(): LocationFix? {
        requests++
        failure?.let { throw it }
        if (hangs) awaitCancellation()
        return current
    }
}

/** The last position in memory, like position.xml. */
class FakeLastPositionStore(var saved: LocationFix? = null) : LastPositionStore {
    var cleared = 0
        private set

    override fun load(): LocationFix? = saved

    override fun save(fix: LocationFix) {
        saved = fix
    }

    override fun clear() {
        saved = null
        cleared++
    }
}

/** A position taken at [time] (by default the tests' "now", [START]), ±12 m. */
fun fix(position: GeoPosition, time: Instant = START, accuracy: Float? = 12f, approximate: Boolean = false) =
    LocationFix(position, accuracy, time, approximate)
