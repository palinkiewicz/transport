package pl.dakil.transport.data.repo

import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import pl.dakil.transport.data.remote.MotisApi
import pl.dakil.transport.data.remote.decode
import pl.dakil.transport.data.remote.dto.TripSegmentDto
import pl.dakil.transport.domain.model.GeoPoint
import pl.dakil.transport.domain.model.VehicleSegment

/** Polyline precision requested from the API (its documented recommendation for zoom >= 11). */
private const val POLYLINE_PRECISION = 5

@Singleton
class VehiclesRepository @Inject constructor(
    private val api: MotisApi,
    private val json: Json,
) {

    /**
     * Trip segments of vehicles currently operating in the viewport. [zoom] is forwarded to
     * the API, which uses it to cull short-distance services at low zoom levels.
     *
     * [windowSeconds] is how far ahead of now the query looks. The API returns every segment
     * whose operation overlaps the window, so a wider window also keeps long inter-stop runs
     * and trips sitting near the boundary from dropping out between fetches.
     */
    suspend fun vehiclesInViewport(
        south: Double,
        west: Double,
        north: Double,
        east: Double,
        zoom: Double,
        windowSeconds: Long,
    ): Result<List<VehicleSegment>> = runCatching {
        val now = OffsetDateTime.now()
        val body = api.mapTrips(
            min = "$south,$west",
            max = "$north,$east",
            zoom = zoom,
            startTime = now.toApiTimestamp(),
            endTime = now.plusSeconds(windowSeconds).toApiTimestamp(),
            precision = POLYLINE_PRECISION,
        )
        json.decode<List<TripSegmentDto>>(body).map { it.toDomain(POLYLINE_PRECISION) }
    }

    /**
     * Segments of one specific trip, for a vehicle opened from a timetable rather than tapped on
     * the map.
     *
     * The API has no per-trip position lookup — `/map/trips` only answers bounding boxes — so the
     * trip is found by querying a small box around [between], the two stops the vehicle currently
     * sits between, and filtering the response down to [tripId]. Keep the box that tight: a box
     * spanning the whole trip would pull every vehicle along the route off a shared community API
     * just to find one of them.
     */
    suspend fun segmentsForTrip(
        tripId: String,
        between: Pair<GeoPoint, GeoPoint>,
        windowSeconds: Long,
    ): Result<List<VehicleSegment>> {
        val (a, b) = between
        return vehiclesInViewport(
            south = minOf(a.lat, b.lat) - BOX_PADDING_DEGREES,
            west = minOf(a.lon, b.lon) - BOX_PADDING_DEGREES,
            north = maxOf(a.lat, b.lat) + BOX_PADDING_DEGREES,
            east = maxOf(a.lon, b.lon) + BOX_PADDING_DEGREES,
            // A detail zoom, not the map's: the API culls short-distance services at low zoom,
            // and the trip being looked up may well be one of them.
            zoom = TRIP_LOOKUP_ZOOM,
            windowSeconds = windowSeconds,
        ).map { segments -> segments.filter { it.tripId == tripId } }
    }

    private companion object {
        const val BOX_PADDING_DEGREES = 0.01
        const val TRIP_LOOKUP_ZOOM = 15.0
    }
}
