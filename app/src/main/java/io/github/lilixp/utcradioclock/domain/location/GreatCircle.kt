package io.github.lilixp.utcradioclock.domain.location

import io.github.lilixp.utcradioclock.domain.model.GeoPosition
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Distance and direction between two places on the Earth taken as a sphere (radius 6371 km), as radio
 * amateurs use them: the short path along the great circle and the azimut to set at the start.
 */
object GreatCircle {

    private const val EARTH_RADIUS_KM = 6371.0

    /** Kilometres along the great circle (haversine formula, accurate also for short distances). */
    fun distanceKm(from: GeoPosition, to: GeoPosition): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_KM * asin(min(1.0, sqrt(h)))
    }

    /** The initial azimut from [from] towards [to], 0–360°, clockwise from true (geographic) north. */
    fun bearingDegrees(from: GeoPosition, to: GeoPosition): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }
}
