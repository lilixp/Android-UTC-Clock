package io.github.lilixp.utcradioclock.data.location

import io.github.lilixp.utcradioclock.data.settings.SettingsRepository
import io.github.lilixp.utcradioclock.domain.location.Maidenhead
import io.github.lilixp.utcradioclock.domain.model.GeoPosition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Where the station is. For now: the centre of the Maidenhead locator entered in Settings, or null
 * when there is no valid locator. A later phase can give the GPS position here instead; everything
 * that uses the position (the Sun, the screens) stays the same.
 */
class PositionRepository(private val settings: SettingsRepository) {

    fun current(): GeoPosition? = Maidenhead.toPosition(settings.station.value.locator)

    /** Emits only when the position itself changes, not when the callsign does. */
    val position: Flow<GeoPosition?> =
        settings.station.map { Maidenhead.toPosition(it.locator) }.distinctUntilChanged()
}
