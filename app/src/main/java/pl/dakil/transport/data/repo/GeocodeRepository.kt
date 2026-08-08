package pl.dakil.transport.data.repo

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import pl.dakil.transport.data.remote.MotisApi
import pl.dakil.transport.data.remote.decode
import pl.dakil.transport.data.remote.dto.MatchDto
import pl.dakil.transport.domain.model.TransitLocation

@Singleton
class GeocodeRepository @Inject constructor(
    private val api: MotisApi,
    private val json: Json,
    private val placeCacheRepository: PlaceCacheRepository,
) {

    /**
     * Geocoder suggestions for [text]. With [stopsOnly] the API is asked for `STOP` matches
     * alone, so every suggestion carries a stop id — required wherever coordinates are not
     * accepted (intermediate stops, departure boards).
     *
     * [biasStrength] is how hard the server is asked to pull its results toward the bias
     * coordinate. This is the only thing that can keep a generic query local: sorting the answer
     * afterwards can only reorder the handful of places the server already picked, so without a
     * bias "Park" comes back from the other side of the planet and stays there. The cost of
     * overdoing it is the mirror image — a distant place searched by its exact name gets buried
     * under local near-misses — which is why the strength is the user's to set.
     *
     * Results are also written to the place cache, which is what lets the same query answer
     * instantly next time — and answer at all with no connection.
     */
    suspend fun suggest(
        text: String,
        biasLat: Double? = null,
        biasLon: Double? = null,
        stopsOnly: Boolean = false,
        biasStrength: Double = 1.0,
    ): Result<List<TransitLocation>> = runCatching {
        if (text.isBlank()) return@runCatching emptyList()
        val place = if (biasLat != null && biasLon != null) "$biasLat,$biasLon" else null
        val body = api.geocode(
            text = text,
            place = place,
            // Meaningless without a coordinate to bias toward, so it travels with one or not at all.
            placeBias = biasStrength.takeIf { place != null },
            numResults = 8,
            type = if (stopsOnly) "STOP" else null,
        )
        val results = json.decode<List<MatchDto>>(body)
            .map { it.toTransitLocation() }
            // The geocoder can return the same stop twice (e.g. matched by name and by alias);
            // duplicates would also crash the picker's LazyColumn, which keys rows by favoriteKey.
            .distinctBy { it.favoriteKey }
        placeCacheRepository.rememberGeocoded(results, System.currentTimeMillis())
        results
    }

    /**
     * Nearest named place to [lat]/[lon], used to label a point the user picked directly on
     * the map. Transit stops are skipped: a point is a bare coordinate by definition, and
     * borrowing a nearby stop's name would read as if the stop itself had been selected.
     * Null when nothing is close enough for the name to describe the picked spot.
     */
    suspend fun nearestPlaceName(lat: Double, lon: Double): Result<String?> = runCatching {
        val body = api.reverseGeocode(place = "$lat,$lon")
        json.decode<List<MatchDto>>(body)
            .filter { it.type != "STOP" }
            .firstOrNull { distanceMeters(lat, lon, it.lat, it.lon) <= MAX_LABEL_DISTANCE_METERS }
            ?.name
    }

    private companion object {
        /** Beyond this, a reverse-geocoded name describes something else than the picked spot. */
        const val MAX_LABEL_DISTANCE_METERS = 60.0
    }
}

/** Equirectangular approximation — plenty at the few-tens-of-metres scale used here. */
private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val meanLat = Math.toRadians((lat1 + lat2) / 2)
    val dx = Math.toRadians(lon2 - lon1) * kotlin.math.cos(meanLat)
    val dy = Math.toRadians(lat2 - lat1)
    return kotlin.math.sqrt(dx * dx + dy * dy) * 6_371_000.0
}
