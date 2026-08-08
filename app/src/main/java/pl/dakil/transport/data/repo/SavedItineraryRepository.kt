package pl.dakil.transport.data.repo

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import pl.dakil.transport.data.local.SavedItineraryDao
import pl.dakil.transport.data.local.SavedItineraryEntity
import pl.dakil.transport.domain.model.Journey
import pl.dakil.transport.domain.model.SavedItinerary
import pl.dakil.transport.domain.model.TransitLocation

/**
 * The journeys the user pinned for offline use.
 *
 * Kept in Room rather than alongside the other starred things in DataStore: a journey carries
 * every leg's decoded geometry, and rewriting a single preferences blob holding several of those
 * on each star would be a lot of work for a tap.
 *
 * Saving an itinerary also files every place it touches into the place cache, so an offline trip
 * can still be searched for, drawn and starred later.
 */
@Singleton
class SavedItineraryRepository @Inject constructor(
    private val dao: SavedItineraryDao,
    private val placeCacheRepository: PlaceCacheRepository,
    private val json: Json,
) {

    val itineraries: Flow<List<SavedItinerary>> = dao.observeAll().map { rows ->
        rows.mapNotNull { it.toSavedItinerary() }
    }

    suspend fun find(id: String): SavedItinerary? = dao.findById(id)?.toSavedItinerary()

    suspend fun save(from: TransitLocation, to: TransitLocation, journey: Journey) {
        val id = SavedItinerary.idFor(from, to, journey)
        dao.upsert(
            SavedItineraryEntity(
                id = id,
                savedAt = System.currentTimeMillis(),
                fromName = from.name,
                toName = to.name,
                departureIso = journey.departureScheduledTime.toString(),
                fromJson = json.encodeToString(TransitLocation.serializer(), from),
                toJson = json.encodeToString(TransitLocation.serializer(), to),
                journeyJson = json.encodeToString(Journey.serializer(), journey),
            ),
        )
        placeCacheRepository.rememberGeocoded(journey.places(), System.currentTimeMillis())
    }

    suspend fun delete(id: String) = dao.delete(id)

    /**
     * Records a successful check against the API, keeping the itinerary's saved-at time.
     *
     * [journey] is the re-planned run when one was recognised, or null when the API answered but
     * no longer offers it. Either way the check happened, so [SavedItinerary.lastRefreshedAt]
     * moves — that timestamp answers "how current are these times", which is not the same
     * question as "did the run still exist".
     */
    suspend fun recordRefresh(existing: SavedItinerary, journey: Journey?, refreshedAt: Long) {
        val updated = journey ?: existing.journey
        dao.upsert(
            SavedItineraryEntity(
                id = existing.id,
                savedAt = existing.savedAt.toInstant().toEpochMilli(),
                fromName = existing.from.name,
                toName = existing.to.name,
                departureIso = updated.departureScheduledTime.toString(),
                fromJson = json.encodeToString(TransitLocation.serializer(), existing.from),
                toJson = json.encodeToString(TransitLocation.serializer(), existing.to),
                journeyJson = json.encodeToString(Journey.serializer(), updated),
                lastRefreshedAt = refreshedAt,
            ),
        )
    }

    /** Keys the cache must never evict: every place a saved journey needs to render offline. */
    suspend fun protectedPlaceKeys(): List<String> =
        dao.getAll().mapNotNull { it.toSavedItinerary() }
            .flatMap { saved -> saved.journey.places().map { it.favoriteKey } }
            .distinct()

    /** A row that no longer decodes is treated as absent rather than crashing the Saved tab. */
    private fun SavedItineraryEntity.toSavedItinerary(): SavedItinerary? = runCatching {
        SavedItinerary(
            id = id,
            savedAt = OffsetDateTime.ofInstant(Instant.ofEpochMilli(savedAt), ZoneId.systemDefault()),
            from = json.decodeFromString(TransitLocation.serializer(), fromJson),
            to = json.decodeFromString(TransitLocation.serializer(), toJson),
            journey = json.decodeFromString(Journey.serializer(), journeyJson),
            lastRefreshedAt = lastRefreshedAt?.let {
                OffsetDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault())
            },
        )
    }.getOrNull()
}

/** Every place a journey passes through — its endpoints, boarding points and intermediate stops. */
private fun Journey.places(): List<TransitLocation> =
    legs.flatMap { leg ->
        listOf(leg.fromPlace, leg.toPlace) + leg.intermediateStops.map { it.place }
    }.distinctBy { it.favoriteKey }
