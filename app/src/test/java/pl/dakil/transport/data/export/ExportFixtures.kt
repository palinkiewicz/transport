package pl.dakil.transport.data.export

import java.time.OffsetDateTime
import java.time.ZoneOffset
import pl.dakil.transport.domain.model.GeoPoint
import pl.dakil.transport.domain.model.IntermediateStop
import pl.dakil.transport.domain.model.Journey
import pl.dakil.transport.domain.model.JourneyLeg
import pl.dakil.transport.domain.model.TransitLocation
import pl.dakil.transport.domain.model.TransportMode

/**
 * The one journey every writer test exports, so GPX, KML and GeoJSON are compared on identical
 * input and a difference between them is always the format and never the fixture.
 */
internal object ExportFixtures {

    val labels = ExportLabels(
        documentName = "Home → Hotel",
        originName = "Home",
        destinationName = "Hotel",
        creator = "Transport test",
        accessLegName = { "Walk" },
        board = "Board",
        transfer = "Change at",
        alight = "Get off at",
        descSeparator = " · ",
        track = { "Pl. $it" },
        towards = { "towards $it" },
        stopsFolder = "Stops",
        routeFolder = "Route",
    )

    /** The same labels, with a colour on every leg — what the itinerary screen actually hands over. */
    val colouredLabels = labels.copy(legColor = { "E1251B" })

    val now: OffsetDateTime = OffsetDateTime.of(2026, 8, 6, 8, 0, 0, 0, ZoneOffset.ofHours(2))

    fun place(name: String, lat: Double, lon: Double, stopId: String? = null) =
        TransitLocation(name = name, lat = lat, lon = lon, stopId = stopId)

    fun at(hour: Int, minute: Int): OffsetDateTime =
        OffsetDateTime.of(2026, 8, 6, hour, minute, 0, 0, ZoneOffset.ofHours(2))

    /**
     * START --walk--> Centralna --rail (1 intermediate)--> Airport --bus--> Terminal A --walk--> END.
     *
     * Covers every boundary kind at once: an origin the API refuses to name, a walk that feeds a
     * ride, a genuine vehicle-to-vehicle change, and a ride that ends in a walk.
     */
    val journey: Journey = run {
        val home = place("START", 52.1, 21.0)
        val centralna = place("Warszawa Centralna", 52.228_67, 21.003_41, stopId = "pl:1")
        val airport = place("Lotnisko Chopina", 52.170_12, 20.973_45, stopId = "pl:2")
        val terminal = place("Terminal A", 52.171, 20.974, stopId = "pl:4")
        val end = place("END", 52.172, 20.975)
        Journey(
            id = "j1",
            duration = 3_600,
            startTime = at(8, 10),
            endTime = at(9, 10),
            transfers = 0,
            legs = listOf(
                JourneyLeg(
                    mode = TransportMode.WALK,
                    fromPlace = home,
                    toPlace = centralna,
                    startTime = at(8, 10),
                    endTime = at(8, 20),
                    scheduledStartTime = at(8, 10),
                    scheduledEndTime = at(8, 20),
                    realTime = false,
                    duration = 600,
                    path = listOf(GeoPoint(52.1, 21.0), GeoPoint(52.228_67, 21.003_41)),
                ),
                JourneyLeg(
                    mode = TransportMode.RAIL,
                    tripId = "trip-1",
                    fromPlace = centralna,
                    toPlace = airport,
                    fromTrack = "3",
                    startTime = at(8, 25),
                    endTime = at(8, 50),
                    // Two minutes late, so the real/scheduled switch has something to show.
                    scheduledStartTime = at(8, 23),
                    scheduledEndTime = at(8, 48),
                    realTime = true,
                    duration = 1_500,
                    headsign = "Lotnisko Chopina",
                    routeShortName = "S2",
                    agencyName = "Koleje Mazowieckie",
                    intermediateStops = listOf(
                        IntermediateStop(
                            place = place("Ochota", 52.21, 20.99, stopId = "pl:3"),
                            arrivalTime = at(8, 32),
                            scheduledArrivalTime = at(8, 30),
                        ),
                    ),
                    path = listOf(
                        GeoPoint(52.228_67, 21.003_41),
                        GeoPoint(52.21, 20.99),
                        GeoPoint(52.170_12, 20.973_45),
                    ),
                ),
                JourneyLeg(
                    mode = TransportMode.BUS,
                    tripId = "trip-2",
                    fromPlace = airport,
                    toPlace = terminal,
                    startTime = at(8, 55),
                    endTime = at(9, 5),
                    scheduledStartTime = at(8, 55),
                    scheduledEndTime = at(9, 5),
                    realTime = false,
                    duration = 600,
                    headsign = "Terminal A",
                    routeShortName = "188",
                    path = listOf(GeoPoint(52.170_12, 20.973_45), GeoPoint(52.171, 20.974)),
                ),
                JourneyLeg(
                    mode = TransportMode.WALK,
                    fromPlace = terminal,
                    toPlace = end,
                    startTime = at(9, 5),
                    endTime = at(9, 10),
                    scheduledStartTime = at(9, 5),
                    scheduledEndTime = at(9, 10),
                    realTime = false,
                    duration = 300,
                    // No geometry: this leg must contribute a waypoint but no track.
                    path = emptyList(),
                ),
            ),
        )
    }
}
