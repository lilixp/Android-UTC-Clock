package io.github.lilixp.utcradioclock.ui.dashboard

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.lilixp.utcradioclock.R

/**
 * The four sections of the app, in the order of the bottom bar: by how much a radio amateur needs them
 * (Lilian's order) — the clock, propagation, the Sun, the location. [CLOCK] is the start screen; the Back
 * button returns to it from the other three. Only the section changes: the data behind all four comes
 * from the one [DashboardViewModel].
 */
enum class AppTab(@StringRes val titleRes: Int, @DrawableRes val iconRes: Int, val testTag: String) {
    CLOCK(R.string.tab_clock, R.drawable.ic_clock, "tab_clock"),
    PROPAGATION(R.string.tab_propagation, R.drawable.ic_antenna, "tab_propagation"),
    SUN(R.string.tab_sun, R.drawable.ic_sun, "tab_sun"),
    LOCATION(R.string.tab_location, R.drawable.ic_location, "tab_location"),
}
