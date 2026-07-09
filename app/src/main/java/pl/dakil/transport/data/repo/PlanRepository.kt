package pl.dakil.transport.data.repo

import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import pl.dakil.transport.data.remote.MotisApi
import pl.dakil.transport.data.remote.decode
import pl.dakil.transport.data.remote.dto.PlanResponseDto
import pl.dakil.transport.domain.model.Journey
import pl.dakil.transport.domain.model.TransitLocation

data class PlanResult(
    val fromName: String,
    val toName: String,
    val journeys: List<Journey>,
    val previousPageCursor: String? = null,
    val nextPageCursor: String? = null,
)

@Singleton
class PlanRepository @Inject constructor(
    private val api: MotisApi,
    private val json: Json,
) {

    suspend fun plan(
        from: TransitLocation,
        to: TransitLocation,
        time: OffsetDateTime? = null,
        maxTransfers: Int? = null,
        pageCursor: String? = null,
    ): Result<PlanResult> = runCatching {
        val body = api.plan(
            fromPlace = from.queryValue,
            toPlace = to.queryValue,
            time = time?.toApiTimestamp(),
            maxTransfers = maxTransfers,
            pageCursor = pageCursor,
        )
        val response = json.decode<PlanResponseDto>(body)
        val direct = response.direct.map { it.toDomain() }
        val transit = response.itineraries.map { it.toDomain() }
        PlanResult(
            fromName = response.from.name,
            toName = response.to.name,
            journeys = (transit + direct).sortedBy { it.startTime },
            previousPageCursor = response.previousPageCursor,
            nextPageCursor = response.nextPageCursor,
        )
    }
}
