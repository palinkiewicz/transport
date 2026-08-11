package pl.dakil.transport.data.repo

import kotlin.math.ln
import kotlin.math.max
import pl.dakil.transport.data.local.foldForSearch
import pl.dakil.transport.domain.model.GeoPoint
import pl.dakil.transport.domain.model.TransitLocation

/**
 * Ranks places against what the user has typed, reproducing the geocoder's own ordering.
 *
 * Both halves of the picker's list go through here — the rows the cache had on the keystroke and
 * the rows the geocoder answers with 300 ms later. That is the point: if a place's position
 * depended on which source it arrived from, the remote answer would reshuffle the list under the
 * user's finger and turn a tap into a mis-tap. Scored the same way, a remote answer can only
 * *insert* rows.
 *
 * ### Where the numbers come from
 *
 * They are fitted to the live Transitous geocoder, not chosen by taste. `/v1/geocode` returns a
 * `score` per match, and it decomposes cleanly:
 *
 * ```
 * score = baseMatchScore(query, place) + placeBias × distanceLadder(metres)
 * ```
 *
 * The ladder ([distanceLadder]) is exact — subtracting it leaves a residual that is identical
 * across every `placeBias` value, for every result of a 974-result probe. The base score is a
 * least-squares fit over the same probe, reproducing the server's ordering on held-out queries
 * with a Spearman correlation of 0.66 and the same top result 70% of the time — against 0.42
 * and 44% for the match-class scale this replaced.
 *
 * The one signal deliberately left on the table is the geocoder's ADDRESS/PLACE distinction
 * (worth about 0.04 more): [TransitLocation] keeps a stop id or a bare coordinate, so it cannot
 * tell a street address from a POI without a new field on the model, the cache column and the
 * persisted favourites blob.
 *
 * **Lower is better**, following the API: the score is a cost, and [rank] sorts ascending.
 *
 * Re-fitting means re-probing the API — see the plan in `.claude/plans/` for the method.
 */
object PlaceSearchEngine {

    /**
     * One station and the platforms that were folded into it.
     *
     * [members] is kept so a caller can tell which sources contributed — the picker uses it to
     * find the cached row it pinned, which is usually absorbed into a station the geocoder's own
     * copy ends up representing.
     */
    data class Station(val place: TransitLocation, val members: List<TransitLocation>)

    /**
     * Scores and orders [places] for [query], one row per station rather than per platform.
     *
     * [bias] is the point results are pulled toward and [biasStrength] how hard — the very same
     * pair the geocoder is asked for, so the cached half of the list and the remote half agree on
     * what "near" means. Places that do not match [query] well enough are dropped entirely; see
     * [score].
     */
    fun rank(
        query: String,
        places: List<TransitLocation>,
        bias: GeoPoint? = null,
        biasStrength: Int = 0,
        limit: Int = Int.MAX_VALUE,
    ): List<TransitLocation> {
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return emptyList()
        return groupIntoStations(places, bias)
            .mapNotNull { station ->
                scoreTokenized(tokens, station.place, bias, biasStrength)
                    ?.let { station.place to it }
            }
            .sortedWith(
                compareBy<Pair<TransitLocation, Double>> { it.second }
                    // A stable tie-break, so two equally scored places never swap places between
                    // keystrokes and make the list appear to shuffle under the user's finger.
                    .thenBy { it.first.name }
                    .thenBy { it.first.favoriteKey },
            )
            .take(limit)
            .map { it.first }
    }

    /**
     * What [place] would cost for [query]: lower sorts first, null means it is not an answer at
     * all. Exposed so a caller merging two sources can order every row through the one function.
     */
    fun score(
        query: String,
        place: TransitLocation,
        bias: GeoPoint? = null,
        biasStrength: Int = 0,
    ): Double? = scoreTokenized(tokenize(query), place, bias, biasStrength)

    /**
     * The geocoder's answers and the cache's, as one list with each station appearing once.
     *
     * The two sources describe the same places differently: the geocoder returns the grouped
     * station, carrying its city and district, while the cache holds the individual platforms the
     * map fetched, which carry neither. Concatenating them shows the same stop several times over
     * — once from the geocoder and once per cached pole — so they are clustered together instead
     * and each station is offered as the member that knows the most about it.
     *
     * Every station is then ordered by [score], whichever source it came from. That is what stops
     * the list reshuffling: the cache answers on the keystroke and the geocoder ~300 ms later, so
     * an order taken from the geocoder's result index — as the picker used to do — replaces the
     * whole list the moment it arrives and moves the row under the user's finger. Scored the same
     * way, a remote answer can only insert rows around the ones already drawn.
     *
     * [pinnedKey] is a place held at the top regardless of its score, matched against the
     * stations' *members* because the row the picker pinned is usually a cached pole that ends up
     * absorbed into a station the geocoder's own copy stands for. See
     * `AppSettings.keepFirstCachedResult`.
     */
    fun merge(
        query: String,
        remote: List<TransitLocation>,
        cached: List<TransitLocation>,
        bias: GeoPoint? = null,
        biasStrength: Int = 0,
        pinnedKey: String? = null,
    ): List<TransitLocation> {
        // Remote first, so where both sources hold the very same place the geocoder's copy — the
        // one carrying the city and district — is the one kept.
        val ordered = groupIntoStations(remote + cached, bias)
            .map { station ->
                // A null score means the local matcher would not have offered this place. It is
                // still shown, last: the geocoder answered with it, and it knows things about
                // spelling and synonyms that matching a stored name cannot.
                station to (score(query, station.place, bias, biasStrength) ?: Double.MAX_VALUE)
            }
            .sortedWith(
                compareBy<Pair<Station, Double>> { it.second }
                    // The same stable tie-break [rank] uses, so equally scored rows never swap
                    // between keystrokes.
                    .thenBy { it.first.place.name }
                    .thenBy { it.first.place.favoriteKey },
            )
            .map { it.first }
        val pinnedIndex = pinnedKey?.let { key ->
            ordered.indexOfFirst { station -> station.members.any { it.favoriteKey == key } }
        } ?: -1
        if (pinnedIndex <= 0) return ordered.map { it.place }
        val reordered = ordered.toMutableList()
        reordered.add(0, reordered.removeAt(pinnedIndex))
        return reordered.map { it.place }
    }

    /**
     * The word to look [query]'s candidates up by, or null when there is nothing to search for.
     *
     * The longest word, because it is the most selective one the cache can be narrowed with — a
     * `LIKE '%…%'` cannot use an index, so which word is chosen decides how much of the table is
     * scanned and how much noise the ranker then has to throw away. It is also the word a result
     * can least afford to miss: [MIN_QUERY_COVERAGE] requires half the query's characters to
     * match, and for the one- and two-word queries place names actually are, missing the longest
     * word almost always fails that.
     */
    fun candidateToken(query: String): String? = tokenize(query).maxByOrNull { it.length }

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
     * a tram-and-metro interchange reads as one. [bias] breaks a tie between members that are
     * equally informative, so collapsing two indistinguishable rows keeps the one nearer the user
     * rather than an arbitrary one.
     */
    fun groupIntoStations(places: List<TransitLocation>, bias: GeoPoint? = null): List<Station> {
        // The very same place can arrive twice — the geocoder and the cache both know it — and a
        // caller merging two sources hands both over. Deduped by identity before anything else,
        // because the callers key list rows by it and a repeat is a crash, not a cosmetic glitch.
        // Earlier entries win, so a caller can order its richest source first.
        val deduped = places.distinctBy { it.favoriteKey }
        // Stops and everything else are grouped apart. An address that happens to share a stop's
        // name is a different answer to the query, not another way in to the same one — and the
        // list draws them with different icons, so they do not read as a repeat.
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
        // Addresses and POIs have no platforms to merge, but they can still arrive as several
        // rows that read exactly alike — the geocoder really does answer "Centrum" three times
        // for Warszawa, and "Rynek Dębnicki" twice for Kraków three metres apart. Two rows
        // carrying the same name and the same area are one choice as far as the user can see, so
        // they are offered once. Proximity deliberately does not enter into it: the two "Psi
        // Park" in Warszawa sit 12 km apart and are genuinely different places, but nothing on
        // screen tells them apart, so offering both is asking the user to guess.
        clusters += other
            .groupBy { AreaKey(foldForSearch(it.name), it.city, it.state, it.country) }
            .values
        return clusters.map { Station(place = it.toStation(bias), members = it) }
    }

    /** What makes two non-stop rows indistinguishable in the list; see [groupIntoStations]. */
    private data class AreaKey(
        val name: String,
        val city: String?,
        val state: String?,
        val country: String?,
    )

    /**
     * The words a name or query is matched by: folded, then split on anything that is not a
     * letter or digit, so `"Buk, Rynek"` and `"N-Park"` yield the words they read as.
     *
     * Deliberately *not* [foldForSearch]'s own job: that function defines the stored
     * `cached_place.searchName` column, and changing it would need a migration recomputing every
     * row. Splitting further here costs nothing and is free to change.
     */
    private fun tokenize(text: String): List<String> =
        foldForSearch(text).split(NON_WORD).filter { it.isNotEmpty() }

    /**
     * Whether two same-named stops should be offered as one row.
     *
     * Two ways in, because neither alone is enough:
     *
     * - **They read alike.** Same name and same area means the two rows are typographically
     *   identical — "Poznań, AWF · Poznań, województwo wielkopolskie, PL" twice over. Whether
     *   they are really one station stops mattering at that point: the user cannot tell them
     *   apart, so offering both only asks them to guess. This ignores both the radius and the
     *   importance check below, which is the point — the pair that prompted it disagreed on one
     *   of them.
     * - **They are platforms of one station.** Near enough to be one place, and not contradicted
     *   by the API's own station-level [TransitLocation.importance]. This is what catches the
     *   poles the map caches, which carry no area at all and so can never match on the first
     *   rule.
     *
     * Rows with no area on either side deliberately fall through to proximity only: "no city" is
     * not evidence of being the same city, and two unlabelled map poles of the same name can sit
     * in different countries.
     */
    private fun TransitLocation.belongsToSameStationAs(other: TransitLocation): Boolean {
        if (areaLabel != null && areaLabel == other.areaLabel) return true
        val bothScored = importance > 0.0 && other.importance > 0.0
        if (bothScored && importance != other.importance) return false
        return GeoPoint(lat, lon).distanceMetersTo(GeoPoint(other.lat, other.lon)) <= STATION_RADIUS_METERS
    }

    /** The one row a cluster is shown as; see [groupIntoStations]. */
    private fun List<TransitLocation>.toStation(bias: GeoPoint?): TransitLocation {
        val representative = maxWithOrNull(
            compareBy<TransitLocation> { if (it.areaLabel != null) 1 else 0 }
                .thenBy { it.importance }
                // Nearest wins among members that are equally worth showing — negated because
                // the comparator elects the maximum.
                .thenBy { place ->
                    bias?.let { -GeoPoint(place.lat, place.lon).distanceMetersTo(it) } ?: 0.0
                }
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

    /**
     * How well one query word is answered by a name, as the multiplier the fit was made against.
     *
     * The gap between a whole word and a word *prefix* is small on purpose — the user is still
     * typing, so every query ends mid-word — while matching only inside a word ("ark" in "Park")
     * is a much weaker claim to have understood what they meant.
     */
    private fun matchQuality(token: String, nameTokens: List<String>): Double = when {
        nameTokens.any { it == token } -> QUALITY_WHOLE_WORD
        nameTokens.any { it.startsWith(token) } -> QUALITY_WORD_PREFIX
        nameTokens.any { it.contains(token) } -> QUALITY_INSIDE_WORD
        else -> QUALITY_NO_MATCH
    }

    /**
     * The fitted cost of [place] for an already-tokenized query, or null when it is not an answer.
     *
     * The geocoder does return places that only match part of the query — searching "Park Gó"
     * near Gorzów answers with "Park 111" as well as "N-Park Gorzów" — so a partial match is a
     * result, just an expensive one ([UNMATCHED_PENALTY]). What it is *not* is a licence to list
     * every nearby stop: a query the cache has no real answer for must come back empty rather
     * than showing whatever happens to be close. [MIN_QUERY_COVERAGE] is that floor, and it is
     * measured — at half the query's characters the local list still reaches 96% of what the API
     * itself returns, while "Park of the Seven Seas" against a cache without one finds nothing.
     */
    private fun scoreTokenized(
        queryTokens: List<String>,
        place: TransitLocation,
        bias: GeoPoint?,
        biasStrength: Int,
    ): Double? {
        if (queryTokens.isEmpty()) return null
        val nameTokens = tokenize(place.name)
        if (nameTokens.isEmpty()) return null

        val qualities = queryTokens.map { matchQuality(it, nameTokens) }

        // Only a word or word-prefix hit counts toward coverage: finding the query buried inside
        // a longer word is what makes an unrelated stop look like a match.
        val queryLength = queryTokens.sumOf { it.length }
        val coveredLength = queryTokens.indices
            .filter { qualities[it] >= QUALITY_WORD_PREFIX }
            .sumOf { queryTokens[it].length }
        if (queryLength == 0 || coveredLength.toDouble() / queryLength < MIN_QUERY_COVERAGE) return null

        val foldedQuery = queryTokens.joinToString(" ")
        val foldedName = nameTokens.joinToString(" ")

        var score = BASE_INTERCEPT
        // The API's own view of how significant the place is, which separates a city's main
        // interchange from a request stop that happens to share its name. Scores are tiny
        // fractions spanning orders of magnitude, so the fit is against their logarithm.
        score += IMPORTANCE_WEIGHT * ln(max(place.importance, MIN_IMPORTANCE))
        // A short name containing the query is mostly the query; a long one buries it.
        score += NAME_COVERAGE_WEIGHT * foldedQuery.length / max(foldedName.length, 1)
        score += QUALITY_WEIGHT * qualities.average()
        score += UNMATCHED_PENALTY *
            qualities.count { it == QUALITY_NO_MATCH }.toDouble() / qualities.size
        // A stop can be searched from, departed from and routed through; an address cannot.
        if (place.stopId != null) score += IS_STOP

        if (bias != null && biasStrength > 0) {
            val metres = GeoPoint(place.lat, place.lon).distanceMetersTo(bias)
            score += biasStrength * distanceLadder(metres)
        }
        return score
    }

    /**
     * The geocoder's proximity bonus, per unit of `placeBias`.
     *
     * A step ladder rather than a smooth falloff because that is measurably what the API does:
     * every band edge below was found by bisection to within a couple of metres, and removing
     * exactly this term makes a result's score identical at every bias strength. Smoothing it
     * would rank the cached rows on a different curve from the remote ones.
     */
    private fun distanceLadder(metres: Double): Double = when {
        metres < 2_000.0 -> -2.5
        metres < 10_000.0 -> -2.0
        metres < 100_000.0 -> -1.0
        metres < 1_000_000.0 -> -0.5
        else -> 0.0
    }

    private val NON_WORD = Regex("[^\\p{L}\\p{Nd}]+")

    // --- Fitted constants. See the class doc: these reproduce the live geocoder's ordering and
    // are not free parameters to tune by eye.

    private const val BASE_INTERCEPT = -9.5
    private const val IMPORTANCE_WEIGHT = -0.25
    private const val NAME_COVERAGE_WEIGHT = -8.0
    private const val QUALITY_WEIGHT = -2.0
    private const val UNMATCHED_PENALTY = 12.5
    private const val IS_STOP = -3.5

    /**
     * The floor [IMPORTANCE_WEIGHT]'s logarithm is taken at. Most cached rows come from
     * `/v6/map/stops`, which supplies no importance at all and leaves it 0 — without a floor
     * those would score negative infinity and sweep the list.
     */
    private const val MIN_IMPORTANCE = 1e-5

    /** See [scoreTokenized]; measured, not picked. */
    private const val MIN_QUERY_COVERAGE = 0.5

    private const val QUALITY_WHOLE_WORD = 1.0
    private const val QUALITY_WORD_PREFIX = 0.75
    private const val QUALITY_INSIDE_WORD = 0.4
    private const val QUALITY_NO_MATCH = 0.0

    /**
     * How far apart two same-named stops may sit and still be one station. Generous, because a
     * big interchange spreads its platforms over a couple of streets — and two genuinely
     * distinct stops that share a name this closely would be indistinguishable to the user
     * anyway.
     */
    private const val STATION_RADIUS_METERS = 400.0

    /**
     * Rows pulled from the cache per candidate query before ranking — once by significance and
     * once by proximity, see `PlaceCacheRepository.search`. Generous: the ranker decides what is
     * best, and cutting here would hide the row it would have put first.
     */
    const val CANDIDATE_LIMIT = 200
}
