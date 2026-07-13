package pl.dakil.transport.domain.model

import kotlinx.serialization.Serializable

/** MOTIS pedestrian accessibility profile for transfers and first/last mile. */
@Serializable
enum class PedestrianProfile(val label: String) {
    FOOT("On foot"),
    WHEELCHAIR("Wheelchair"),
}

/** Elevation cost profile for street routing: penalize inclines in favor of flatter paths. */
@Serializable
enum class ElevationCosts(val label: String) {
    NONE("None"),
    LOW("Low"),
    HIGH("High"),
}

/** Non-transit modes usable for direct connections and the first/last mile. API mode names. */
@Serializable
enum class StreetMode(val label: String) {
    WALK("Walk"),
    BIKE("Bike"),
    CAR("Car"),
    RENTAL("Rental"),
}

/** GBFS rental vehicle form factors. API enum names. */
@Serializable
enum class RentalFormFactor(val label: String) {
    BICYCLE("Bike"),
    CARGO_BICYCLE("Cargo bike"),
    CAR("Car"),
    MOPED("Moped"),
    SCOOTER_STANDING("Scooter"),
    SCOOTER_SEATED("Seated scooter"),
    OTHER("Other"),
}

/** GBFS rental vehicle propulsion types. API enum names. */
@Serializable
enum class RentalPropulsionType(val label: String) {
    HUMAN("Human"),
    ELECTRIC_ASSIST("El. assist"),
    ELECTRIC("Electric"),
    COMBUSTION("Petrol"),
    COMBUSTION_DIESEL("Diesel"),
    HYBRID("Hybrid"),
    PLUG_IN_HYBRID("Plug-in hybrid"),
    HYDROGEN_FUEL_CELL("Hydrogen"),
}

/** Which street leg of a journey a [StreetLegOptions] instance configures. */
enum class LegContext(val label: String) {
    DIRECT("Direct"),
    PRE_TRANSIT("First mile"),
    POST_TRANSIT("Last mile"),
}

/**
 * Street routing options for one leg context (direct / first mile / last mile).
 * Empty rental sets mean "no restriction" and the matching params are omitted.
 */
@Serializable
data class StreetLegOptions(
    val modes: Set<StreetMode> = setOf(StreetMode.WALK),
    val maxTimeMinutes: Int = 15,
    val rentalFormFactors: Set<RentalFormFactor> = emptySet(),
    val rentalPropulsionTypes: Set<RentalPropulsionType> = emptySet(),
    val ignoreRentalReturnConstraints: Boolean = false,
)

/**
 * User-tunable search options for the plan and stoptimes requests, persisted as one JSON
 * blob. New fields must have defaults so old persisted values keep decoding. Fields at their
 * "unset" state (null, all categories, empty rental sets) are omitted from requests so the
 * server defaults apply.
 */
@Serializable
data class SearchOptions(
    val maxTransfers: Int = 12,
    val arriveBy: Boolean = false,
    // Routing
    val transitCategories: Set<TransitFilterCategory> = TransitFilterCategory.entries.toSet(),
    val minTransferTimeMinutes: Int = 0,
    val additionalTransferTimeMinutes: Int = 0,
    val transferTimeFactor: Float = 1.0f,
    /** Minutes; null = server default (effectively unlimited). */
    val maxTravelTimeMinutes: Int? = null,
    val useRoutedTransfers: Boolean = false,
    // Accessibility & street
    val pedestrianProfile: PedestrianProfile = PedestrianProfile.FOOT,
    /** Meters per second; null = server default. */
    val pedestrianSpeed: Float? = null,
    /** Meters per second; null = server default. */
    val cyclingSpeed: Float? = null,
    val elevationCosts: ElevationCosts = ElevationCosts.NONE,
    val requireBikeTransport: Boolean = false,
    val requireCarTransport: Boolean = false,
    val direct: StreetLegOptions = StreetLegOptions(maxTimeMinutes = 30),
    val preTransit: StreetLegOptions = StreetLegOptions(maxTimeMinutes = 15),
    val postTransit: StreetLegOptions = StreetLegOptions(maxTimeMinutes = 15),
    // Results
    val searchWindowMinutes: Int = 15,
    val numItineraries: Int = 5,
    val slowDirect: Boolean = false,
    val fastestDirectFactor: Float = 1.0f,
    /** Experimental API params (ODM/fares); null = omitted. */
    val passengers: Int? = null,
    val luggage: Int? = null,
    // Departures board
    val departuresCategories: Set<TransitFilterCategory> = TransitFilterCategory.entries.toSet(),
    val departuresCount: Int = 20,
    /** Search radius around coordinates; only used when the stop has no id. */
    val departuresRadiusMeters: Int = 300,
) {
    val isDefault: Boolean get() = this == DEFAULT

    fun legOptions(context: LegContext): StreetLegOptions = when (context) {
        LegContext.DIRECT -> direct
        LegContext.PRE_TRANSIT -> preTransit
        LegContext.POST_TRANSIT -> postTransit
    }

    fun copyLeg(context: LegContext, transform: (StreetLegOptions) -> StreetLegOptions): SearchOptions =
        when (context) {
            LegContext.DIRECT -> copy(direct = transform(direct))
            LegContext.PRE_TRANSIT -> copy(preTransit = transform(preTransit))
            LegContext.POST_TRANSIT -> copy(postTransit = transform(postTransit))
        }

    companion object {
        val DEFAULT = SearchOptions()
    }
}

/**
 * Comma-joined API mode list for a category selection, or null when every category is
 * selected (matching the server's all-transit default, so the param can be omitted).
 */
fun Set<TransitFilterCategory>.toModeParam(): String? =
    if (size == TransitFilterCategory.entries.size) null
    else flatMap { it.modes }.joinToString(",") { it.name }
