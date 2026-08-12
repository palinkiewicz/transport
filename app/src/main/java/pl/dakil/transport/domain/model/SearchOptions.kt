package pl.dakil.transport.domain.model

import androidx.annotation.StringRes
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pl.dakil.transport.R

/**
 * MOTIS pedestrian accessibility profile for transfers and first/last mile.
 *
 * Every enum here carries a `labelRes` rather than a literal: the option panels read it with
 * `stringResource(it.labelRes)`. Only the enum *names* are serialized, so the ids are free to
 * change without breaking stored options.
 */
@Serializable
enum class PedestrianProfile(@param:StringRes val labelRes: Int) {
    FOOT(R.string.pedestrian_profile_foot),
    WHEELCHAIR(R.string.pedestrian_profile_wheelchair),
}

/** Elevation cost profile for street routing: penalize inclines in favor of flatter paths. */
@Serializable
enum class ElevationCosts(@param:StringRes val labelRes: Int) {
    NONE(R.string.elevation_costs_none),
    LOW(R.string.elevation_costs_low),
    HIGH(R.string.elevation_costs_high),
}

/** Non-transit modes usable for direct connections and the first/last mile. API mode names. */
@Serializable
enum class StreetMode(@param:StringRes val labelRes: Int) {
    WALK(R.string.street_mode_walk),
    BIKE(R.string.street_mode_bike),
    CAR(R.string.street_mode_car),
    RENTAL(R.string.street_mode_rental),
}

/** GBFS rental vehicle form factors. API enum names. */
@Serializable
enum class RentalFormFactor(@param:StringRes val labelRes: Int) {
    BICYCLE(R.string.rental_form_bicycle),
    CARGO_BICYCLE(R.string.rental_form_cargo_bicycle),
    CAR(R.string.rental_form_car),
    MOPED(R.string.rental_form_moped),
    SCOOTER_STANDING(R.string.rental_form_scooter_standing),
    SCOOTER_SEATED(R.string.rental_form_scooter_seated),
    OTHER(R.string.rental_form_other),
}

/** GBFS rental vehicle propulsion types. API enum names. */
@Serializable
enum class RentalPropulsionType(@param:StringRes val labelRes: Int) {
    HUMAN(R.string.rental_propulsion_human),
    ELECTRIC_ASSIST(R.string.rental_propulsion_electric_assist),
    ELECTRIC(R.string.rental_propulsion_electric),
    COMBUSTION(R.string.rental_propulsion_combustion),
    COMBUSTION_DIESEL(R.string.rental_propulsion_combustion_diesel),
    HYBRID(R.string.rental_propulsion_hybrid),
    PLUG_IN_HYBRID(R.string.rental_propulsion_plug_in_hybrid),
    HYDROGEN_FUEL_CELL(R.string.rental_propulsion_hydrogen),
}

/**
 * How the transfer count is constrained, derived from [SearchOptions.maxTransfers]. UI-only:
 * the number itself is what gets persisted and sent.
 */
enum class TransfersMode(@param:StringRes val labelRes: Int) {
    ANY(R.string.transfers_any),
    NONE(R.string.transfers_none),
    LIMIT(R.string.transfers_limit),
}

/** Which street leg of a journey a [StreetLegOptions] instance configures. */
enum class LegContext(@param:StringRes val labelRes: Int) {
    DIRECT(R.string.leg_context_direct),
    PRE_TRANSIT(R.string.leg_context_first_mile),
    POST_TRANSIT(R.string.leg_context_last_mile),
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
    /**
     * null = no limit (the param is omitted), 0 = direct runs only, n = at most n transfers.
     * The serial name is deliberately not `maxTransfers`: that key holds the old mandatory
     * `Int` cap of 12, and decoding it would give every existing install a limit it never
     * chose. Unknown keys are ignored, so the stored value is simply dropped.
     */
    @SerialName("maxTransfersLimit") val maxTransfers: Int? = null,
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

    val transfersMode: TransfersMode
        get() = when (maxTransfers) {
            null -> TransfersMode.ANY
            0 -> TransfersMode.NONE
            else -> TransfersMode.LIMIT
        }

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
