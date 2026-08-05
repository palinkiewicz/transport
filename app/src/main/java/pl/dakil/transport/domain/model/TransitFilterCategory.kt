package pl.dakil.transport.domain.model

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Commute
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Tram
import pl.dakil.transport.R

/**
 * User-facing transit category grouping the fine-grained [TransportMode]s, used wherever the
 * user filters by "kind of transport" (map filters today; potentially search/results later).
 * Street modes (walk/bike/car) deliberately have no category — they aren't transit.
 */
enum class TransitFilterCategory(
    /** Translated display name; read with `stringResource(category.labelRes)`. */
    @param:StringRes val labelRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val modes: Set<TransportMode>,
) {
    BUS(R.string.category_bus, Icons.Default.DirectionsBus, setOf(TransportMode.BUS, TransportMode.COACH)),
    TRAM(R.string.category_tram, Icons.Default.Tram, setOf(TransportMode.TRAM)),
    TRAIN(
        R.string.category_train,
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
    SUBWAY(R.string.category_subway, Icons.Default.Subway, setOf(TransportMode.SUBWAY)),
    FERRY(R.string.category_ferry, Icons.Default.DirectionsBoat, setOf(TransportMode.FERRY)),
    OTHER(
        R.string.category_other,
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
