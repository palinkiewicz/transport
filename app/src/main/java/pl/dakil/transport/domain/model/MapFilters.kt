package pl.dakil.transport.domain.model

import androidx.annotation.StringRes
import kotlinx.serialization.Serializable
import pl.dakil.transport.R

/** Data source of a vehicle position: live-tracked or computed from the schedule. */
@Serializable
enum class VehicleSource(@param:StringRes val labelRes: Int) {
    LIVE(R.string.vehicle_source_live),
    TIMETABLE(R.string.vehicle_source_timetable),
}

/**
 * The map's power-user layer filters. Serializable so it can be persisted as-is; new fields
 * must have defaults so old persisted values keep decoding.
 */
@Serializable
data class MapFilters(
    /** Stop markers to show; defaults to every category. */
    val stopCategories: Set<TransitFilterCategory> = TransitFilterCategory.entries.toSet(),
    /** Vehicle markers to show; empty (the default) disables vehicles entirely. */
    val vehicleCategories: Set<TransitFilterCategory> = emptySet(),
    /** Which data sources of vehicles to show; never empty (enforced by the UI). */
    val vehicleSources: Set<VehicleSource> = VehicleSource.entries.toSet(),
) {
    val isDefault: Boolean get() = this == DEFAULT

    fun matchesStop(stop: TransitLocation): Boolean {
        // Stops the API reports without any mode can't be categorized; treat them as OTHER.
        val categories = stop.modes.mapNotNull { TransitFilterCategory.of(it) }
            .ifEmpty { listOf(TransitFilterCategory.OTHER) }
        return categories.any { it in stopCategories }
    }

    fun matchesVehicle(vehicle: VehicleSegment): Boolean {
        val category = TransitFilterCategory.of(vehicle.mode) ?: TransitFilterCategory.OTHER
        if (category !in vehicleCategories) return false
        val source = if (vehicle.realTime) VehicleSource.LIVE else VehicleSource.TIMETABLE
        return source in vehicleSources
    }

    companion object {
        val DEFAULT = MapFilters()
    }
}
