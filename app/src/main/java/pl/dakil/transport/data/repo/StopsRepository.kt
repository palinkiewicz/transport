package pl.dakil.transport.data.repo

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import pl.dakil.transport.data.remote.MotisApi
import pl.dakil.transport.data.remote.decode
import pl.dakil.transport.data.remote.dto.PlaceDto
import pl.dakil.transport.domain.model.TransitLocation

@Singleton
class StopsRepository @Inject constructor(
    private val api: MotisApi,
    private val json: Json,
) {

    /** @param south/west/north/east bounding box of the visible map viewport, in degrees. */
    suspend fun stopsInViewport(
        south: Double,
        west: Double,
        north: Double,
        east: Double,
    ): Result<List<TransitLocation>> = runCatching {
        val body = api.mapStops(min = "$south,$west", max = "$north,$east")
        json.decode<List<PlaceDto>>(body)
            .filter { it.stopId != null }
            .map { it.toTransitLocation() }
    }
}
