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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** Domain-level mode of transport, mapped from the MOTIS `Mode` enum (see openapi.yaml). */
enum class TransportMode(val icon: ImageVector, val color: Color) {
    WALK(Icons.AutoMirrored.Filled.DirectionsWalk, Color(0xFF6B7280)),
    BIKE(Icons.Default.PedalBike, Color(0xFF16A34A)),
    CAR(Icons.Default.DirectionsCar, Color(0xFF6B7280)),
    TRAM(Icons.Default.Tram, Color(0xFFDC2626)),
    SUBWAY(Icons.Default.Subway, Color(0xFF2563EB)),
    FERRY(Icons.Default.DirectionsBoat, Color(0xFF0891B2)),
    AIRPLANE(Icons.Default.Flight, Color(0xFF7C3AED)),
    BUS(Icons.Default.DirectionsBus, Color(0xFFEA580C)),
    COACH(Icons.Default.DirectionsBus, Color(0xFFB45309)),
    RAIL(Icons.Default.Train, Color(0xFF15803D)),
    HIGHSPEED_RAIL(Icons.Default.Train, Color(0xFF9333EA)),
    LONG_DISTANCE(Icons.Default.Train, Color(0xFF15803D)),
    NIGHT_RAIL(Icons.Default.Train, Color(0xFF1E3A8A)),
    REGIONAL_RAIL(Icons.Default.Train, Color(0xFF15803D)),
    SUBURBAN(Icons.Default.Train, Color(0xFF0D9488)),
    FUNICULAR(Icons.Default.Tram, Color(0xFF854D0E)),
    AERIAL_LIFT(Icons.Default.Tram, Color(0xFF854D0E)),
    OTHER(Icons.Default.DirectionsBus, Color(0xFF6B7280));

    companion object {
        fun fromApiValue(value: String?): TransportMode =
            value?.let { v -> entries.firstOrNull { it.name == v } } ?: OTHER
    }
}
