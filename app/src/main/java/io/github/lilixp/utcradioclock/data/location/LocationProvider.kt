package io.github.lilixp.utcradioclock.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import io.github.lilixp.utcradioclock.domain.model.GeoPosition
import io.github.lilixp.utcradioclock.domain.model.LocationFix
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.Instant
import kotlin.coroutines.resume

/**
 * The phone's Location Services, asked only when the app needs a position. Tests use a fake, so they
 * never touch the real GPS.
 */
interface LocationProvider {
    /** Approximate or precise location is allowed. */
    fun hasPermission(): Boolean

    /** Precise location is allowed (not only approximate). */
    fun hasPrecisePermission(): Boolean

    /** Location is turned on on the phone. */
    fun isLocationEnabled(): Boolean

    /** The newest position the phone already has (from any app), without turning anything on. */
    fun lastKnown(): LocationFix?

    /** One new position; null when the phone cannot give one. The caller sets the time limit. */
    suspend fun currentLocation(): LocationFix?
}

/**
 * [LocationProvider] on Android's own LocationManager (no Google Play Services needed): one position at
 * a time, never continuous tracking, never in the background. Nothing is sent anywhere.
 */
class AndroidLocationProvider(private val context: Context) : LocationProvider {

    private val manager: LocationManager? = context.getSystemService(LocationManager::class.java)

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private val precise get() = granted(Manifest.permission.ACCESS_FINE_LOCATION)

    override fun hasPermission(): Boolean = precise || granted(Manifest.permission.ACCESS_COARSE_LOCATION)

    override fun hasPrecisePermission(): Boolean = precise

    override fun isLocationEnabled(): Boolean = manager?.let(LocationManagerCompat::isLocationEnabled) == true

    @SuppressLint("MissingPermission") // checked first; a revoked permission throws and gives null
    override fun lastKnown(): LocationFix? {
        val manager = manager ?: return null
        if (!hasPermission()) return null
        return manager.getProviders(true)
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.toFix()
    }

    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(): LocationFix? {
        val manager = manager ?: return null
        if (!hasPermission()) return null
        val provider = provider(manager) ?: return null
        return suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            try {
                LocationManagerCompat.getCurrentLocation(manager, provider, signal, ContextCompat.getMainExecutor(context)) {
                    if (continuation.isActive) continuation.resume(it?.toFix())
                }
            } catch (_: SecurityException) {
                continuation.resume(null) // permission revoked in the meantime
            } catch (_: IllegalArgumentException) {
                continuation.resume(null) // provider gone in the meantime
            }
        }
    }

    /**
     * The system's fused provider (Android 12+), else the network (quick, enough for a locator), else
     * the GPS. Before Android 12 the GPS needs precise location.
     */
    private fun provider(manager: LocationManager): String? {
        val enabled = manager.getProviders(true)
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && LocationManager.FUSED_PROVIDER in enabled ->
                LocationManager.FUSED_PROVIDER
            LocationManager.NETWORK_PROVIDER in enabled -> LocationManager.NETWORK_PROVIDER
            LocationManager.GPS_PROVIDER in enabled &&
                (precise || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) -> LocationManager.GPS_PROVIDER
            else -> null
        }
    }

    private fun Location.toFix() = LocationFix(
        position = GeoPosition(latitude, longitude),
        accuracyMeters = if (hasAccuracy()) accuracy else null,
        time = Instant.ofEpochMilli(time),
        approximate = !precise,
        altitudeMeters = altitude(),
    )

    /** Above sea level when the phone knows it (Android 14+), otherwise the GPS height; null without any. */
    private fun Location.altitude(): Double? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && hasMslAltitude() -> mslAltitudeMeters
        hasAltitude() -> altitude
        else -> null
    }
}
