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
     * One station and the platforms that were folded into it.
     *
     * [members] is kept so a caller can tell which sources contributed — the picker uses it to
     * keep the geocoder's own ordering for the stations it returned, whichever of their platforms
     * happened to be cached.
     */
    data class Station(val place: TransitLocation, val members: List<TransitLocation>)

    /**
     * Scores and orders [places] for [query], one row per station rather than per platform.
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
        return groupIntoStations(places.filter { foldForSearch(it.name).contains(folded) })
            .map { it.place }
            .map { station -> station to score(folded, station, reference, distanceWeight) }
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

    /**
     * Collapses platforms into the stations they belong to.
     *
     * `/v6/map/stops` returns every pole separately — seven rows called "Centrum" for one
     * interchange — while the geocoder answers with the single grouped station. Left alone, the
     * picker shows the same place over and over and the user has to guess which row is the one
     * they want. So the poles are put back together here.
     *
     * Two stops are the same station when they share a name *and* are close enough to be one
     * place. `importance` corroborates it where the API supplied it: it is a station-level score,
     * identical across every pole, so two same-named stops that disagree on it are two different
     * stations however close they happen to sit.
     *
     * The row that represents a station is the member that can say the most about it: a
     * geocoded one (which alone carries the city, and whose id is the station rather than one of
     * its platforms) ahead of a bare map pole. Its modes are the union of the whole station's, so
     * a tram-and-metro interchange reads as one.
     */
    fun groupIntoStations(places: List<TransitLocation>): List<Station> {
        // The very same place can arrive twice — the geocoder and the cache both know it — and a
        // caller merging two sources hands both over. Deduped by identity before anything else,
        // because the callers key list rows by it and a repeat is a crash, not a cosmetic glitch.
        // Earlier entries win, so a caller can order its richest source first.
        val deduped = places.distinctBy { it.favoriteKey }
        // Only stops have platforms to merge. An address that happens to share a stop's name is a
        // different answer to the query, not another way in to the same one.
        val (stops, other) = deduped.partition { it.stopId != null }
        val clusters = mutableListOf<List<TransitLocation>>()
        // Grouped by folded name first, so the distance test only ever runs within one name.
        for ((_, sameName) in stops.groupBy { foldForSearch(it.name) }) {
            val forName = mutableListOf<MutableList<TransitLocation>>()
            for (place in sameName) {
                val existing = forName.firstOrNull { cluster ->
                    cluster.any { it.belongsToSameStationAs(place) }
                }
                if (existing != null) existing.add(place) else forName.add(mutableListOf(place))
            }
            clusters += forName
        }
        clusters += other.map { listOf(it) }
        return clusters.map { Station(place = it.toStation(), members = it) }
    }

    /**
     * Whether two same-named stops are platforms of one station: near enough to be one place,
     * and not contradicted by the API's own station-level [TransitLocation.importance].
     */
    private fun TransitLocation.belongsToSameStationAs(other: TransitLocation): Boolean {
        val bothScored = importance > 0.0 && other.importance > 0.0
        if (bothScored && importance != other.importance) return false
        return GeoPoint(lat, lon).distanceMetersTo(GeoPoint(other.lat, other.lon)) <= STATION_RADIUS_METERS
    }

    /** The one row a cluster is shown as; see [groupIntoStations]. */
    private fun List<TransitLocation>.toStation(): TransitLocation {
        val representative = maxWithOrNull(
            compareBy<TransitLocation> { if (it.areaLabel != null) 1 else 0 }
                .thenBy { it.importance }
                // Last resort, so the same cluster always elects the same member.
                .thenBy { it.favoriteKey },
        ) ?: first()
        return representative.copy(
            modes = flatMap { it.modes }.distinct(),
            // Any member that has been geocoded knows where the station is; the rest of the
            // cluster is the same physical place, so the label describes them too.
            city = representative.city ?: firstNotNullOfOrNull { it.city },
            state = representative.state ?: firstNotNullOfOrNull { it.state },
            country = representative.country ?: firstNotNullOfOrNull { it.country },
            importance = maxOf { it.importance },
        )
    }

    /** Callers filter to names containing [foldedQuery] first, so a match is assumed here. */
    private fun score(
        foldedQuery: String,
        place: TransitLocation,
        reference: GeoPoint?,
        distanceWeight: Double,
    ): Double {
        val name = foldForSearch(place.name)
        val index = name.indexOf(foldedQuery).coerceAtLeast(0)

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

        // The API's own view of how significant the place is, which separates a city's main
        // interchange from a request stop that happens to share its name. Scores are tiny
        // fractions, so this is normalised rather than added raw.
        score += IMPORTANCE_WEIGHT * (place.importance / (place.importance + IMPORTANCE_MIDPOINT))

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
    private const val IMPORTANCE_WEIGHT = 20.0

    /** Importance at which the bonus is half its maximum; roughly a well-served city stop. */
    private const val IMPORTANCE_MIDPOINT = 0.002

    /**
     * How far apart two same-named stops may sit and still be one station. Generous, because a
     * big interchange spreads its platforms over a couple of streets — and two genuinely
     * distinct stops that share a name this closely would be indistinguishable to the user
     * anyway.
     */
    private const val STATION_RADIUS_METERS = 400.0

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
