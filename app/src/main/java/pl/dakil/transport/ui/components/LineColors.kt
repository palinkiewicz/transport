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

/**
 * Turning the feed's route colours into what the list screens actually draw.
 *
 * Operators routinely publish one house colour for their whole network, or none at all, so
 * colouring every badge straight from the feed can leave a whole departures board in one shade.
 * [LineColorMode] lets the user replace or repair that with their own palette; this file is the
 * single place that decision is made. The map is deliberately not a client: markers and overlays
 * have no draw order to hand a palette out along.
 *
 * The palette is handed out **by position, not by line**: the first badge in a run takes colour
 * one, the second colour two, and so on, wrapping after six. Nothing is remembered — no lookup
 * from a line to a colour, no state carried across refreshes — so what a screen draws is always a
 * plain function of the rows it is drawing right now.
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

/** One line badge a screen is about to draw, in draw order. */
data class LineColorRequest(
    /** The feed's GTFS `RRGGBB` route colour, if it published one. */
    val serverHex: String?,
    /** What to draw when there is no usable server colour — normally the mode's colour. */
    val fallback: Color,
    /**
     * Set only where one screen draws the same line more than once — a departures board lists
     * every departure, so line 127 can be four of its rows. Badges sharing a key share a colour
     * and take one slot between them; the rest of the board carries on from there. Left null the
     * counter simply advances once per badge.
     */
    val key: String? = null,
)

/** The colours a screen's badges take, in the order they were requested. */
@Immutable
class LineColors internal constructor(private val colors: List<Color>) {
    /** Colour for the [index]-th badge on the screen, counting from zero in draw order. */
    fun at(index: Int, fallback: Color): Color = colors.getOrElse(index) { fallback }
}

/**
 * Resolves colours for every badge a screen draws.
 *
 * [groups] is the draw order, split wherever one run of badges stops being a continuation of the
 * last — one group per journey card on the results list, one flat group for a departures board.
 * Every group starts again at the first palette colour, and [LineColorMode.AUTO] never compares a
 * badge with one on the other side of a boundary.
 */
@Composable
fun rememberLineColorGroups(groups: List<List<LineColorRequest>>): LineColors {
    val settings = LocalLineColorSettings.current
    return remember(settings, groups) { resolve(settings, groups) }
}

/** [rememberLineColorGroups] for a screen that draws one continuous sequence of badges. */
@Composable
fun rememberLineColors(requests: List<LineColorRequest>): LineColors =
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
 * an operator red still reads as one line twice. Once we have decided to displace a badge, the
 * replacement has to be obviously another colour.
 */
private const val DISTINCT_DELTA_E = 25.0

private fun resolve(settings: LineColorSettings, groups: List<List<LineColorRequest>>): LineColors {
    val colors = mutableListOf<Color>()
    for (group in groups) {
        var index = 0
        var previous: Color? = null
        for (request in group) {
            val color = when (settings.mode) {
                LineColorMode.TRANSITOUS -> parseRouteColor(request.serverHex, request.fallback)
                LineColorMode.CUSTOM -> settings.palette[index % settings.palette.size]
                LineColorMode.AUTO -> {
                    val server = parseRouteColor(request.serverHex, request.fallback)
                    if (previous != null && tooSimilar(server, previous)) {
                        settings.paletteColorUnlike(previous, index)
                    } else {
                        server
                    }
                }
            }
            colors += color
            previous = color
            index++
        }
    }
    return LineColors(colors)
}

/**
 * The badge's own palette colour, or — when that is not clearly different from the neighbour — the
 * next one along that is. Scanning from the badge's own position rather than jumping straight to
 * the most distant colour keeps the palette being handed out roughly in order.
 *
 * With nothing clearing [DISTINCT_DELTA_E] the most distant entry wins anyway: six colours can't
 * always beat an arbitrary feed colour, and the furthest one is the best on offer.
 */
private fun LineColorSettings.paletteColorUnlike(previous: Color, index: Int): Color {
    val ordered = palette.indices.map { palette[(index + it) % palette.size] }
    return ordered.firstOrNull { deltaE(it, previous) >= DISTINCT_DELTA_E }
        ?: ordered.maxBy { deltaE(it, previous) }
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
