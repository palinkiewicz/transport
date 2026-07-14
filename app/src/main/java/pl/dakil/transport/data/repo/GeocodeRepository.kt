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
) {

    suspend fun suggest(
        text: String,
        biasLat: Double? = null,
        biasLon: Double? = null,
    ): Result<List<TransitLocation>> = runCatching {
        if (text.isBlank()) return@runCatching emptyList()
        val place = if (biasLat != null && biasLon != null) "$biasLat,$biasLon" else null
        val body = api.geocode(text = text, place = place, numResults = 8)
        json.decode<List<MatchDto>>(body)
            .map { it.toTransitLocation() }
            // The geocoder can return the same stop twice (e.g. matched by name and by alias);
            // duplicates would also crash the picker's LazyColumn, which keys rows by favoriteKey.
            .distinctBy { it.favoriteKey }
    }
}
