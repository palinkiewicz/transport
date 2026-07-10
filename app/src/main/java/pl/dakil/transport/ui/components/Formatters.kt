package pl.dakil.transport.ui.components

import kotlin.math.roundToInt

/** "830 m" below a kilometer, "1.2 km" above. */
fun formatDistance(meters: Double): String =
    if (meters < 1000) "${meters.roundToInt()} m" else "%.1f km".format(meters / 1000)
