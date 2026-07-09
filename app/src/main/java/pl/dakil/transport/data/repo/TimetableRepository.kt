package pl.dakil.transport.data.repo

import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import pl.dakil.transport.data.remote.MotisApi
import pl.dakil.transport.data.remote.decode
import pl.dakil.transport.data.remote.dto.StopTimesResponseDto
import pl.dakil.transport.domain.model.StopDepartures
import pl.dakil.transport.domain.model.TransitLocation

@Singleton
class TimetableRepository @Inject constructor(
    private val api: MotisApi,
    private val json: Json,
) {

    suspend fun departures(
        stop: TransitLocation,
        time: OffsetDateTime? = null,
        n: Int = 20,
        pageCursor: String? = null,
    ): Result<StopDepartures> = runCatching {
        val body = if (stop.stopId != null) {
            api.stoptimes(
                stopId = stop.stopId,
                time = time?.toApiTimestamp(),
                n = n,
                pageCursor = pageCursor,
            )
        } else {
            api.stoptimes(
                center = "${stop.lat},${stop.lon}",
                radius = 300,
                time = time?.toApiTimestamp(),
                n = n,
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
}
