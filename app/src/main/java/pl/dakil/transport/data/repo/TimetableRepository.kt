package pl.dakil.transport.data.repo

import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import pl.dakil.transport.data.remote.MotisApi
import pl.dakil.transport.data.remote.decode
import pl.dakil.transport.data.remote.dto.ItineraryDto
import pl.dakil.transport.data.remote.dto.StopTimesResponseDto
import pl.dakil.transport.domain.model.Journey
import pl.dakil.transport.domain.model.SearchOptions
import pl.dakil.transport.domain.model.StopDepartures
import pl.dakil.transport.domain.model.TransitLocation
import pl.dakil.transport.domain.model.toModeParam

@Singleton
class TimetableRepository @Inject constructor(
    private val api: MotisApi,
    private val json: Json,
) {

    suspend fun departures(
        stop: TransitLocation,
        time: OffsetDateTime? = null,
        options: SearchOptions = SearchOptions.DEFAULT,
        pageCursor: String? = null,
    ): Result<StopDepartures> = runCatching {
        val body = if (stop.stopId != null) {
            api.stoptimes(
                stopId = stop.stopId,
                time = time?.toApiTimestamp(),
                arriveBy = options.arriveBy.takeIf { it },
                mode = options.departuresCategories.toModeParam(),
                n = options.departuresCount,
                pageCursor = pageCursor,
            )
        } else {
            api.stoptimes(
                center = "${stop.lat},${stop.lon}",
                radius = options.departuresRadiusMeters,
                time = time?.toApiTimestamp(),
                arriveBy = options.arriveBy.takeIf { it },
                mode = options.departuresCategories.toModeParam(),
                n = options.departuresCount,
                pageCursor = pageCursor,
            )
        }
        val response = json.decode<StopTimesResponseDto>(body)
        StopDepartures(
            stopName = response.place.name,
            stopId = response.place.stopId,
            departures = response.stopTimes.map { it.toDomain() },
            nextPageCursor = response.nextPageCursor,
            previousPageCursor = response.previousPageCursor,
        )
    }

    /** Fetches a single trip's full run (all stops, with real-time data) as a [Journey]. */
    suspend fun trip(tripId: String): Result<Journey> = runCatching {
        val body = api.trip(tripId = tripId, detailedLegs = false)
        json.decode<ItineraryDto>(body).toDomain()
    }
}
