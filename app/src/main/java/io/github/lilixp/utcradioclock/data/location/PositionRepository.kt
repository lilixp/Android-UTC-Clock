package io.github.lilixp.utcradioclock.data.location

import io.github.lilixp.utcradioclock.data.settings.SettingsRepository
import io.github.lilixp.utcradioclock.data.time.TimeProvider
import io.github.lilixp.utcradioclock.domain.location.Maidenhead
import io.github.lilixp.utcradioclock.domain.model.GeoPosition
import io.github.lilixp.utcradioclock.domain.model.GpsStatus
import io.github.lilixp.utcradioclock.domain.model.LocationFix
import io.github.lilixp.utcradioclock.domain.model.PositionOrigin
import io.github.lilixp.utcradioclock.domain.model.PositionSource
import io.github.lilixp.utcradioclock.domain.model.StationIdentity
import io.github.lilixp.utcradioclock.domain.model.StationPosition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration

/**
 * Where the station is. In [PositionSource.MANUAL] mode (the default): the centre of the Maidenhead
 * locator entered in Settings. In [PositionSource.AUTOMATIC] mode: the phone's position, or the manual
 * locator while there is none (no permission, location off, no signal). Everything that uses the
 * position (the Sun, day and night for the bands, the LOCATION card) reads it from here.
 *
 * The phone is asked for a position only while [updates] is collected (the dashboard on screen): when
 * it appears, every [REFRESH_INTERVAL] after that, and when [refreshNow] is called (e.g. back from the
 * permission dialog). No tracking, nothing in the background, and the position never leaves the phone.
 */
class PositionRepository(
    private val settings: SettingsRepository,
    private val locations: LocationProvider,
    private val store: LastPositionStore,
    private val time: TimeProvider,
) {
    /** What the phone's location gave last; [fix] is the newest position, possibly older than now. */
    private data class Gps(val status: GpsStatus, val fix: LocationFix? = null)

    private val gps = MutableStateFlow(startingGps())

    /** Asks for a new position at once; each call counts up, so none is lost while one is running. */
    private val requests = MutableStateFlow(0)

    /** The position and where it came from. Only reads what is known: it never turns the GPS on. */
    val state: Flow<StationPosition> =
        combine(settings.station, settings.positionSource, gps, ::resolve).distinctUntilChanged()

    /** Emits only when the position itself changes, not when the callsign does. */
    val position: Flow<GeoPosition?> = state.map { it.position }.distinctUntilChanged()

    fun current(): GeoPosition? = currentState().position

    fun currentState(): StationPosition =
        resolve(settings.station.value, settings.positionSource.value, gps.value)

    /** [state], and while it is collected, the phone is asked for its position in automatic mode. */
    fun updates(): Flow<StationPosition> = channelFlow {
        launch {
            settings.positionSource.collectLatest { source ->
                if (source == PositionSource.AUTOMATIC) {
                    track()
                } else {
                    // Back to the locator: the saved GPS position is no longer needed, so it is deleted
                    store.clear()
                    gps.value = Gps(GpsStatus.SEARCHING)
                }
            }
        }
        state.collect { send(it) }
    }

    /** After the permission dialog or coming back to the app: look again now, not at the next interval. */
    fun refreshNow() {
        requests.value++
    }

    private suspend fun track() {
        while (true) {
            val seen = requests.value
            locate()
            withTimeoutOrNull(REFRESH_INTERVAL.toMillis()) { requests.first { it != seen } }
        }
    }

    /** One look at the phone's location, the cheapest way that gives a recent position. */
    private suspend fun locate() {
        if (!ask(false) { locations.hasPermission() }) {
            gps.value = Gps(GpsStatus.NO_PERMISSION) // a position saved earlier is not used without permission
            return
        }
        val known = gps.value.fix ?: store.load()
        if (known != null && known.isRecent()) {
            gps.value = Gps(GpsStatus.OK, known)
            return
        }
        if (!ask(false) { locations.isLocationEnabled() }) {
            gps.value = Gps(GpsStatus.LOCATION_OFF, known) // the last position is still the best there is
            return
        }
        // Another app may have asked recently: then nothing needs to be turned on at all
        ask(null) { locations.lastKnown() }?.takeIf { it.position.isValid && it.isRecent() }?.let {
            accept(it)
            return
        }
        gps.value = Gps(GpsStatus.SEARCHING, known)
        val fix = withTimeoutOrNull(LOCATE_TIMEOUT.toMillis()) { ask(null) { locations.currentLocation() } }
        if (fix != null && fix.position.isValid) accept(fix) else gps.value = Gps(GpsStatus.UNAVAILABLE, known)
    }

    private fun accept(fix: LocationFix) {
        store.save(fix)
        gps.value = Gps(GpsStatus.OK, fix)
    }

    private fun LocationFix.isRecent(): Boolean {
        val age = Duration.between(time, now())
        return !age.isNegative && age < RECENT
    }

    private fun now() = time.now()

    /** Location Services can fail in many ways (permission revoked meanwhile, provider gone): [fallback]. */
    private inline fun <T> ask(fallback: T, block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        fallback
    }

    private fun startingGps(): Gps =
        if (ask(false) { locations.hasPermission() }) {
            Gps(GpsStatus.SEARCHING, store.load())
        } else {
            Gps(GpsStatus.NO_PERMISSION)
        }

    private fun resolve(station: StationIdentity, source: PositionSource, gps: Gps): StationPosition {
        val automatic = source == PositionSource.AUTOMATIC
        val fix = gps.fix?.takeIf { automatic && gps.status != GpsStatus.NO_PERMISSION }
        val status = gps.status.takeIf { automatic }
        if (fix != null) {
            return StationPosition(
                position = fix.position,
                origin = PositionOrigin.GPS,
                locator = Maidenhead.fromPosition(fix.position),
                source = source,
                gps = status,
                fix = fix,
            )
        }
        val manual = Maidenhead.toPosition(station.locator)
        return StationPosition(
            position = manual,
            origin = if (manual != null) PositionOrigin.LOCATOR else PositionOrigin.NONE,
            locator = station.locator.ifEmpty { null },
            source = source,
            gps = status,
        )
    }

    companion object {
        /** A station stays put: a new position every half hour is plenty. */
        val REFRESH_INTERVAL: Duration = Duration.ofMinutes(30)

        /** A position younger than this is used as it is, without asking the phone again. */
        val RECENT: Duration = Duration.ofMinutes(10)

        /** How long to wait for the phone to find a position. */
        val LOCATE_TIMEOUT: Duration = Duration.ofSeconds(30)
    }
}
