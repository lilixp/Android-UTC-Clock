package io.github.lilixp.utcradioclock.domain.location

import io.github.lilixp.utcradioclock.domain.model.GeoPosition
import io.github.lilixp.utcradioclock.domain.model.StationIdentity

/**
 * Maidenhead locator → the centre of its square. A locator is pairs of characters, each pair cutting
 * the square of the previous one (longitude first, then latitude):
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
 *
 * The app accepts **4, 6 or 8 characters** ([VALID_LENGTHS]), as Settings says. A field alone
 * (2 characters, e.g. "KN") is not accepted: its centre can be hundreds of kilometres from the station.
 *
 * [fromPosition] goes the other way (e.g. from GPS), with the same table.
 */
object Maidenhead {

    private class Pair(val first: Char, val count: Int)

    private val pairs = listOf(Pair('A', 18), Pair('0', 10), Pair('A', 24), Pair('0', 10))

    /**
     * The centre of the locator's square, or null if the locator is empty or not valid: not 4, 6 or 8
     * characters, or a character out of range for its place. The one check for "is this a locator".
     */
    fun toPosition(locator: String): GeoPosition? {
        val text = locator.trim().uppercase()
        if (text.length !in VALID_LENGTHS) return null
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

    /** A locator the app can use (see [toPosition]). */
    fun isValid(locator: String): Boolean = toPosition(locator) != null

    /**
     * The locator of the square that contains [position], with [characters] characters (4, 6 or 8),
     * written as the app writes locators: KN46dw. Null for a position that is not on Earth (out of
     * range, NaN). The north pole and the 180° meridian belong to the last square, as in other programs.
     */
    fun fromPosition(position: GeoPosition, characters: Int = DEFAULT_CHARACTERS): String? {
        require(characters in VALID_LENGTHS) { "4, 6 or 8 characters" }
        if (!position.isValid) return null
        // Fractions of the whole world, kept just below 1 so 180° and 90° stay in the last square
        var x = ((position.longitude + 180.0) / 360.0).coerceAtMost(ALMOST_ONE)
        var y = ((position.latitude + 90.0) / 180.0).coerceAtMost(ALMOST_ONE)
        val text = StringBuilder()
        for (i in 0 until characters / 2) {
            val pair = pairs[i]
            val column = (x * pair.count).toInt().coerceIn(0, pair.count - 1)
            val row = (y * pair.count).toInt().coerceIn(0, pair.count - 1)
            text.append(pair.first + column).append(pair.first + row)
            x = x * pair.count - column
            y = y * pair.count - row
        }
        return StationIdentity.normalizeLocator(text.toString())
    }

    /** Six characters (KN46dw): a square of about 6 × 4.6 km here, like the locators people enter. */
    const val DEFAULT_CHARACTERS = 6

    /** The lengths the app accepts, as Settings says: KN46, KN46dw, KN46dw12. */
    val VALID_LENGTHS = setOf(4, 6, 8)

    private const val ALMOST_ONE = 1.0 - 1e-12
}
