package pl.dakil.transport.domain.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.PedalBike
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Tram
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import pl.dakil.transport.R

/** Domain-level mode of transport, mapped from the MOTIS `Mode` enum (see openapi.yaml). */
enum class TransportMode(
    val icon: ImageVector,
    val color: Color,
    /** Translated display name; read with `stringResource(mode.labelRes)`. */
    @param:StringRes val labelRes: Int,
    /**
     * Translated name of a *place* served by this mode ("Bus stop", "Train station"), as opposed
     * to the mode itself. Used wherever a stop is described, so a stop never reads identically to
     * a vehicle of the same mode. The street modes fall back to a generic stop name — they never
     * label a stop in practice.
     */
    @param:StringRes val stopLabelRes: Int,
) {
    WALK(Icons.AutoMirrored.Filled.DirectionsWalk, Color(0xFF6B7280), R.string.mode_walk, R.string.stop_generic),
    BIKE(Icons.Default.PedalBike, Color(0xFF16A34A), R.string.mode_bike, R.string.stop_generic),
    CAR(Icons.Default.DirectionsCar, Color(0xFF6B7280), R.string.mode_car, R.string.stop_generic),
    TRAM(Icons.Default.Tram, Color(0xFFDC2626), R.string.mode_tram, R.string.stop_tram),
    SUBWAY(Icons.Default.Subway, Color(0xFF2563EB), R.string.mode_subway, R.string.stop_subway),
    FERRY(Icons.Default.DirectionsBoat, Color(0xFF0891B2), R.string.mode_ferry, R.string.stop_ferry),
    AIRPLANE(Icons.Default.Flight, Color(0xFF7C3AED), R.string.mode_airplane, R.string.stop_airplane),
    BUS(Icons.Default.DirectionsBus, Color(0xFFEA580C), R.string.mode_bus, R.string.stop_bus),
    COACH(Icons.Default.DirectionsBus, Color(0xFFB45309), R.string.mode_coach, R.string.stop_coach),
    RAIL(Icons.Default.Train, Color(0xFF15803D), R.string.mode_rail, R.string.stop_rail),
    HIGHSPEED_RAIL(
        Icons.Default.Train,
        Color(0xFF9333EA),
        R.string.mode_highspeed_rail,
        R.string.stop_highspeed_rail,
    ),
    LONG_DISTANCE(Icons.Default.Train, Color(0xFF15803D), R.string.mode_long_distance, R.string.stop_long_distance),
    NIGHT_RAIL(Icons.Default.Train, Color(0xFF1E3A8A), R.string.mode_night_rail, R.string.stop_night_rail),
    REGIONAL_RAIL(Icons.Default.Train, Color(0xFF15803D), R.string.mode_regional_rail, R.string.stop_regional_rail),
    SUBURBAN(Icons.Default.Train, Color(0xFF0D9488), R.string.mode_suburban, R.string.stop_suburban),
    FUNICULAR(Icons.Default.Tram, Color(0xFF854D0E), R.string.mode_funicular, R.string.stop_funicular),
    AERIAL_LIFT(Icons.Default.Tram, Color(0xFF854D0E), R.string.mode_aerial_lift, R.string.stop_aerial_lift),
    OTHER(Icons.Default.DirectionsBus, Color(0xFF6B7280), R.string.mode_other, R.string.stop_generic);

    companion object {
        fun fromApiValue(value: String?): TransportMode =
            value?.let { v -> entries.firstOrNull { it.name == v } } ?: OTHER
    }
}
