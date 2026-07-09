package pl.dakil.transport.data.repo

import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import pl.dakil.transport.data.remote.MotisApi
import pl.dakil.transport.data.remote.decode
import pl.dakil.transport.data.remote.dto.TripSegmentDto
import pl.dakil.transport.domain.model.VehicleSegment

/**
 * How far into the future the trips query looks. The API returns every segment whose
 * operation overlaps the window, so "now + a minute" captures all currently moving vehicles.
 */
private const val TIME_WINDOW_SECONDS = 60L

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
     */
    suspend fun vehiclesInViewport(
        south: Double,
        west: Double,
        north: Double,
        east: Double,
        zoom: Double,
    ): Result<List<VehicleSegment>> = runCatching {
        val now = OffsetDateTime.now()
        val body = api.mapTrips(
            min = "$south,$west",
            max = "$north,$east",
            zoom = zoom,
            startTime = now.toApiTimestamp(),
            endTime = now.plusSeconds(TIME_WINDOW_SECONDS).toApiTimestamp(),
            precision = POLYLINE_PRECISION,
        )
        json.decode<List<TripSegmentDto>>(body).map { it.toDomain(POLYLINE_PRECISION) }
    }
}
