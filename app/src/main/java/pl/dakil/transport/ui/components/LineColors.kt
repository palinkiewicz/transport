package pl.dakil.transport.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import kotlin.math.sqrt
import pl.dakil.transport.domain.model.LineColorMode
import pl.dakil.transport.domain.model.LinePalette
import pl.dakil.transport.domain.model.TransportMode

/**
 * Turning the feed's route colours into what the list screens actually draw.
 *
 * Operators routinely publish one house colour for their whole network, or none at all, so
 * colouring every badge straight from the feed can leave a whole departures board in one shade.
 * [LineColorMode] lets the user replace or repair that with their own palette; this file is the
 * single place that decision is made. The map is deliberately not a client: markers and overlays
 * have no "next line" order to hand a palette out along.
 */

/** The line-colour preference as the UI needs it: a mode plus the palette already parsed. */
@Immutable
data class LineColorSettings(
    val mode: LineColorMode = LineColorMode.TRANSITOUS,
    val palette: List<Color> = LinePalette.DEFAULT.map { parseRouteColor(it, Color.Gray) },
) {
    companion object {
        val DEFAULT = LineColorSettings()
    }
}

/**
 * Set once around the nav host. Defaults to [LineColorMode.TRANSITOUS], so anything not wrapped —
 * previews, the map — behaves exactly as it did before this setting existed.
 */
val LocalLineColorSettings = staticCompositionLocalOf { LineColorSettings.DEFAULT }

/** One line as a screen draws it, in draw order. */
data class LineColorRequest(
    /** Line identity, from [lineColorKey]. */
    val key: String,
    /** The feed's GTFS `RRGGBB` route colour, if it published one. */
    val serverHex: String?,
    /** What to draw when there is no usable server colour — normally the mode's colour. */
    val fallback: Color,
)

/**
 * Colour per line for one screen, keyed by [lineColorKey]. Resolved once per composition rather
 * than per row so [LineColorMode.AUTO] can see each line's neighbours.
 */
@Immutable
class LineColorMap internal constructor(private val byKey: Map<String, Color>) {
    fun of(key: String, fallback: Color): Color = byKey[key] ?: fallback
}

/**
 * Identity a colour is assigned to. Deliberately coarser than `FavoriteLine.key`: that one
 * separates a line's two directions, which for colouring would mean drawing the same line in two
 * colours on one board.
 */
fun lineColorKey(mode: TransportMode, label: String): String = "${mode.name}|$label"

/**
 * Resolves colours for every line a screen draws.
 *
 * [groups] is the draw order, split wherever adjacency stops meaning anything — one group per
 * journey card on the results list, one flat group for a departures board. [LineColorMode.AUTO]
 * never compares across a group boundary.
 *
 * Assignments are sticky: a line keeps the palette index it got when it was first seen, so an
 * auto-refresh that reorders or drops rows doesn't reshuffle the board's colours. Changing the
 * setting (or editing a swatch) drops the state and starts over, which is what the user is asking
 * for when they change it.
 */
@Composable
fun rememberLineColorGroups(groups: List<List<LineColorRequest>>): LineColorMap {
    val settings = LocalLineColorSettings.current
    val assignments = remember(settings) { LineColorAssignments(settings) }
    return remember(assignments, groups) { assignments.resolve(groups) }
}

/** [rememberLineColorGroups] for a screen that draws one continuous sequence of lines. */
@Composable
fun rememberLineColors(requests: List<LineColorRequest>): LineColorMap =
    rememberLineColorGroups(listOf(requests))

/**
 * CIE76 ΔE below which two badges read as the same line. ~2.3 is the just-noticeable difference
 * and ~5 is "distinguishable side by side", but two small chips separated by a row of text need
 * considerably more than that before anyone reads them as different colours.
 */
private const val SIMILAR_DELTA_E = 12.0

/**
 * How far apart a *substitute* has to be. Clearing [SIMILAR_DELTA_E] is enough to call two colours
 * different, but not enough to be worth replacing one with the other: a palette red swapped in for
 * an operator red still reads as one line twice. Once we have decided to displace a line, the
 * replacement has to be obviously another colour.
 */
private const val DISTINCT_DELTA_E = 25.0

/**
 * The sticky part of [rememberLineColors], kept out of composition so the walk is a plain loop.
 * Not thread-safe; only ever touched from the composition it belongs to.
 */
internal class LineColorAssignments(private val settings: LineColorSettings) {
    private val indexByKey = mutableMapOf<String, Int>()

    /**
     * Keys [LineColorMode.AUTO] has already displaced. Without this a line would flip between its
     * operator colour and a palette one as the rows above it change between refreshes.
     */
    private val autoOverrides = mutableSetOf<String>()

    fun resolve(groups: List<List<LineColorRequest>>): LineColorMap {
        val resolved = mutableMapOf<String, Color>()
        for (group in groups) {
            var previous: Color? = null
            for (request in group) {
                val color = resolved[request.key] ?: resolve(request, previous)
                resolved[request.key] = color
                previous = color
            }
        }
        return LineColorMap(resolved)
    }

    private fun resolve(request: LineColorRequest, previous: Color?): Color {
        val index = indexByKey.getOrPut(request.key) { indexByKey.size }
        val paletteColor = settings.palette[index % settings.palette.size]
        return when (settings.mode) {
            LineColorMode.TRANSITOUS -> parseRouteColor(request.serverHex, request.fallback)
            LineColorMode.CUSTOM -> paletteColor
            LineColorMode.AUTO -> {
                val candidate = if (request.key in autoOverrides) {
                    paletteColorUnlike(previous, index)
                } else {
                    parseRouteColor(request.serverHex, request.fallback)
                }
                if (previous == null || !tooSimilar(candidate, previous)) return candidate
                autoOverrides += request.key
                paletteColorUnlike(previous, index)
            }
        }
    }

    /**
     * The line's own palette colour, or — when that is not clearly different from the neighbour —
     * the next one along that is. Scanning from the line's own index rather than jumping straight
     * to the most distant colour keeps the palette being handed out roughly in order.
     *
     * With nothing clearing [DISTINCT_DELTA_E] the most distant entry wins anyway: six colours
     * can't always beat an arbitrary feed colour, and the furthest one is the best on offer.
     */
    private fun paletteColorUnlike(previous: Color?, index: Int): Color {
        val palette = settings.palette
        val ordered = palette.indices.map { palette[(index + it) % palette.size] }
        if (previous == null) return ordered.first()
        return ordered.firstOrNull { deltaE(it, previous) >= DISTINCT_DELTA_E }
            ?: ordered.maxBy { deltaE(it, previous) }
    }
}

/** Whether two colours are close enough to read as one line. */
private fun tooSimilar(a: Color, b: Color): Boolean = deltaE(a, b) < SIMILAR_DELTA_E

/** CIE76 colour difference in CIELAB. */
private fun deltaE(a: Color, b: Color): Double {
    val labA = DoubleArray(3)
    val labB = DoubleArray(3)
    ColorUtils.colorToLAB(a.toArgb(), labA)
    ColorUtils.colorToLAB(b.toArgb(), labB)
    val dl = labA[0] - labB[0]
    val da = labA[1] - labB[1]
    val db = labA[2] - labB[2]
    return sqrt(dl * dl + da * da + db * db)
}
