package pl.dakil.transport.domain.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Commute
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Tram

/**
 * User-facing transit category grouping the fine-grained [TransportMode]s, used wherever the
 * user filters by "kind of transport" (map filters today; potentially search/results later).
 * Street modes (walk/bike/car) deliberately have no category — they aren't transit.
 */
enum class TransitFilterCategory(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val modes: Set<TransportMode>,
) {
    BUS("Bus", Icons.Default.DirectionsBus, setOf(TransportMode.BUS, TransportMode.COACH)),
    TRAM("Tram", Icons.Default.Tram, setOf(TransportMode.TRAM)),
    TRAIN(
        "Train",
        Icons.Default.Train,
        setOf(
            TransportMode.RAIL,
            TransportMode.HIGHSPEED_RAIL,
            TransportMode.LONG_DISTANCE,
            TransportMode.NIGHT_RAIL,
            TransportMode.REGIONAL_RAIL,
            TransportMode.SUBURBAN,
        ),
    ),
    SUBWAY("Subway", Icons.Default.Subway, setOf(TransportMode.SUBWAY)),
    FERRY("Ferry", Icons.Default.DirectionsBoat, setOf(TransportMode.FERRY)),
    OTHER(
        "Other",
        Icons.Default.Commute,
        setOf(
            TransportMode.AIRPLANE,
            TransportMode.FUNICULAR,
            TransportMode.AERIAL_LIFT,
            TransportMode.OTHER,
        ),
    );

    companion object {
        /** Category of a transit [mode]; null for street modes (walk/bike/car). */
        fun of(mode: TransportMode): TransitFilterCategory? =
            entries.firstOrNull { mode in it.modes }
    }
}
