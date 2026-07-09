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
enum class TransportMode(val icon: ImageVector, val color: Color, val label: String) {
    WALK(Icons.AutoMirrored.Filled.DirectionsWalk, Color(0xFF6B7280), "Walking"),
    BIKE(Icons.Default.PedalBike, Color(0xFF16A34A), "Bike"),
    CAR(Icons.Default.DirectionsCar, Color(0xFF6B7280), "Car"),
    TRAM(Icons.Default.Tram, Color(0xFFDC2626), "Tram"),
    SUBWAY(Icons.Default.Subway, Color(0xFF2563EB), "Subway"),
    FERRY(Icons.Default.DirectionsBoat, Color(0xFF0891B2), "Ferry"),
    AIRPLANE(Icons.Default.Flight, Color(0xFF7C3AED), "Airplane"),
    BUS(Icons.Default.DirectionsBus, Color(0xFFEA580C), "Bus"),
    COACH(Icons.Default.DirectionsBus, Color(0xFFB45309), "Coach"),
    RAIL(Icons.Default.Train, Color(0xFF15803D), "Rail"),
    HIGHSPEED_RAIL(Icons.Default.Train, Color(0xFF9333EA), "High-speed rail"),
    LONG_DISTANCE(Icons.Default.Train, Color(0xFF15803D), "Long-distance rail"),
    NIGHT_RAIL(Icons.Default.Train, Color(0xFF1E3A8A), "Night train"),
    REGIONAL_RAIL(Icons.Default.Train, Color(0xFF15803D), "Regional rail"),
    SUBURBAN(Icons.Default.Train, Color(0xFF0D9488), "Suburban rail"),
    FUNICULAR(Icons.Default.Tram, Color(0xFF854D0E), "Funicular"),
    AERIAL_LIFT(Icons.Default.Tram, Color(0xFF854D0E), "Aerial lift"),
    OTHER(Icons.Default.DirectionsBus, Color(0xFF6B7280), "Transit");

    companion object {
        fun fromApiValue(value: String?): TransportMode =
            value?.let { v -> entries.firstOrNull { it.name == v } } ?: OTHER
    }
}
