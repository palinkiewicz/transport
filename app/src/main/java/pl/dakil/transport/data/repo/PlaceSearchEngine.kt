package pl.dakil.transport.data.repo

import pl.dakil.transport.data.local.foldForSearch
import pl.dakil.transport.domain.model.GeoPoint
import pl.dakil.transport.domain.model.TransitLocation

/**
 * Ranks cached places against what the user has typed.
 *
 * Deliberately small and explainable rather than clever: the geocoder is still authoritative and
 * arrives a moment later, so this only has to put a plausible answer on screen instantly and in
 * an order that does not jump around as the remote results merge in. Everything it needs is
 * already in the query and the row — no index to build, no state to keep warm.
 *
 * Only names *containing* the query survive; the score then decides the order.
 */
object PlaceSearchEngine {

    /**
     * Scores and orders [places] for [query].
     *
     * [reference] is the point the user is measuring from — the neighbouring stop on the route
     * being built, or their own position. Passing null (or leaving [distanceWeight] at zero)
     * ranks on the name alone.
     */
    fun rank(
        query: String,
        places: List<TransitLocation>,
        reference: GeoPoint? = null,
        distanceWeight: Double = 0.0,
        limit: Int = Int.MAX_VALUE,
    ): List<TransitLocation> {
        val folded = foldForSearch(query)
        if (folded.isEmpty()) return emptyList()
        return places
            .mapNotNull { place -> score(folded, place, reference, distanceWeight)?.let { place to it } }
            .sortedWith(
                compareByDescending<Pair<TransitLocation, Double>> { it.second }
                    // A stable tie-break, so two equally scored places never swap places between
                    // keystrokes and make the list appear to shuffle under the user's finger.
                    .thenBy { it.first.name }
                    .thenBy { it.first.favoriteKey },
            )
            .take(limit)
            .map { it.first }
    }

    /** Null when [place] does not contain [foldedQuery] at all. */
    private fun score(
        foldedQuery: String,
        place: TransitLocation,
        reference: GeoPoint?,
        distanceWeight: Double,
    ): Double? {
        val name = foldForSearch(place.name)
        val index = name.indexOf(foldedQuery)
        if (index < 0) return null

        var score = when {
            // "Cent" for "Centrum" — the user is almost certainly typing this name out.
            index == 0 -> MATCH_AT_START
            // "Cent" for "Warszawa Centrum" — still the name of the place, just not its first word.
            name.getOrNull(index - 1) == ' ' -> MATCH_AT_WORD_START
            else -> MATCH_ANYWHERE
        }

        // A short name containing the query is mostly the query; a long one buries it. Damped so
        // it only ever separates places that matched the same way.
        score += SHORTNESS_WEIGHT * foldedQuery.length / name.length.coerceAtLeast(1)

        // A stop can be searched from, departed from and routed through; an address cannot.
        if (place.stopId != null) score += IS_STOP

        if (reference != null && distanceWeight > 0.0) {
            val km = GeoPoint(place.lat, place.lon).distanceMetersTo(reference) / 1_000.0
            // Falls off smoothly instead of stepping, so a place just past some radius is not
            // treated as if it were on another continent.
            score += distanceWeight * (1.0 / (1.0 + km / DISTANCE_HALF_LIFE_KM))
        }

        return score
    }

    // Spaced an order of magnitude apart, so a better match class always outranks a worse one
    // however much the bonuses below contribute. Where the query matched is the strongest signal
    // there is about what the user meant; nothing else should be able to overturn it.
    private const val MATCH_AT_START = 1_000.0
    private const val MATCH_AT_WORD_START = 600.0
    private const val MATCH_ANYWHERE = 200.0

    private const val SHORTNESS_WEIGHT = 10.0
    private const val IS_STOP = 8.0

    /** Distance at which the proximity bonus is halved. */
    private const val DISTANCE_HALF_LIFE_KM = 2.0

    /**
     * Weight given to proximity when the user has asked for distance-sorted suggestions. Large
     * enough to reorder places that matched equally well, never enough to lift a mid-name match
     * above something the user typed the start of.
     */
    const val DISTANCE_WEIGHT_WHEN_SORTING = 30.0

    /** Rows pulled from the cache before ranking. Generous: the ranker decides what is best. */
    const val CANDIDATE_LIMIT = 400
}
