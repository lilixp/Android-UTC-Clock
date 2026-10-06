package io.github.lilixp.utcradioclock.data.location

import android.content.Context
import androidx.core.content.edit
import io.github.lilixp.utcradioclock.domain.model.GeoPosition
import io.github.lilixp.utcradioclock.domain.model.LocationFix
import java.time.Instant
import kotlin.math.roundToLong

/** The last GPS position, so the app has one at start, indoors or without signal. */
interface LastPositionStore {
    fun load(): LocationFix?
    fun save(fix: LocationFix)
    fun clear()
}

/**
 * The last GPS position in its own SharedPreferences file, "position.xml". It stays on the phone: the
 * file is left out of backups (res/xml), is never sent anywhere, and is deleted when the user goes back
 * to the manual locator. The coordinates are rounded to 0.001° (about 100 m): enough for the locator and
 * the Sun, no more precise than needed.
 */
class SharedPreferencesLastPositionStore(context: Context) : LastPositionStore {

    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun load(): LocationFix? {
        if (!preferences.contains(KEY_LATITUDE) || !preferences.contains(KEY_LONGITUDE)) return null
        val position = GeoPosition(
            latitude = preferences.getFloat(KEY_LATITUDE, Float.NaN).toDouble().rounded(),
            longitude = preferences.getFloat(KEY_LONGITUDE, Float.NaN).toDouble().rounded(),
        )
        if (!position.isValid) return null
        return LocationFix(
            position = position,
            accuracyMeters = preferences.getFloat(KEY_ACCURACY, -1f).takeIf { it >= 0f },
            time = Instant.ofEpochMilli(preferences.getLong(KEY_TIME, 0L)),
            approximate = preferences.getBoolean(KEY_APPROXIMATE, false),
            altitudeMeters = preferences.getFloat(KEY_ALTITUDE, Float.NaN).takeIf { it.isFinite() }?.toDouble(),
        )
    }

    override fun save(fix: LocationFix) = preferences.edit {
        putFloat(KEY_LATITUDE, fix.position.latitude.rounded().toFloat())
        putFloat(KEY_LONGITUDE, fix.position.longitude.rounded().toFloat())
        if (fix.accuracyMeters != null) putFloat(KEY_ACCURACY, fix.accuracyMeters) else remove(KEY_ACCURACY)
        putLong(KEY_TIME, fix.time.toEpochMilli())
        putBoolean(KEY_APPROXIMATE, fix.approximate)
        if (fix.altitudeMeters != null) putFloat(KEY_ALTITUDE, fix.altitudeMeters.toFloat()) else remove(KEY_ALTITUDE)
    }

    override fun clear() = preferences.edit { clear() }

    /** To 0.001°; a Float keeps that exactly enough (7 significant digits). */
    private fun Double.rounded(): Double = if (isFinite()) (this * 1000).roundToLong() / 1000.0 else this

    private companion object {
        const val FILE_NAME = "position" // not in the backup rules (res/xml): stays on this phone
        const val KEY_LATITUDE = "latitude"
        const val KEY_LONGITUDE = "longitude"
        const val KEY_ACCURACY = "accuracy_m"
        const val KEY_TIME = "time_ms"
        const val KEY_APPROXIMATE = "approximate"
        const val KEY_ALTITUDE = "altitude_m"
    }
}
