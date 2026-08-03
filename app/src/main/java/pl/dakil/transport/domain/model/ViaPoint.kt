package pl.dakil.transport.domain.model

import kotlinx.serialization.Serializable

/**
 * An intermediate stop the journey has to pass through, mapping to one entry of the plan API's
 * `via` / `viaMinimumStay` pair.
 *
 * The API only accepts **stop ids** here — coordinates are explicitly rejected — so [location]
 * must be a location whose [TransitLocation.stopId] is set. The picker enforces this by asking
 * the geocoder for stops only; [MAX_VIA_POINTS] enforces the other limit.
 */
@Serializable
data class ViaPoint(
    val location: TransitLocation,
    /**
     * Minutes the journey must stay at this stop. `0` — the API default — means "may stay on
     * the same vehicle", i.e. the stop only has to be passed through and no transfer is
     * counted; any larger value forces a real stopover of at least that long.
     */
    val minimumStayMinutes: Int = 0,
) {
    val isPassThrough: Boolean get() = minimumStayMinutes == 0

    companion object {
        /** The plan API caps `via` at two entries (`maxItems: 2` in the MOTIS OpenAPI schema). */
        const val MAX_VIA_POINTS = 2

        /** Stay durations offered in the UI, in minutes. */
        val STAY_PRESETS_MINUTES = listOf(0, 5, 15, 30)
    }
}

/** Comma-joined stop ids for the `via` query parameter, or null when there are none. */
fun List<ViaPoint>.toViaParam(): String? =
    mapNotNull { it.location.stopId }.takeIf { it.isNotEmpty() }?.joinToString(",")

/**
 * Comma-joined minimum stay durations, aligned with [toViaParam]'s entries. Null when there is
 * nothing to send or every stay is the default `0` — the API then applies its own default.
 */
fun List<ViaPoint>.toViaMinimumStayParam(): String? =
    filter { it.location.stopId != null }
        .takeIf { vias -> vias.isNotEmpty() && vias.any { !it.isPassThrough } }
        ?.joinToString(",") { it.minimumStayMinutes.toString() }
