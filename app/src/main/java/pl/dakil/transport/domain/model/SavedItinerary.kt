package pl.dakil.transport.domain.model

import java.time.OffsetDateTime

/**
 * A journey the user starred, as it was planned.
 *
 * Unlike a [FavoriteConnection] — which saves only "from here to there" and searches afresh
 * every time — this keeps the whole plan: the legs, the lines, the times, the geometry. That is
 * what makes it openable with no connection at all.
 *
 * It is never treated as current. Opening one always tries to re-plan the same run first; the
 * snapshot is what is drawn until (and unless) that succeeds. See `SavedItineraryViewModel`.
 */
data class SavedItinerary(
    val id: String,
    val savedAt: OffsetDateTime,
    /** The endpoints the journey was planned between, kept so it can be re-planned. */
    val from: TransitLocation,
    val to: TransitLocation,
    val journey: Journey,
) {
    val fromName: String get() = from.name
    val toName: String get() = to.name

    companion object {
        /**
         * Identity of a saved journey: its endpoints, its departure, and the runs it is made of.
         *
         * Built from the trip ids rather than a random id so starring the same journey twice —
         * from a fresh search, or after a refresh revised its times — updates the one entry
         * instead of piling up near-duplicates. The scheduled departure is used, not the
         * real-time one, because a delay revision must not change what the journey *is*.
         */
        fun idFor(from: TransitLocation, to: TransitLocation, journey: Journey): String {
            val runs = journey.legs.joinToString(",") { leg -> leg.tripId ?: leg.mode.name }
            return listOf(
                from.favoriteKey,
                to.favoriteKey,
                journey.departureScheduledTime.toString(),
                runs,
            ).joinToString("|")
        }
    }
}
