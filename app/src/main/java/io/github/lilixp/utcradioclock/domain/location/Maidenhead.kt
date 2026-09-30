package io.github.lilixp.utcradioclock.domain.location

import io.github.lilixp.utcradioclock.domain.model.GeoPosition

/**
 * Maidenhead locator → the centre of its square. A locator is 1 to 4 pairs of characters, each pair
 * cutting the square of the previous one (longitude first, then latitude):
 *
 * | pair | characters | longitude × latitude of the square |
 * |------|------------|-------------------------------------|
 * | 1    | A–R        | 20° × 10° (field)                   |
 * | 2    | 0–9        | 2° × 1° (square)                    |
 * | 3    | A–X        | 5′ × 2.5′ (subsquare)               |
 * | 4    | 0–9        | 30″ × 15″ (extended square)         |
 *
 * E.g. KN46dw → 46.9375° N, 28.2917° E. The centre is at most half a square away from the station:
 * about 3 km with 6 characters, enough for sunrise and sunset to the minute.
 */
object Maidenhead {

    private class Pair(val first: Char, val count: Int)

    private val pairs = listOf(Pair('A', 18), Pair('0', 10), Pair('A', 24), Pair('0', 10))

    /** The centre of the locator's square, or null if the locator is empty or not a valid locator. */
    fun toPosition(locator: String): GeoPosition? {
        val text = locator.trim().uppercase()
        if (text.isEmpty() || text.length % 2 != 0 || text.length > 2 * pairs.size) return null
        var longitude = -180.0
        var latitude = -90.0
        var width = 360.0
        var height = 180.0
        for (i in 0 until text.length / 2) {
            val pair = pairs[i]
            width /= pair.count
            height /= pair.count
            val x = text[2 * i] - pair.first
            val y = text[2 * i + 1] - pair.first
            if (x !in 0 until pair.count || y !in 0 until pair.count) return null
            longitude += x * width
            latitude += y * height
        }
        return GeoPosition(latitude = latitude + height / 2, longitude = longitude + width / 2)
    }
}
