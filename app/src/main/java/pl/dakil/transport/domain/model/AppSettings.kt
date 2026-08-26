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
    val frameIntervalMillis: Int = 3_000,

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
 * Which of the two bundled basemap styles the map draws with.
 *
 * Deliberately its own setting rather than a read of the app theme: the map is a picture of the
 * world rather than a surface of the UI, and wanting a dark app at night is not the same wish as
 * wanting a dark map in daylight. [SYSTEM] is what makes the two agree by default.
 */
@Serializable
enum class MapTheme(@param:StringRes val labelRes: Int) {
    SYSTEM(R.string.map_theme_system),
    LIGHT(R.string.map_theme_light),
    DARK(R.string.map_theme_dark),
    ;

    /** Resolved against the device's current setting, which only [SYSTEM] consults. */
    fun isDark(systemInDarkTheme: Boolean): Boolean = when (this) {
        SYSTEM -> systemInDarkTheme
        LIGHT -> false
        DARK -> true
    }
}

/**
 * The palette the app itself paints in.
 *
 * [DYNAMIC] is the platform's wallpaper palette, which only exists from Android 12 — the picker
 * hides it below that, and [colorSchemeFor] falls back to [TRANSPORT] for anyone whose stored
 * choice arrives on a phone that cannot honour it.
 *
 * Deliberately unrelated to [MapTheme]: this colours the app's own surfaces, that one picks a
 * basemap.
 */
@Serializable
enum class AppColorTheme(@param:StringRes val labelRes: Int) {
    /** The app's own identity, and the default: a vivid violet with a teal third accent. */
    TRANSPORT(R.string.theme_transport),

    /** The wallpaper palette, where the platform offers one. Android 12 and up. */
    DYNAMIC(R.string.theme_dynamic),
    OCEAN(R.string.theme_ocean),
    LAVENDER(R.string.theme_lavender),
    SUNSET(R.string.theme_sunset),
    ROSE(R.string.theme_rose),
    TEAL(R.string.theme_teal),
}

/**
 * Whether the app follows the system dark setting or overrides it.
 *
 * Separate from [AppColorTheme] because the two are independent: every colour theme has a light
 * and a dark form, and someone who wants a dark app on a light system should not have to give up
 * their colour to get it.
 */
@Serializable
enum class DarkThemeOption(@param:StringRes val labelRes: Int) {
    SYSTEM(R.string.dark_theme_system),
    LIGHT(R.string.dark_theme_light),
    DARK(R.string.dark_theme_dark),
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

/**
 * The file formats an itinerary can leave the app as. Picked per export from the share button's
 * menu rather than stored in settings: which one is right depends on where the file is going, not
 * on a standing preference.
 *
 * [extension] and [mimeType] are machine-facing and deliberately not resources; only [labelRes] is
 * app text. Constant names are not persisted anywhere, but they do reach the outside world through
 * the file's own name, so they are the format's usual spelling.
 */
enum class ExportFormat(
    @param:StringRes val labelRes: Int,
    val extension: String,
    val mimeType: String,
) {
    /** Google Earth / My Maps and most GIS tools; a zip holding one `doc.kml`. */
    KMZ(R.string.export_format_kmz, "kmz", "application/vnd.google-earth.kmz"),

    /** The interchange format of watches, hiking apps and desktop mapping tools. */
    GPX(R.string.export_format_gpx, "gpx", "application/gpx+xml"),

    /** RFC 7946, for anything that speaks web mapping. */
    GEOJSON(R.string.export_format_geojson, "geojson", "application/geo+json"),
}

/** How the itinerary export hands the finished file over. */
@Serializable
enum class ExportDelivery(@param:StringRes val labelRes: Int) {
    /** Straight to the system share sheet, from a file in the app's cache. */
    SHARE(R.string.export_delivery_share),

    /** The system document picker, so the file lands wherever the user chooses. */
    SAVE(R.string.export_delivery_save),

    /** Offer both each time. */
    ASK(R.string.export_delivery_ask),
}

/** How the exported file is named. The extension comes from the chosen [ExportFormat]. */
@Serializable
enum class ExportFileName(@param:StringRes val labelRes: Int) {
    /** `warszawa-centralna-lotnisko-chopina-2026-08-06.gpx` */
    ROUTE_AND_DATE(R.string.export_filename_route_date),

    /** `itinerary-20260806-0812.kmz` */
    DATE_TIME(R.string.export_filename_date_time),

    /** `itinerary.geojson` */
    PLAIN(R.string.export_filename_plain),
}

/**
 * What an exported itinerary contains and how it leaves the app — the same set of choices for
 * every [ExportFormat], since all three carry the same points, paths and text.
 *
 * The readers disagree about what they want: some choke on a file with thousands of track points,
 * others ignore waypoints entirely. So the shape of the file is a user decision rather than one
 * baked in.
 */
@Serializable
data class ExportSettings(
    val delivery: ExportDelivery = ExportDelivery.SHARE,

    val fileName: ExportFileName = ExportFileName.ROUTE_AND_DATE,

    /** One path per leg, drawn from the leg's decoded geometry. Off, the file is waypoints only. */
    val includeTracks: Boolean = true,

    /** Whether the walk/bike/car legs at either end contribute waypoints and tracks too. */
    val includeAccessLegs: Boolean = true,

    /** A waypoint for every stop passed through, not only the ones boarded and alighted at. */
    val includeIntermediateStops: Boolean = false,

    /** Timestamps on the waypoints. */
    val includeTimes: Boolean = true,

    /** Real-time times where the feed revises them; off, always the published schedule. */
    val useRealTimes: Boolean = true,

    /** A description carrying the line, headsign, platform and operator. */
    val includeDescriptions: Boolean = true,
) {
    val isDefault: Boolean get() = this == DEFAULT

    companion object {
        val DEFAULT = ExportSettings()
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

    /**
     * The palette the app paints itself in.
     *
     * Defaults to the app's own [AppColorTheme.TRANSPORT] rather than the wallpaper palette.
     * Dynamic colour used to be unconditional on Android 12 and up, with no way to turn it off;
     * it is now one entry in the picker, so an existing install moves off it on upgrade.
     */
    val colorTheme: AppColorTheme = AppColorTheme.TRANSPORT,

    /** Whether the app follows the system dark setting or overrides it. */
    val darkTheme: DarkThemeOption = DarkThemeOption.SYSTEM,

    /** True black backgrounds in dark mode, for OLED screens. Nothing at all in light mode. */
    val pureBlack: Boolean = false,

    /** Which of the bundled basemap styles every map in the app draws with. */
    val mapTheme: MapTheme = MapTheme.SYSTEM,

    /** Which tab the app opens on. */
    val defaultTab: DefaultTab = DefaultTab.MAP,

    /** Which times the connection result cards lead with. */
    val connectionTimesMode: ConnectionTimesMode = ConnectionTimesMode.BOTH,

    /** Whether the search forms' locations survive a restart. */
    val rememberLastSearch: Boolean = true,

    /** Whether the map reopens where it was last left. */
    val rememberMapCamera: Boolean = true,

    /**
     * Whether location suggestions are re-sorted by raw distance from the neighbouring point on
     * the route being built — the previous stop, or the next one when the route has nothing
     * before it, falling back to the current position.
     *
     * Off by default, and a niche option rather than a refinement: it discards how well anything
     * matched, so the nearest stop containing the query wins even when the user typed another
     * one's full name. Proximity is already part of the ranking — `PlaceSearchEngine` applies the
     * geocoder's own distance ladder at [searchBiasStrength] — so this is only for someone who
     * genuinely wants a nearest-first list.
     *
     * The `@SerialName` is deliberately not the property name: it was once on by default, and
     * changing the default alone would have left every existing install with the old behaviour
     * stored. Reading it under a new key retires those stored values exactly once. Renaming it
     * again would reset everyone a second time.
     */
    @SerialName("sortSuggestionsByDistanceV2")
    val sortSuggestionsByDistance: Boolean = false,

    /**
     * How many recently picked places the location picker remembers and offers back, newest
     * first — one history shared by every field, see [RecentPlaces].
     *
     * [RECENT_PLACES_OFF] turns the whole feature off: nothing is listed, nothing is pinned, and
     * the next pick clears what was already stored rather than leaving a forgotten history on
     * disk.
     */
    val recentPlacesLimit: Int = 10,

    /**
     * Whether a recently used place is held at the top of the results when it matches what is
     * being typed, ahead of whatever the ranking would otherwise have put there.
     *
     * On by default: a place picked before is a far stronger signal about what the user means
     * than a name match is. It is a separate switch from [keepFirstCachedResult] because it
     * changes *which* row leads, not merely whether the leading row stays put.
     */
    val pinRecentPlaces: Boolean = true,

    /**
     * Whether the best cached result stays pinned to the top of the picker once it appears.
     *
     * The cache answers on the keystroke and the geocoder ~300 ms later. Ranking both through
     * `PlaceSearchEngine` already keeps the list from reshuffling, but the remote answer can
     * still insert a better match above the row the user is reaching for. Pinning trades that
     * one position for a list whose top row never moves under a finger.
     */
    val keepFirstCachedResult: Boolean = true,

    /**
     * How hard the geocoder is asked to pull its results toward the point a search is measured
     * from. [SEARCH_BIAS_NONE] leaves the server's own worldwide ranking alone; higher values
     * keep a generic query ("Park") in the user's own city, at the cost of burying a far-away
     * place searched by its exact name.
     *
     * Sent to the API as `placeBias` *and* applied locally by `PlaceSearchEngine` to the cached
     * half of the list, so both halves agree on how much proximity is worth.
     */
    val searchBiasStrength: Int = 4,

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

    /**
     * What an exported itinerary contains. The stored key predates KMZ and GeoJSON and is kept as
     * it was: renaming it would silently reset the choices of everyone who had tuned the export.
     */
    @SerialName("gpxExport")
    val export: ExportSettings = ExportSettings(),

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

    /**
     * Whether everything the Appearance screen edits still holds its default, for that section's
     * reset button — the app palette, the basemap colourway and the line-colour pair alike.
     */
    val appearanceIsDefault: Boolean
        get() = colorTheme == DEFAULT.colorTheme &&
            darkTheme == DEFAULT.darkTheme &&
            pureBlack == DEFAULT.pureBlack &&
            mapTheme == DEFAULT.mapTheme &&
            lineColorMode == DEFAULT.lineColorMode &&
            customLineColors == DEFAULT.customLineColors

    companion object {
        val DEFAULT = AppSettings()

        /** [searchBiasStrength] range; 1 is the geocoder's own default, i.e. no added pull. */
        const val SEARCH_BIAS_NONE = 1
        const val SEARCH_BIAS_MAX = 10

        /** [recentPlacesLimit] range; zero remembers nothing at all. */
        const val RECENT_PLACES_OFF = 0
        const val RECENT_PLACES_MAX = 25
    }
}
