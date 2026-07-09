package pl.dakil.transport.domain.model

import kotlinx.serialization.Serializable

/** Which vehicles to show by data source: everything, live-tracked only, or schedule-only. */
@Serializable
enum class VehicleDataFilter(val label: String) {
    ALL("All"),
    LIVE("Live"),
    TIMETABLE("Timetable"),
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
    val vehicleData: VehicleDataFilter = VehicleDataFilter.ALL,
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
        return when (vehicleData) {
            VehicleDataFilter.ALL -> true
            VehicleDataFilter.LIVE -> vehicle.realTime
            VehicleDataFilter.TIMETABLE -> !vehicle.realTime
        }
    }

    companion object {
        val DEFAULT = MapFilters()
    }
}
