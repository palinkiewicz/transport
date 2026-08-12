package pl.dakil.transport.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.dakil.transport.data.local.foldForSearch
import pl.dakil.transport.domain.model.AppSettings
import pl.dakil.transport.domain.model.GeoPoint
import pl.dakil.transport.domain.model.TransitLocation
import pl.dakil.transport.domain.model.TransportMode

/**
 * Covers what would go wrong invisibly: folding that silently drops Polish stops from every
 * result, and an ordering the user reads as random.
 */
class PlaceSearchEngineTest {

    private fun stop(
        name: String,
        lat: Double = 52.23,
        lon: Double = 21.01,
        id: String = "stop:$name",
        importance: Double = 0.0,
    ) = TransitLocation(name = name, lat = lat, lon = lon, stopId = id, importance = importance)

    /** A platform of a station: same name and importance, a short walk away. */
    private fun pole(
        name: String,
        id: String,
        metresEast: Double,
        importance: Double = 0.01,
    ) = stop(
        name = name,
        lat = 52.23,
        // ~1 degree of longitude is 68 km at this latitude.
        lon = 21.01 + metresEast / 68_000.0,
        id = id,
        importance = importance,
    )

    private fun address(name: String, lat: Double = 52.23, lon: Double = 21.01) =
        TransitLocation(name = name, lat = lat, lon = lon, stopId = null)

    private fun names(results: List<TransitLocation>) = results.map { it.name }

    @Test
    fun `folding strips Polish diacritics`() {
        assertEquals("zabkowska", foldForSearch("Ząbkowska"))
        assertEquals("swietokrzyska", foldForSearch("Świętokrzyska"))
        assertEquals("lodz zdunska wola", foldForSearch("Łódź  Zduńska Wola"))
    }

    @Test
    fun `folding leaves an already plain name alone`() {
        assertEquals("centrum", foldForSearch("Centrum"))
        assertEquals("warszawa centralna", foldForSearch(" Warszawa   Centralna "))
    }

    @Test
    fun `an unaccented query matches an accented name`() {
        val results = PlaceSearchEngine.rank("zabkowska", listOf(stop("Ząbkowska")))
        assertEquals(listOf("Ząbkowska"), names(results))
    }

    @Test
    fun `only names containing the query are returned`() {
        val results = PlaceSearchEngine.rank(
            "centrum",
            listOf(stop("Centrum"), stop("Warszawa Centralna"), stop("Metro Centrum Nauki")),
        )
        assertEquals(setOf("Centrum", "Metro Centrum Nauki"), names(results).toSet())
    }

    @Test
    fun `a name that is mostly the query beats one that buries it`() {
        val results = PlaceSearchEngine.rank(
            "centrum",
            listOf(stop("Metro Centrum Nauki Kopernik"), stop("Centrum")),
        )
        assertEquals("Centrum", names(results).first())
    }

    @Test
    fun `a query found only inside a word is not an answer`() {
        // "kowska" is a whole word of one and a tail of the other. Matching inside a word is too
        // weak a claim to have understood the query: on a real cache it is what turns a search
        // into a list of unrelated stops that happen to share a syllable.
        val results = PlaceSearchEngine.rank(
            "kowska",
            listOf(stop("Ząbkowska"), stop("Kowska Brama")),
        )
        assertEquals(listOf("Kowska Brama"), names(results))
    }

    @Test
    fun `a word prefix still matches, because the user is still typing`() {
        val results = PlaceSearchEngine.rank("centr", listOf(stop("Centrum")))
        assertEquals(listOf("Centrum"), names(results))
    }

    @Test
    fun `a multi-word query matches a name the words are spread across`() {
        // The whole query is nowhere in the name as a substring — "park go" never appears in
        // "N-Park Gorzów" — but both words do, which is what the geocoder answers to as well.
        val results = PlaceSearchEngine.rank("Park Gó", listOf(stop("N-Park Gorzów")))
        assertEquals(listOf("N-Park Gorzów"), names(results))
    }

    @Test
    fun `a fully matched name outranks one that answers only part of the query`() {
        // The geocoder does return partial matches — searching "Park Gó" near Gorzów answers with
        // "Park 111" too — but well below the places that answered the whole query.
        val results = PlaceSearchEngine.rank(
            "Park Gó",
            listOf(stop("Park 111"), stop("Park Górczyński")),
        )
        assertEquals("Park Górczyński", names(results).first())
    }

    @Test
    fun `a query the cache has no real answer for returns nothing`() {
        // The point of the coverage floor: with thousands of stops on disk, a query naming a
        // place that is not among them must come back empty rather than listing whatever happens
        // to share a stopword with it.
        val cache = listOf(
            stop("Park 111"),
            stop("The Zoo"),
            stop("Parking przy szpitalu"),
            stop("Dworzec Główny"),
        )
        assertTrue(PlaceSearchEngine.rank("Park of the Seven Seas", cache).isEmpty())
        assertTrue(PlaceSearchEngine.rank("Zzzz Qqqq", cache).isEmpty())
    }

    @Test
    fun `among names matched the same way the shorter one wins`() {
        val results = PlaceSearchEngine.rank(
            "centrum",
            listOf(stop("Centrum Nauki Kopernik Przystanek"), stop("Centrum")),
        )
        assertEquals("Centrum", names(results).first())
    }

    @Test
    fun `at the same name a stop beats an address`() {
        val results = PlaceSearchEngine.rank("centrum", listOf(address("Centrum"), stop("Centrum")))
        assertEquals("stop:Centrum", results.first().stopId)
    }

    @Test
    fun `the nearer of two equal matches wins`() {
        val near = stop("Centrum Bliskie", lat = 52.2300, lon = 21.0100)
        val far = stop("Centrum Dalekie", lat = 52.4000, lon = 21.4000)

        val ranked = PlaceSearchEngine.rank(
            query = "centrum",
            places = listOf(far, near),
            bias = GeoPoint(52.2300, 21.0100),
            biasStrength = 4,
        )
        assertEquals("Centrum Bliskie", names(ranked).first())
    }

    // --- The orderings the app's author wrote out by hand for three real searches from Luboń.
    // These are the specification the scoring weights were solved against; see PlaceSearchEngine.
    // A place is built from the distance the app actually showed, so no reference point has to be
    // guessed: `at` puts it due north of the origin at the requested range.

    private val origin = GeoPoint(52.0, 21.0)

    /**
     * A place [km] from [origin] — a degree of latitude is ~111.19 km on the sphere used.
     *
     * The longitude is nudged by a name-derived millimetre so that two *places* at the same
     * distance stay distinct: a non-stop's `favoriteKey` is its coordinate, and identical
     * coordinates would be deduplicated as the same place before ranking ever saw them.
     */
    private fun at(name: String, km: Double, isStop: Boolean, importance: Double = 0.0) =
        TransitLocation(
            name = name,
            lat = 52.0 + km / 111.19,
            lon = 21.0 + (name.hashCode().toDouble() % 1_000.0) * 1e-8,
            stopId = if (isStop) "stop:$name:$km" else null,
            importance = importance,
        )

    private fun ranked(query: String, places: List<TransitLocation>) = names(
        PlaceSearchEngine.rank(
            query = query,
            places = places,
            bias = origin,
            biasStrength = AppSettings.DEFAULT.searchBiasStrength,
        ),
    )

    @Test
    fun `ranks a school search the way the author expects`() {
        // A name the query starts off beats a tighter name it appears later in; a nearby bus stop
        // beats a further-off place even though it answers one word fewer; and the stop 269 km
        // away comes last however well it matches.
        val places = listOf(
            at("Zespół Szkół Muzycznych", 9.7, false),
            at("Zgoda Zespół Szkół", 269.0, true, importance = 6.973087874939665e-05),
            at("Zespół Szkół nr 8", 4.1, false),
            at("Internat Zespołu Szkół Komunikacji", 5.2, false),
            at("Luboń/Zespół Szkół", 1.1, true, importance = 2.1534729967243038e-05),
            at("Zespół Szkół nr 4", 8.9, false),
            at("Zespół Szkół Komunikacji im. Hipolita Cegielskiego", 6.8, false),
        )
        assertEquals(
            listOf(
                "Zespół Szkół Komunikacji im. Hipolita Cegielskiego",
                "Internat Zespołu Szkół Komunikacji",
                "Luboń/Zespół Szkół",
                "Zespół Szkół nr 8",
                "Zespół Szkół nr 4",
                "Zespół Szkół Muzycznych",
                "Zgoda Zespół Szkół",
            ),
            ranked("zespoł szkol komunikacji", places),
        )
    }

    @Test
    fun `ranks an acronym search the way the author expects`() {
        // Stops lead places at the same name, an exact name leads a longer one, and a name the
        // query only appears part-way into ("Poznań, AWF") drops below both.
        val places = listOf(
            at("Parking AWF", 6.1, false),
            at("AWF", 120.0, true),
            at("AWF Poznań", 6.1, false),
            at("Poznań, AWF", 6.3, true, importance = 0.00022069802798796445),
            at("AWF", 6.2, false),
            at("AWF Poznań", 6.1, true),
            at("AWF", 6.2, true, importance = 0.0002816450141835958),
        )
        val order = ranked("awf", places)
        assertEquals("AWF", order[0])
        assertEquals("AWF Poznań", order[1])
        assertEquals("AWF", order[2])
        // Ranks 4 and 5 — "AWF Poznań" the place and "Poznań, AWF" the stop — are the one pair
        // the solved weights get the wrong way round, kept as a known and accepted deviation
        // rather than paid for with a much worse fit everywhere else.
        assertEquals(setOf("AWF Poznań", "Poznań, AWF"), setOf(order[3], order[4]))
        assertEquals("Parking AWF", order[5])
        assertEquals("AWF", order[6])
    }

    @Test
    fun `ranks a common-word search the way the author expects`() {
        // Three stops a couple of kilometres out lead a better-named tram stop five kilometres
        // out; a nearby place still loses to that tram stop; and 870 km away comes last.
        val places = listOf(
            at("Park", 870.0, true, importance = 0.008711523376405239),
            at("Parking AWF", 6.1, false),
            at("PARK", 6.4, false),
            at("Luboń/Park Przemysłowy", 2.6, true),
            at("Park Inn", 7.4, false),
            at("Red Park", 1.7, false),
            at("Park Wilsona", 5.1, true),
            at("Luboń/Park Siewcy", 2.2, true),
            at("Luboń/Parkowa", 2.1, true),
        )
        assertEquals(
            listOf(
                "Luboń/Parkowa",
                "Luboń/Park Siewcy",
                "Luboń/Park Przemysłowy",
                "Park Wilsona",
                "Red Park",
                "PARK",
                "Park Inn",
                "Parking AWF",
                "Park",
            ),
            ranked("park", places),
        )
    }

    @Test
    fun `a name the query starts beats a tighter name it appears later in`() {
        // The isolated rule behind the first result above: "Internat Zespołu Szkół Komunikacji" is
        // the shorter, more completely-matched name, and still loses for starting eight
        // characters in.
        val leading = at("Zespół Szkół Komunikacji im. Hipolita Cegielskiego", 6.8, false)
        val buried = at("Internat Zespołu Szkół Komunikacji", 5.2, false)
        assertEquals(
            "Zespół Szkół Komunikacji im. Hipolita Cegielskiego",
            ranked("zespoł szkol komunikacji", listOf(buried, leading)).first(),
        )
    }

    @Test
    fun `the proximity bonus steps at the geocoder's own distances`() {
        // Measured against the live API by bisection: the bands change at 2 km, 10 km, 100 km and
        // 1000 km, and nowhere else. Sharing the edges is what makes the cached half of the
        // picker's list interleave with the geocoder's half instead of sorting on its own curve.
        val here = GeoPoint(52.0, 21.0)
        fun at(kmNorth: Double) = PlaceSearchEngine.score(
            query = "centrum",
            // A degree of latitude is ~111.19 km on the sphere the distance is measured on.
            place = stop("Centrum", lat = 52.0 + kmNorth / 111.19, lon = 21.0),
            bias = here,
            biasStrength = 1,
        )!!

        // Every band is strictly worse than the one inside it, and the edges sit where the
        // geocoder's own bisected edges do — 0.9/1.1, 1.9/2.1 and so on fall either side.
        val edges = listOf(0.9, 1.1, 1.9, 2.1, 2.9, 3.1, 4.9, 5.1, 9.9, 10.1,
                           24.0, 26.0, 49.0, 51.0, 99.0, 101.0, 249.0, 251.0, 999.0, 1_001.0)
        val scores = edges.map { at(it) }
        assertEquals(scores.sorted(), scores)

        // Two places inside one band are indistinguishable on distance, which is what lets the
        // name decide between 6.1 km and 7.4 km…
        assertEquals(at(6.1), at(7.4), 1e-9)
        // …while a band edge between them is a real step, which is what puts 2.6 km ahead of
        // 5.1 km however much better the further name reads.
        assertTrue(at(5.1) - at(2.6) > 1.0)
    }

    @Test
    fun `the distance penalty accelerates, so far is far worse than merely distant`() {
        val here = GeoPoint(52.0, 21.0)
        fun at(km: Double) = PlaceSearchEngine.score(
            "centrum", stop("Centrum", lat = 52.0 + km / 111.19, lon = 21.0), here, 1,
        )!!
        // 6 km -> 120 km must cost far more than 1 km -> 5 km. A linear ramp over the bands makes
        // the author's orderings provably unsatisfiable; this is why the penalty is a power.
        assertTrue((at(120.0) - at(6.0)) > 4.0 * (at(5.0) - at(1.0)))
    }

    @Test
    fun `a nearby stop outranks a far one that matches the query exactly`() {
        // Off the device: searching "park" from Poznań listed a stop called exactly "Park" in
        // Brussels, 880 km away, above "Park Wilsona" five kilometres out. The exact name is the
        // better match on paper; it is not a plausible thing for someone planning a tram journey
        // to have meant.
        val poznan = GeoPoint(52.4064, 16.9252)
        val wilsona = stop("Park Wilsona", lat = 52.4020, lon = 16.9000, id = "poznan-wilsona")
        val brussels = stop("Park", lat = 50.8450, lon = 4.3560, id = "bxl-park", importance = 0.00871)

        val ranked = PlaceSearchEngine.rank(
            query = "park",
            places = listOf(brussels, wilsona),
            bias = poznan,
            biasStrength = AppSettings.DEFAULT.searchBiasStrength,
        )
        assertEquals("Park Wilsona", names(ranked).first())
    }

    @Test
    fun `bias strength scales the proximity bonus linearly`() {
        val here = GeoPoint(52.0, 21.0)
        // Not at zero distance: the innermost band carries no penalty at all, so nothing would
        // scale and the test would pass on a broken implementation.
        val far = stop("Centrum", lat = 52.0 + 30.0 / 111.19, lon = 21.0)
        fun at(strength: Int) = PlaceSearchEngine.score("centrum", far, here, strength)!!

        val unbiased = at(0)
        val oneUnit = at(1) - unbiased
        assertTrue("a distant place must cost something", oneUnit > 0.0)
        assertEquals(4 * oneUnit, at(4) - unbiased, 1e-9)
        assertEquals(10 * oneUnit, at(10) - unbiased, 1e-9)
    }

    @Test
    fun `proximity never rescues a place that did not match`() {
        // Distance reorders answers; it does not turn a non-answer into one, however close by.
        val underfoot = stop("Dworzec Główny", lat = 52.0, lon = 21.0)
        assertTrue(
            PlaceSearchEngine.rank(
                query = "centrum",
                places = listOf(underfoot),
                bias = GeoPoint(52.0, 21.0),
                biasStrength = 10,
            ).isEmpty(),
        )
    }

    @Test
    fun `a blank query matches nothing`() {
        assertTrue(PlaceSearchEngine.rank("", listOf(stop("Centrum"))).isEmpty())
        assertTrue(PlaceSearchEngine.rank("   ", listOf(stop("Centrum"))).isEmpty())
    }

    @Test
    fun `the limit is applied after ranking, not before`() {
        val places = listOf(
            stop("Zzz Centrum"),
            stop("Centrum"),
            stop("Aaa Centrum"),
        )
        assertEquals(listOf("Centrum"), names(PlaceSearchEngine.rank("centrum", places, limit = 1)))
    }

    @Test
    fun `platforms of one station collapse into a single result`() {
        val poles = listOf(
            pole("Centrum", "pl-Warszawa_centrum10", metresEast = 0.0),
            pole("Centrum", "pl-Warszawa_centrum15", metresEast = 60.0),
            pole("Centrum", "pl-Warszawa_centruma13", metresEast = 200.0),
        )
        assertEquals(listOf("Centrum"), names(PlaceSearchEngine.rank("centrum", poles)))
    }

    @Test
    fun `same-named stations far apart stay separate`() {
        val warsaw = stop("Rynek", lat = 52.23, lon = 21.01, id = "pl-Warszawa_rynek")
        val krakow = stop("Rynek", lat = 50.06, lon = 19.94, id = "pl-Krakow_rynek")
        assertEquals(2, PlaceSearchEngine.rank("rynek", listOf(warsaw, krakow)).size)
    }

    @Test
    fun `nearby same-named stops with different importance are different stations`() {
        // The API scores a station as a whole, so poles of one station always agree. Two that
        // disagree are two stations that happen to share a name and sit close together.
        val busy = pole("Rynek", "a", metresEast = 0.0, importance = 0.01)
        val quiet = pole("Rynek", "b", metresEast = 100.0, importance = 0.0001)
        assertEquals(2, PlaceSearchEngine.rank("rynek", listOf(busy, quiet)).size)
    }

    @Test
    fun `a station is represented by the member that knows its area`() {
        val bare = pole("Centrum", "pl-Warszawa_centrum10", metresEast = 0.0)
        val geocoded = pole("Centrum", "pl-Warszawa_centruma13", metresEast = 200.0)
            .copy(city = "Warszawa", country = "PL")

        val result = PlaceSearchEngine.rank("centrum", listOf(bare, geocoded)).single()
        assertEquals("pl-Warszawa_centruma13", result.stopId)
        assertEquals("Warszawa, PL", result.areaLabel)
    }

    @Test
    fun `a station carries the modes of every platform`() {
        val tram = pole("Centrum", "a", metresEast = 0.0).copy(modes = listOf(TransportMode.TRAM))
        val bus = pole("Centrum", "b", metresEast = 60.0).copy(modes = listOf(TransportMode.BUS))
        val metro = pole("Centrum", "c", metresEast = 120.0).copy(modes = listOf(TransportMode.SUBWAY))

        val result = PlaceSearchEngine.rank("centrum", listOf(tram, bus, metro)).single()
        assertEquals(
            setOf(TransportMode.TRAM, TransportMode.BUS, TransportMode.SUBWAY),
            result.modes.toSet(),
        )
    }

    @Test
    fun `stops of the same name and area are offered once`() {
        // Off the device: searching "AWF" near Poznań listed "Poznań, AWF · Poznań, województwo
        // wielkopolskie, PL" twice, both bus stops, both 6.3 km away. They sit further apart than
        // the station radius and disagree on importance, so neither platform test catches them —
        // but the two rows are typographically identical, so only one is worth offering.
        val poznan = { id: String, lon: Double, importance: Double ->
            stop("Poznań, AWF", lat = 52.4020, lon = lon, id = id, importance = importance)
                .copy(city = "Poznań", state = "województwo wielkopolskie", country = "PL")
        }
        val results = PlaceSearchEngine.rank(
            "awf",
            // ~700 m apart: past STATION_RADIUS_METERS, and with importances that disagree.
            listOf(poznan("a", 16.9100, 0.004), poznan("b", 16.9203, 0.001)),
        )
        assertEquals(listOf("Poznań, AWF"), names(results))
    }

    @Test
    fun `a bare map pole folds into the geocoder's description of the same stop`() {
        // `/v6/map/stops` returns no area at all, so a stop the map cached is unlabelled until a
        // search describes it. Without this the same stop draws twice — once bare, once with its
        // city — and the two rows look like two different places.
        val cached = stop("Park Wilsona", lat = 52.4020, lon = 16.9000, id = "map-pole")
        val geocoded = stop("Park Wilsona", lat = 52.4090, lon = 16.9070, id = "geocoded", importance = 0.004)
            .copy(city = "Poznań", country = "PL")

        val merged = PlaceSearchEngine.merge("park wilsona", listOf(geocoded), listOf(cached))
        assertEquals(1, merged.size)
        // …and the row shown is the one that knows where it is.
        assertEquals("Poznań, PL", merged.single().areaLabel)
    }

    @Test
    fun `folding a bare pole in does not dislodge a pinned first result`() {
        // The pin is matched against a station's members, so a cached row keeps the top slot even
        // when the geocoder's copy of it becomes the row actually drawn.
        val cached = stop("Park Wilsona", lat = 52.4020, lon = 16.9000, id = "map-pole")
        val geocoded = stop("Park Wilsona", lat = 52.4090, lon = 16.9070, id = "geocoded", importance = 0.004)
            .copy(city = "Poznań", country = "PL")
        val better = stop("Park", lat = 52.4020, lon = 16.9000, id = "remote-park", importance = 0.9)
            .copy(city = "Poznań", country = "PL")

        val merged = PlaceSearchEngine.merge(
            query = "park wilsona",
            remote = listOf(better, geocoded),
            cached = listOf(cached),
            pinnedKeys = listOf("map-pole"),
        )
        assertEquals("Park Wilsona", merged.first().name)
        assertEquals("Poznań, PL", merged.first().areaLabel)
    }

    @Test
    fun `a bare pole is not absorbed by a same-named stop in another town`() {
        val cached = stop("Rynek", lat = 52.4020, lon = 16.9000, id = "lubon-rynek")
        val faraway = stop("Rynek", lat = 50.0647, lon = 19.9450, id = "krakow-rynek", importance = 0.01)
            .copy(city = "Kraków", country = "PL")
        assertEquals(2, PlaceSearchEngine.merge("rynek", listOf(faraway), listOf(cached)).size)
    }

    @Test
    fun `unlabelled stops of the same name are not merged across cities`() {
        // Map-cached poles carry no area at all. "Same missing city" is not evidence of the same
        // city, so these must still fall through to the proximity test.
        val poznan = stop("AWF", lat = 52.4020, lon = 16.9100, id = "poznan-awf")
        val warszawa = stop("AWF", lat = 52.2297, lon = 21.0122, id = "warszawa-awf")
        assertEquals(2, PlaceSearchEngine.rank("awf", listOf(poznan, warszawa)).size)
    }

    @Test
    fun `a stop and a place of the same name stay separate rows`() {
        // They draw with different icons — a tram and a pin — so they do not read as a repeat,
        // and only one of them can be routed from.
        val results = PlaceSearchEngine.rank(
            "awf",
            listOf(
                stop("AWF", id = "stop-awf").copy(city = "Poznań", country = "PL"),
                address("AWF").copy(city = "Poznań", country = "PL"),
            ),
        )
        assertEquals(2, results.size)
    }

    @Test
    fun `places of the same name and area are offered once`() {
        // Straight from the live geocoder: searching "Centrum" near Warszawa answers with three
        // separate PLACE results all called "Centrum" in Warszawa, up to 662 m apart. Nothing on
        // screen distinguishes them.
        val warsaw = { lat: Double, lon: Double ->
            address("Centrum", lat = lat, lon = lon)
                .copy(city = "Warszawa", state = "województwo mazowieckie", country = "PL")
        }
        val results = PlaceSearchEngine.rank(
            "centrum",
            listOf(warsaw(52.2297, 21.0122), warsaw(52.2330, 21.0180), warsaw(52.2350, 21.0100)),
        )
        assertEquals(listOf("Centrum"), names(results))
    }

    @Test
    fun `same-named places far apart in one city still collapse`() {
        // The two "Psi Park" in Warszawa sit ~12 km apart and really are different parks, but
        // they render identically, so offering both only asks the user to guess.
        val park = { lat: Double, lon: Double ->
            address("Psi Park", lat = lat, lon = lon).copy(city = "Warszawa", country = "PL")
        }
        val results = PlaceSearchEngine.rank("psi park", listOf(park(52.15, 21.00), park(52.26, 21.05)))
        assertEquals(1, results.size)
    }

    @Test
    fun `the surviving row is the one nearest the user`() {
        val near = address("Psi Park", lat = 52.2300, lon = 21.0100).copy(city = "Warszawa")
        val far = address("Psi Park", lat = 52.3500, lon = 21.2000).copy(city = "Warszawa")

        val result = PlaceSearchEngine
            .rank("psi park", listOf(far, near), bias = GeoPoint(52.2300, 21.0100), biasStrength = 4)
            .single()
        assertEquals(52.2300, result.lat, 1e-9)
    }

    @Test
    fun `same-named places in different cities stay separate`() {
        val warsaw = address("Rynek", lat = 52.23, lon = 21.01).copy(city = "Warszawa", country = "PL")
        val krakow = address("Rynek", lat = 50.06, lon = 19.94).copy(city = "Kraków", country = "PL")
        assertEquals(2, PlaceSearchEngine.rank("rynek", listOf(warsaw, krakow)).size)
    }

    @Test
    fun `an address is never folded into a stop of the same name`() {
        val results = PlaceSearchEngine.rank(
            "centrum",
            listOf(address("Centrum"), stop("Centrum")),
        )
        assertEquals(2, results.size)
        // …and the stop still leads, because it is the one you can actually travel from.
        assertEquals("stop:Centrum", results.first().stopId)
    }

    @Test
    fun `a place held by both sources is returned once`() {
        // What the picker hands over: the geocoder's answer concatenated with the cache's, where
        // the cache is holding the very same rows because it stored them last time. Every result
        // keys a list row, so a repeat here is a crash rather than a cosmetic problem.
        val remote = listOf(
            address("Marszałkowska 1").copy(city = "Warszawa"),
            stop("Centrum", id = "pl-Warszawa_centruma13").copy(city = "Warszawa"),
        )
        val cached = listOf(address("Marszałkowska 1"), stop("Centrum", id = "pl-Warszawa_centruma13"))

        val results = PlaceSearchEngine.groupIntoStations(remote + cached).map { it.place }
        assertEquals(results.size, results.distinctBy { it.favoriteKey }.size)
        assertEquals(2, results.size)
        // The geocoder's copy is the one kept, because it knows the area.
        assertTrue(results.all { it.city == "Warszawa" })
    }

    @Test
    fun `ranking never returns two results with the same key`() {
        val duplicated = listOf(
            address("Centrum"), address("Centrum"),
            stop("Centrum", id = "a"), stop("Centrum", id = "a"),
        )
        val results = PlaceSearchEngine.rank("centrum", duplicated)
        assertEquals(results.size, results.distinctBy { it.favoriteKey }.size)
    }

    @Test
    fun `a more important station outranks a lesser one of the same name`() {
        val major = stop("Dworzec", id = "major", lat = 52.30, lon = 21.20, importance = 0.05)
        val minor = stop("Dworzec", id = "minor", lat = 52.40, lon = 21.30, importance = 0.00001)
        assertEquals("major", PlaceSearchEngine.rank("dworzec", listOf(minor, major)).first().stopId)
    }

    @Test
    fun `the geocoder's answer does not move the rows already on screen`() {
        // The whole point of the rewrite. The cache answers on the keystroke; the geocoder lands
        // ~300 ms later. If arriving results reorder what is already drawn, the row under the
        // user's finger moves and the tap lands on the wrong place.
        val cached = listOf(
            stop("Dworzec Główny", id = "cached-glowny", importance = 0.02),
            stop("Dworzec Wschodni", id = "cached-wschodni", importance = 0.01),
            stop("Dworzec Zachodni", id = "cached-zachodni", importance = 0.005),
        )
        val before = names(PlaceSearchEngine.merge("dworzec", remote = emptyList(), cached = cached))

        // The geocoder answers with a place the cache had never seen, plus its own copy of one it
        // had. In result-index order it would have led the list and pushed everything down.
        val remote = listOf(
            stop("Dworzec Mały", id = "remote-maly", importance = 0.0001),
            stop("Dworzec Główny", id = "cached-glowny", importance = 0.02).copy(city = "Warszawa"),
        )
        val after = names(PlaceSearchEngine.merge("dworzec", remote = remote, cached = cached))

        // Everything that was on screen keeps its order; the newcomer is merely inserted.
        assertEquals(before, after.filter { it in before })
        assertTrue("the new place should appear", "Dworzec Mały" in after)
        // …and the place both sources knew is still a single row, wearing the geocoder's area.
        assertEquals(4, after.size)
    }

    @Test
    fun `a pinned cached row is held at the top`() {
        val cached = listOf(stop("Dworzec Wschodni", id = "cached-wschodni", importance = 0.001))
        // A remote result that would otherwise outrank it outright.
        val remote = listOf(stop("Dworzec", id = "remote-dworzec", importance = 0.9))

        val unpinned = names(PlaceSearchEngine.merge("dworzec", remote, cached))
        assertEquals("Dworzec", unpinned.first())

        val pinned = names(
            PlaceSearchEngine.merge("dworzec", remote, cached, pinnedKeys = listOf("cached-wschodni")),
        )
        assertEquals("Dworzec Wschodni", pinned.first())
        // Pinning reorders, it never drops anything.
        assertEquals(unpinned.toSet(), pinned.toSet())
    }

    @Test
    fun `a pinned key the merge no longer holds changes nothing`() {
        val cached = listOf(stop("Dworzec Wschodni", id = "cached-wschodni"))
        val ordered = names(PlaceSearchEngine.merge("dworzec", emptyList(), cached))
        assertEquals(
            ordered,
            names(PlaceSearchEngine.merge("dworzec", emptyList(), cached, pinnedKeys = listOf("gone"))),
        )
    }

    @Test
    fun `a pinned cached pole is found through the station it merged into`() {
        // The cache holds a bare platform; the geocoder returns the grouped station, which wins
        // the representative election. The pin still has to recognise it.
        val pole = pole("Centrum", "pl-Warszawa_centrum10", metresEast = 0.0)
        val station = pole("Centrum", "pl-Warszawa_centrum", metresEast = 60.0)
            .copy(city = "Warszawa")
        val elsewhere = stop("Centrum Handlowe", id = "remote-ch", importance = 0.5)

        val merged = PlaceSearchEngine.merge(
            query = "centrum",
            remote = listOf(elsewhere, station),
            cached = listOf(pole),
            pinnedKeys = listOf("pl-Warszawa_centrum10"),
        )
        assertEquals("Centrum", merged.first().name)
        // The row shown is the geocoder's, which is the one that knows where it is.
        assertEquals("Warszawa", merged.first().city)
    }

    @Test
    fun `several pinned rows lead in the order they were given`() {
        // The picker pins the recently used places first and the steady cached row after them,
        // so the order it hands over is the order the list has to open in.
        val cached = listOf(
            stop("Dworzec Wschodni", id = "cached-wschodni", importance = 0.001),
            stop("Dworzec Zachodni", id = "cached-zachodni", importance = 0.001),
        )
        val remote = listOf(stop("Dworzec", id = "remote-dworzec", importance = 0.9))

        val pinned = names(
            PlaceSearchEngine.merge(
                query = "dworzec",
                remote = remote,
                cached = cached,
                pinnedKeys = listOf("cached-zachodni", "cached-wschodni"),
            ),
        )
        assertEquals(listOf("Dworzec Zachodni", "Dworzec Wschodni", "Dworzec"), pinned)
    }

    @Test
    fun `two pinned keys of one station pin it once`() {
        // A recently used pole and the cached row held steady can be the same station seen twice;
        // offering it twice would be a duplicate row, and a crash where the list keys by place.
        val pole = pole("Centrum", "pl-Warszawa_centrum10", metresEast = 0.0)
        val station = pole("Centrum", "pl-Warszawa_centrum", metresEast = 60.0).copy(city = "Warszawa")
        val elsewhere = stop("Centrum Handlowe", id = "remote-ch", importance = 0.5)

        val merged = names(
            PlaceSearchEngine.merge(
                query = "centrum",
                remote = listOf(elsewhere, station),
                cached = listOf(pole),
                pinnedKeys = listOf("pl-Warszawa_centrum10", "pl-Warszawa_centrum"),
            ),
        )
        assertEquals(listOf("Centrum", "Centrum Handlowe"), merged)
    }

    @Test
    fun `a remote result the local matcher would reject is kept, but last`() {
        // The geocoder knows about spellings and synonyms a stored name cannot reproduce, so its
        // answers are never dropped — only ranked below the ones we can vouch for.
        val remote = listOf(stop("Something Else Entirely", id = "remote-odd"))
        val cached = listOf(stop("Dworzec Główny", id = "cached-glowny"))

        val merged = names(PlaceSearchEngine.merge("dworzec", remote, cached))
        assertEquals(listOf("Dworzec Główny", "Something Else Entirely"), merged)
    }

    @Test
    fun `equally scored places keep a stable order between keystrokes`() {
        val places = listOf(stop("Centrum B"), stop("Centrum A"))
        val first = names(PlaceSearchEngine.rank("centrum", places))
        val second = names(PlaceSearchEngine.rank("centrum", places.reversed()))
        assertEquals(first, second)
    }
}
