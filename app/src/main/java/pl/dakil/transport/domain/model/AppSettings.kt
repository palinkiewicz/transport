package pl.dakil.transport.domain.model

import androidx.annotation.StringRes
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pl.dakil.transport.R

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
 * How long the app keeps what it has fetched, and how much of it.
 *
 * The governing rule behind every field: an expired cache is still *shown*. Expiry only decides
 * when the app bothers to ask the API again — never whether the user gets to see a stop. With no
 * connection at all, a three-week-old cache is the difference between a working map and a blank
 * one, so throwing it away on a timer would defeat the point of having it.
 */
@Serializable
data class OfflineCacheSettings(
    /** Days before a fetched map area is asked for again. Its stops keep showing meanwhile. */
    val stopCacheTtlDays: Int = 7,

    /** Days before a cached search result is re-checked against the geocoder. */
    val searchCacheTtlDays: Int = 7,

    /**
     * Cap on cached places, oldest evicted first. Starred places and the stops of saved
     * itineraries are never evicted, however low this is set.
     */
    val maxCachedPlaces: Int = 50_000,

    /**
     * Whether stops from an area whose cache has expired are drawn while it refreshes. Off,
     * they are hidden until the refresh lands — for people who would rather see nothing than
     * something possibly out of date.
     */
    val showExpiredCache: Boolean = true,

    /** Whether typing searches the local cache at all, or waits for the geocoder every time. */
    val offlineSearchEnabled: Boolean = true,
) {
    val isDefault: Boolean get() = this == DEFAULT

    val stopCacheTtlMillis: Long get() = stopCacheTtlDays * MILLIS_PER_DAY

    val searchCacheTtlMillis: Long get() = searchCacheTtlDays * MILLIS_PER_DAY

    companion object {
        val DEFAULT = OfflineCacheSettings()

        const val MIN_TTL_DAYS = 1
        const val MAX_TTL_DAYS = 30

        /**
         * Selectable caps for [maxCachedPlaces]. Non-linear because the interesting decision is
         * at the small end (a phone kept tight on space) while the large end only needs to say
         * "effectively everything I will ever look at".
         */
        val MAX_PLACES_STEPS = listOf(
            5_000, 10_000, 25_000, 50_000, 100_000, 200_000, 500_000,
        )

        private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
    }
}

/** Which bottom-bar tab the app opens on. */
@Serializable
enum class DefaultTab(@param:StringRes val labelRes: Int) {
    MAP(R.string.tab_map),
    CONNECTIONS(R.string.tab_connections),
    DEPARTURES(R.string.tab_departures),

    /**
     * The Saved tab. Kept serializing as `FAVOURITES`, its name before the tab was renamed:
     * only enum *names* are persisted, so a bare rename would quietly reset the default-tab
     * choice of everyone who had picked this one.
     */
    @SerialName("FAVOURITES")
    SAVED(R.string.tab_saved),
}

/**
 * Which times a connection result card shows. Stop times are when the vehicle itself departs
 * and arrives; door-to-door times include the walk at each end, so they answer "when do I
 * leave home" rather than "when does the bus go".
 */
@Serializable
enum class ConnectionTimesMode(@param:StringRes val labelRes: Int) {
    STOP_TIMES(R.string.connection_times_stops),
    DOOR_TO_DOOR(R.string.connection_times_door_to_door),
    BOTH(R.string.connection_times_both),
    ;

    /** Whether the walk at each end is part of the headline times (and the countdown). */
    val includesDoorToDoor: Boolean get() = this != STOP_TIMES
}

/**
 * Where a line's colour comes from on the list screens. The map always uses the server's colours:
 * markers and route overlays have no "next line" order to hand a palette out along.
 */
@Serializable
enum class LineColorMode(@param:StringRes val labelRes: Int) {
    /** The colours the operator publishes, exactly as the API sends them. */
    TRANSITOUS(R.string.line_colors_transitous),

    /** Only [AppSettings.palette], handed out in the order lines first appear on a screen. */
    CUSTOM(R.string.line_colors_custom),

    /** Operator colours, except where two neighbouring lines would look the same. */
    AUTO(R.string.line_colors_auto),
}

/** The user-definable colour set [LineColorMode.CUSTOM] and [LineColorMode.AUTO] draw from. */
object LinePalette {
    const val SIZE = 6

    /**
     * Material-expressive mid-tones, pairwise far enough apart that the [LineColorMode.AUTO]
     * similarity test never has to reject one of its own entries. Every entry is also offered by
     * the settings picker, so the swatch being edited shows up as the selected one.
     */
    val DEFAULT = listOf("6750A4", "00658F", "006D3B", "9A4B00", "B3261E", "984061")
}

/** How the itinerary export hands the finished file over. */
@Serializable
enum class GpxDelivery(@param:StringRes val labelRes: Int) {
    /** Straight to the system share sheet, from a file in the app's cache. */
    SHARE(R.string.gpx_delivery_share),

    /** The system document picker, so the file lands wherever the user chooses. */
    SAVE(R.string.gpx_delivery_save),

    /** Offer both each time. */
    ASK(R.string.gpx_delivery_ask),
}

/** How the exported file is named. */
@Serializable
enum class GpxFileName(@param:StringRes val labelRes: Int) {
    /** `warszawa-centralna-lotnisko-chopina-2026-08-06.gpx` */
    ROUTE_AND_DATE(R.string.gpx_filename_route_date),

    /** `itinerary-20260806-0812.gpx` */
    DATE_TIME(R.string.gpx_filename_date_time),

    /** `itinerary.gpx` */
    PLAIN(R.string.gpx_filename_plain),
}

/**
 * What an itinerary exported as GPX contains and how it leaves the app.
 *
 * GPX is read by everything from hiking watches to desktop mapping tools, and they disagree about
 * what they want: some choke on a file with thousands of track points, others ignore waypoints
 * entirely. So the shape of the file is a user decision rather than one baked in.
 */
@Serializable
data class GpxExportSettings(
    val delivery: GpxDelivery = GpxDelivery.SHARE,

    val fileName: GpxFileName = GpxFileName.ROUTE_AND_DATE,

    /** One `<trk>` per leg, drawn from the leg's decoded geometry. Off, the file is waypoints only. */
    val includeTracks: Boolean = true,

    /** Whether the walk/bike/car legs at either end contribute waypoints and tracks too. */
    val includeAccessLegs: Boolean = true,

    /** A `<wpt>` for every stop passed through, not only the ones boarded and alighted at. */
    val includeIntermediateStops: Boolean = false,

    /** `<time>` stamps on the waypoints. */
    val includeTimes: Boolean = true,

    /** Real-time times where the feed revises them; off, always the published schedule. */
    val useRealTimes: Boolean = true,

    /** `<desc>` carrying the line, headsign, platform and operator. */
    val includeDescriptions: Boolean = true,
) {
    val isDefault: Boolean get() = this == DEFAULT

    companion object {
        val DEFAULT = GpxExportSettings()
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

    /** Which tab the app opens on. */
    val defaultTab: DefaultTab = DefaultTab.MAP,

    /** Which times the connection result cards lead with. */
    val connectionTimesMode: ConnectionTimesMode = ConnectionTimesMode.BOTH,

    /** Whether the search forms' locations survive a restart. */
    val rememberLastSearch: Boolean = true,

    /** Whether the map reopens where it was last left. */
    val rememberMapCamera: Boolean = true,

    /**
     * Whether location suggestions are re-ranked by distance from the neighbouring point on the
     * route being built — the previous stop, or the next one when the route has nothing before
     * it, falling back to the current position — instead of keeping the server's own ranking.
     */
    val sortSuggestionsByDistance: Boolean = true,

    /** Whether the itinerary map labels the stops where you board and alight. */
    val showItineraryStopNames: Boolean = true,

    /**
     * Whether selecting a vehicle narrows the map to that run alone: only its stops, and no
     * other vehicles. Off, the map keeps showing everything in the viewport around it.
     */
    val focusSelectedVehicle: Boolean = true,

    /**
     * Whether "Begin here"/"Finish here" keep the user on the map while the other end of the
     * route is still missing, instead of jumping to the Connections form after the first pick.
     */
    val stayOnMapWhenPickingRoute: Boolean = true,

    /** Where the list screens take their line colours from. */
    val lineColorMode: LineColorMode = LineColorMode.TRANSITOUS,

    /** The user's own colour set, as GTFS-shaped `RRGGBB` hex. Read it through [palette]. */
    val customLineColors: List<String> = LinePalette.DEFAULT,

    /** What an itinerary exported as GPX contains. */
    val gpxExport: GpxExportSettings = GpxExportSettings(),

    /** How long fetched stops and places are kept, and how many of them. */
    val offlineCache: OfflineCacheSettings = OfflineCacheSettings(),
) {
    val isDefault: Boolean get() = this == DEFAULT

    /**
     * [customLineColors] normalised to exactly [LinePalette.SIZE] usable entries. A stored blob
     * predating a palette resize (or hand-edited) would otherwise index out of bounds — unlike a
     * bad enum name, a wrong-length list is not something `coerceInputValues` repairs.
     */
    val palette: List<String>
        get() = List(LinePalette.SIZE) { index ->
            customLineColors.getOrNull(index)?.takeIf { it.isNotBlank() } ?: LinePalette.DEFAULT[index]
        }

    /** Whether both line-colour fields still hold their defaults, for the group's reset button. */
    val lineColorsAreDefault: Boolean
        get() = lineColorMode == DEFAULT.lineColorMode && customLineColors == DEFAULT.customLineColors

    companion object {
        val DEFAULT = AppSettings()
    }
}
