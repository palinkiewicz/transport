package pl.dakil.transport.domain.model

import kotlinx.serialization.Serializable

/**
 * How the map turns the API's schedule data into moving vehicle markers.
 *
 * None of this is GPS: MOTIS serves stop-to-stop *segments* (a polyline plus a departure and an
 * arrival time), so a vehicle's position between two stops is always interpolated from its
 * timetable — corrected by real-time delays where a feed provides them. These settings control
 * how that interpolation is fetched, advanced and drawn.
 */
@Serializable
data class VehicleMotionSettings(
    /** Seconds between viewport fetches of vehicle segments. */
    val refreshIntervalSeconds: Int = 30,

    /**
     * How far ahead of "now" each fetch asks for segments. The API returns every segment
     * overlapping the window, so a wider window keeps long inter-stop runs and trips near the
     * window boundary from dropping out between fetches.
     */
    val fetchWindowSeconds: Int = 180,

    /**
     * How long a trip missing from the latest fetch keeps being drawn from its last known
     * segments. Without this a trip that drops out of a single poll vanishes and pops back.
     * `0` reverts to replacing the whole set on every fetch.
     */
    val segmentRetentionSeconds: Int = 120,

    /**
     * Milliseconds between redraws. Lower is smoother and costs more CPU; no extra requests.
     * The UI offers [FRAME_INTERVAL_STEPS] rather than a linear range: the useful settings are
     * bunched under a second, while the power-saving end is only worth coarse steps.
     */
    val frameIntervalMillis: Int = 50,

    /**
     * Never let a vehicle slide backwards along its route. Real-time feeds revise delays
     * continuously, and a revision that lands mid-segment would otherwise rewind the marker
     * hundreds of metres; with this on, the vehicle stalls until its schedule catches up.
     */
    val monotonicProgress: Boolean = true,

    /**
     * Fraction of the remaining distance a marker covers each frame, easing it toward the
     * position the timetable implies instead of snapping there. `1` disables easing.
     */
    val smoothingFactor: Float = 0.18f,

    /**
     * Beyond this distance a correction is applied instantly rather than eased — easing a
     * kilometre-scale jump would draw a vehicle gliding across the map through open country.
     */
    val smoothingSnapThresholdMeters: Int = 500,

    /** Vehicles are not fetched below this zoom level. */
    val minZoom: Float = 9.0f,
) {
    val isDefault: Boolean get() = this == DEFAULT

    companion object {
        val DEFAULT = VehicleMotionSettings()

        /**
         * Selectable values for [frameIntervalMillis]: fine-grained where smoothness is decided
         * (tens of milliseconds), coarse where the point is only to stop redrawing often.
         */
        val FRAME_INTERVAL_STEPS = listOf(
            50, 100, 200, 300, 400, 500, 600, 700, 800, 900,
            1_000, 1_500, 2_000, 2_500, 3_000, 4_000, 5_000,
            10_000, 15_000, 20_000, 25_000, 30_000,
        )
    }
}

/**
 * Everything the Settings screen tunes, persisted as one JSON blob. New fields must have
 * defaults so previously stored values keep decoding.
 */
@Serializable
data class AppSettings(
    val vehicleMotion: VehicleMotionSettings = VehicleMotionSettings(),

    /**
     * Whether the results list, departures board and trip view reload themselves while open.
     * Off, they load once and offer a manual refresh instead.
     */
    val autoRefreshEnabled: Boolean = true,

    /** Seconds between automatic reloads of the results list and the departures board. */
    val resultsRefreshSeconds: Int = 30,

    /** Stop markers are not fetched or drawn below this zoom level. */
    val stopsMinZoom: Float = 13.0f,
) {
    val isDefault: Boolean get() = this == DEFAULT

    companion object {
        val DEFAULT = AppSettings()
    }
}
