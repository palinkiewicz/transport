package pl.dakil.transport.data.repo

import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import pl.dakil.transport.data.remote.MotisApi
import pl.dakil.transport.data.remote.decode
import pl.dakil.transport.data.remote.dto.PlanResponseDto
import pl.dakil.transport.domain.model.ElevationCosts
import pl.dakil.transport.domain.model.Journey
import pl.dakil.transport.domain.model.PedestrianProfile
import pl.dakil.transport.domain.model.SearchOptions
import pl.dakil.transport.domain.model.StreetMode
import pl.dakil.transport.domain.model.TransitLocation
import pl.dakil.transport.domain.model.toModeParam

private fun Float.toApiValue(unset: Float): Double? = takeIf { it != unset }?.toDouble()

private fun Int.toApiSeconds(unsetMinutes: Int): Int? = takeIf { it != unsetMinutes }?.times(60)

private fun Set<StreetMode>.toStreetModeParam(default: Set<StreetMode>): String? =
    takeIf { it != default }?.joinToString(",") { it.name }

private fun <T : Enum<T>> Set<T>.toEnumParam(): String? =
    takeIf { it.isNotEmpty() }?.joinToString(",") { it.name }

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
        options: SearchOptions = SearchOptions.DEFAULT,
        pageCursor: String? = null,
    ): Result<PlanResult> = runCatching {
        // Options at their "unset" state map to null so the server defaults apply; time-based
        // params are converted here to the units the API expects (see MOTIS OpenAPI v2.10.2:
        // transfer/travel times are minutes, street-leg times and searchWindow are seconds).
        val body = api.plan(
            fromPlace = from.queryValue,
            toPlace = to.queryValue,
            time = time?.toApiTimestamp(),
            arriveBy = options.arriveBy.takeIf { it },
            maxTransfers = options.maxTransfers,
            transitModes = options.transitCategories.toModeParam(),
            minTransferTime = options.minTransferTimeMinutes.takeIf { it > 0 },
            additionalTransferTime = options.additionalTransferTimeMinutes.takeIf { it > 0 },
            transferTimeFactor = options.transferTimeFactor.toApiValue(unset = 1.0f),
            maxTravelTime = options.maxTravelTimeMinutes,
            useRoutedTransfers = options.useRoutedTransfers.takeIf { it },
            pedestrianProfile = options.pedestrianProfile.takeIf { it != PedestrianProfile.FOOT }?.name,
            pedestrianSpeed = options.pedestrianSpeed?.toDouble(),
            cyclingSpeed = options.cyclingSpeed?.toDouble(),
            elevationCosts = options.elevationCosts.takeIf { it != ElevationCosts.NONE }?.name,
            requireBikeTransport = options.requireBikeTransport.takeIf { it },
            requireCarTransport = options.requireCarTransport.takeIf { it },
            directModes = options.direct.modes.toStreetModeParam(default = setOf(StreetMode.WALK)),
            preTransitModes = options.preTransit.modes.toStreetModeParam(default = setOf(StreetMode.WALK)),
            postTransitModes = options.postTransit.modes.toStreetModeParam(default = setOf(StreetMode.WALK)),
            maxDirectTime = options.direct.maxTimeMinutes.toApiSeconds(unsetMinutes = 30),
            maxPreTransitTime = options.preTransit.maxTimeMinutes.toApiSeconds(unsetMinutes = 15),
            maxPostTransitTime = options.postTransit.maxTimeMinutes.toApiSeconds(unsetMinutes = 15),
            directRentalFormFactors = options.direct.rentalFormFactors.toEnumParam(),
            preTransitRentalFormFactors = options.preTransit.rentalFormFactors.toEnumParam(),
            postTransitRentalFormFactors = options.postTransit.rentalFormFactors.toEnumParam(),
            directRentalPropulsionTypes = options.direct.rentalPropulsionTypes.toEnumParam(),
            preTransitRentalPropulsionTypes = options.preTransit.rentalPropulsionTypes.toEnumParam(),
            postTransitRentalPropulsionTypes = options.postTransit.rentalPropulsionTypes.toEnumParam(),
            ignoreDirectRentalReturnConstraints = options.direct.ignoreRentalReturnConstraints.takeIf { it },
            ignorePreTransitRentalReturnConstraints = options.preTransit.ignoreRentalReturnConstraints.takeIf { it },
            ignorePostTransitRentalReturnConstraints = options.postTransit.ignoreRentalReturnConstraints.takeIf { it },
            searchWindow = (options.searchWindowMinutes * 60).takeIf { options.searchWindowMinutes != 15 },
            numItineraries = options.numItineraries.takeIf { it != 5 },
            slowDirect = options.slowDirect.takeIf { it },
            fastestDirectFactor = options.fastestDirectFactor.toApiValue(unset = 1.0f),
            passengers = options.passengers,
            luggage = options.luggage?.takeIf { it > 0 },
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
