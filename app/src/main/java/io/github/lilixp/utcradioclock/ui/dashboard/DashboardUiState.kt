package io.github.lilixp.utcradioclock.ui.dashboard

import io.github.lilixp.utcradioclock.domain.model.ThemeMode

/**
 * Everything the dashboard shows, already as text. A null value means "not known yet" and is shown
 * as a dash; sun, location and propagation are filled in by later phases.
 */
data class DashboardUiState(
    val utcDate: String,
    val utcTime: String,
    val localTime: String,
    /** Only when the local date differs from the UTC date (e.g. just after local midnight). */
    val localDate: String?,
    val timeZone: String,
    /** From Settings; null when empty. */
    val callsign: String? = null,
    val sunrise: String? = null,
    val sunset: String? = null,
    val dayLength: String? = null,
    val latitude: String? = null,
    val longitude: String? = null,
    /** Maidenhead locator from Settings; null when empty. */
    val locator: String? = null,
    val propagationAvailable: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)
