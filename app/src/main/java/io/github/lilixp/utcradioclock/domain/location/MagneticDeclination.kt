package io.github.lilixp.utcradioclock.domain.location

import android.hardware.GeomagneticField
import io.github.lilixp.utcradioclock.domain.model.GeoPosition
import java.time.Instant

/**
 * The magnetic declination at a place: how many degrees the compass's north lies east (positive) or west
 * (negative) of true north. From Android's own World Magnetic Model ([GeomagneticField]), on the phone,
 * without any data from the Internet. Useful to point an antenna with a compass.
 */
object MagneticDeclination {

    /** Degrees, east positive, at [position], [altitudeMeters] (sea level when unknown) and [time]. */
    fun at(position: GeoPosition, altitudeMeters: Double?, time: Instant): Double =
        GeomagneticField(
            position.latitude.toFloat(),
            position.longitude.toFloat(),
            (altitudeMeters ?: 0.0).toFloat(),
            time.toEpochMilli(),
        ).declination.toDouble()
}
