package pl.dakil.transport.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import pl.dakil.transport.domain.model.TransitLocation
import pl.dakil.transport.domain.model.TransportMode

/**
 * One place the app has seen — a transit stop from the map, or anything the geocoder returned.
 *
 * Map stops and geocoded matches share a table on purpose: they describe the same physical
 * places, and keeping them apart would mean a stop tapped on the map is invisible to the search
 * box (and vice versa) even though the app already knows it. [fromMap] only records how a row
 * was last confirmed, so an area refresh can drop stops that no longer exist without touching
 * places the geocoder alone knows about.
 */
@Entity(
    tableName = "cached_place",
    indices = [
        // The map's viewport query filters on both axes; latitude leads because a viewport is
        // far more selective there at the zooms stops are drawn at.
        Index(value = ["lat", "lon"]),
        Index(value = ["searchName"]),
        Index(value = ["stopId"]),
    ],
)
data class CachedPlaceEntity(
    /** [TransitLocation.favoriteKey] — the stop id, or the bare coordinate. */
    @PrimaryKey val key: String,
    val name: String,
    /** [name] run through [foldForSearch]; the column the search engine's LIKE runs against. */
    val searchName: String,
    val lat: Double,
    val lon: Double,
    val stopId: String?,
    val city: String?,
    val state: String?,
    val country: String?,
    /** [TransportMode] names, comma-joined. Unknown names are skipped when read back. */
    val modes: String,
    /**
     * The API's own significance score for this place, 0 when it did not say.
     *
     * Worth storing for two reasons: it ranks a major interchange above a request stop far
     * better than any heuristic on the name could, and every pole of one station carries the
     * *identical* value, which is what lets poles be recognised as one station.
     */
    val importance: Double,
    /** Whether the last confirmation of this row came from a map-area fetch. */
    val fromMap: Boolean,
    /** When this row was last written, in epoch millis; drives both TTL and eviction order. */
    val fetchedAt: Long,
)

/**
 * A tile whose stops have all been fetched at least once. Absence means "never fetched" — the
 * only reason to go to the network for an area.
 *
 * A tile is written only after the request covering it succeeds, so a failure simply leaves the
 * area unknown and it is retried on the next settled viewport.
 */
@Entity(tableName = "stop_tile")
data class StopTileEntity(
    /** [TileKey.id] at [TileGrid.TILE_ZOOM]. */
    @PrimaryKey val tileKey: String,
    val fetchedAt: Long,
)

/**
 * A journey the user starred, frozen as it was planned.
 *
 * The snapshot is what makes the trip openable with no connection at all; it is never treated
 * as current — opening a saved itinerary always tries to refresh it first (see
 * `SavedItineraryViewModel`).
 */
@Entity(tableName = "saved_itinerary")
data class SavedItineraryEntity(
    @PrimaryKey val id: String,
    val savedAt: Long,
    val fromName: String,
    val toName: String,
    /** Scheduled departure of the first transit leg, ISO-8601, for re-planning the same run. */
    val departureIso: String,
    /** The endpoints the journey was planned between, as serialized [TransitLocation]s. */
    val fromJson: String,
    val toJson: String,
    /** The serialized `Journey` itself. */
    val journeyJson: String,
    /**
     * When the run was last successfully checked against the API, or null if it never has been.
     *
     * Distinct from [savedAt]: it is what tells the user whether the times in front of them are
     * current or the ones frozen when they starred it, which is the whole question a saved
     * itinerary raises.
     */
    val lastRefreshedAt: Long? = null,
)

fun CachedPlaceEntity.toTransitLocation(): TransitLocation = TransitLocation(
    name = name,
    lat = lat,
    lon = lon,
    stopId = stopId,
    city = city,
    state = state,
    country = country,
    modes = modes.split(',')
        .filter { it.isNotBlank() }
        // A mode the app no longer knows is dropped rather than crashing an old cache row.
        .mapNotNull { name -> runCatching { TransportMode.valueOf(name) }.getOrNull() },
    importance = importance,
)

fun TransitLocation.toCachedPlace(fromMap: Boolean, now: Long): CachedPlaceEntity =
    CachedPlaceEntity(
        key = favoriteKey,
        name = name,
        searchName = foldForSearch(name),
        lat = lat,
        lon = lon,
        stopId = stopId,
        city = city,
        state = state,
        country = country,
        modes = modes.joinToString(",") { it.name },
        importance = importance,
        fromMap = fromMap,
        fetchedAt = now,
    )
