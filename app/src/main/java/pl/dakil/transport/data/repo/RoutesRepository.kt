package pl.dakil.transport.data.repo

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import pl.dakil.transport.data.remote.MotisApi
import pl.dakil.transport.data.remote.decode
import pl.dakil.transport.data.remote.dto.ItineraryDto
import pl.dakil.transport.data.remote.dto.StopTimeDto
import pl.dakil.transport.data.remote.dto.StopTimesResponseDto
import pl.dakil.transport.domain.model.GeoPoint
import pl.dakil.transport.domain.model.RouteShape
import pl.dakil.transport.domain.model.TransitLocation
import pl.dakil.transport.domain.model.TransportMode
import pl.dakil.transport.domain.model.TripDetails

/** How many upcoming departures to sample when discovering which lines serve a stop. */
private const val DEPARTURES_SAMPLE_SIZE = 48

/** Upper bound on trip fetches per request — Transitous is a shared community API. */
private const val MAX_ROUTES = 12
private const val TRIP_FETCH_PARALLELISM = 4

@Singleton
class RoutesRepository @Inject constructor(
    private val api: MotisApi,
    private val json: Json,
) {

    /**
     * Geometry of the lines serving [stop], discovered from its upcoming departures: one
     * representative trip is fetched per distinct line (so only one direction/branch of each
     * line is drawn). Lines whose trip fetch fails are dropped rather than failing the whole
     * request; the [Result] itself only fails when the departures lookup does.
     */
    suspend fun routesThroughStop(stop: TransitLocation): Result<List<RouteShape>> = runCatching {
        val body = if (stop.stopId != null) {
            api.stoptimes(stopId = stop.stopId, n = DEPARTURES_SAMPLE_SIZE)
        } else {
            api.stoptimes(center = "${stop.lat},${stop.lon}", radius = 300, n = DEPARTURES_SAMPLE_SIZE)
        }
        val sampledTrips = json.decode<StopTimesResponseDto>(body).stopTimes
            .filter { it.tripId != null }
            .distinctBy { it.mode to it.lineLabel() }
            .take(MAX_ROUTES)

        coroutineScope {
            val semaphore = Semaphore(TRIP_FETCH_PARALLELISM)
            sampledTrips.map { stopTime ->
                async {
                    semaphore.withPermit {
                        runCatching { tripShape(stopTime) }.getOrNull()
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    /**
     * Geometry plus amenity/accessibility attributes of one vehicle run, for the map's
     * vehicle info panel. Attributes come from the trip's first transit leg.
     */
    suspend fun tripDetails(tripId: String, lineLabel: String): Result<TripDetails> = runCatching {
        val body = api.trip(tripId = tripId)
        val itinerary = json.decode<ItineraryDto>(body)
        val leg = itinerary.legs.firstOrNull { it.tripShortName != null || it.routeShortName != null || it.headsign != null }
            ?: itinerary.legs.firstOrNull()
        val segments = itinerary.shapeSegments()
        TripDetails(
            headsign = leg?.headsign,
            agencyName = leg?.agencyName,
            routeLongName = leg?.routeLongName,
            wheelchairAccessible = when (leg?.wheelchairAccessible) {
                "ACCESSIBLE" -> true
                "NOT_ACCESSIBLE" -> false
                else -> null
            },
            bikesAllowed = leg?.bikesAllowed,
            shape = if (segments.isEmpty()) {
                null
            } else {
                RouteShape(
                    lineLabel = lineLabel,
                    headsign = leg?.headsign,
                    mode = TransportMode.fromApiValue(leg?.mode),
                    routeColor = leg?.routeColor?.takeIf { it.isNotBlank() },
                    segments = segments,
                )
            },
        )
    }

    private suspend fun tripShape(stopTime: StopTimeDto): RouteShape? {
        val body = api.trip(tripId = requireNotNull(stopTime.tripId))
        val segments = json.decode<ItineraryDto>(body).shapeSegments()
        if (segments.isEmpty()) return null
        return RouteShape(
            lineLabel = stopTime.lineLabel(),
            headsign = stopTime.headsign,
            mode = TransportMode.fromApiValue(stopTime.mode),
            routeColor = stopTime.routeColor?.takeIf { it.isNotBlank() },
            segments = segments,
        )
    }

    private fun ItineraryDto.shapeSegments(): List<List<GeoPoint>> = legs
        .mapNotNull { leg ->
            leg.legGeometry
                ?.let { decodePolyline(it.points, it.precision ?: DEFAULT_POLYLINE_PRECISION) }
                ?.takeIf { it.size >= 2 }
        }

    private fun StopTimeDto.lineLabel(): String =
        routeShortName ?: displayName ?: tripShortName ?: headsign ?: mode

    companion object {
        /** The v6 endpoints encode with precision 6; used only if the response omits the field. */
        private const val DEFAULT_POLYLINE_PRECISION = 6
    }
}
