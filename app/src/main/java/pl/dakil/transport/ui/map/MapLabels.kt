package pl.dakil.transport.ui.map

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import org.maplibre.compose.expressions.ast.Expression
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.case
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.dsl.switch
import org.maplibre.compose.expressions.value.ImageValue
import org.maplibre.compose.util.ImageResizeOptions

/**
 * How the map's labels are drawn: every name sits on a rounded background, so a label reads as an
 * object of its own rather than as more basemap type, and stops and vehicles wear different ones —
 * a stop's is a neutral bubble under its pin, a vehicle's is a badge in its line's colour beside
 * it — so the two can be told apart at a glance.
 *
 * The text is "Roboto Medium" from the style's glyph server (a fontstack it actually serves; see
 * [MapStyleRepository][pl.dakil.transport.data.repo.MapStyleRepository]), the weight the app's own
 * label text uses, rather than the Regular the basemap's street names are set in.
 */
internal val MAP_LABEL_FONT = listOf("Roboto Medium")

/** Fill, border and text of a stop label. Keyed to the basemap, not the app theme. */
internal data class StopLabelColors(val fill: Color, val border: Color, val text: Color)

internal fun stopLabelColors(darkMap: Boolean): StopLabelColors =
    if (darkMap) {
        StopLabelColors(fill = Color(0xFF2E2E2E), border = Color(0xFF4A4A4A), text = Color(0xFFEDEDED))
    } else {
        StopLabelColors(fill = Color.White, border = Color(0xFFD6D6D6), text = Color(0xFF212121))
    }

/** Text on a vehicle's badge, picked against the badge's own fill the way the app's chips are. */
internal fun vehicleLabelTextHex(fillHex: String): String =
    if (parseHex(fillHex).luminance() > 0.5f) "#000000" else "#FFFFFF"

/** The background behind every stop label on the map. */
@Composable
internal fun rememberStopLabelImage(darkMap: Boolean): Expression<ImageValue> {
    val density = LocalDensity.current
    val background = remember(density, darkMap) {
        val colors = stopLabelColors(darkMap)
        labelBackground(density, colors.fill, colors.border)
    }
    return image(background.first, resizeOptions = background.second)
}

/**
 * The badge behind every vehicle label, in the colour of that vehicle's line and ringed with the
 * same border a stop's label has, so the two read as one family. One image per colour
 * on the map, picked by the feature's own `color` — the backgrounds cannot be tinted per feature,
 * because MapLibre stretches SDF images wrongly once they are fitted to text (they scale up whole,
 * into discs hundreds of pixels across), so each colour is rasterised as an ordinary image.
 */
@Composable
internal fun rememberVehicleLabelImages(darkMap: Boolean, colorHexes: Set<String>): Expression<ImageValue> {
    val density = LocalDensity.current
    // Kept across recompositions so a colour that comes and goes is drawn once, not every poll.
    val cache = remember(density, darkMap) { mutableMapOf<String, Pair<ImageBitmap, ImageResizeOptions>>() }
    return remember(colorHexes, cache) {
        val border = stopLabelColors(darkMap).border
        fun badge(hex: String) = cache.getOrPut(hex) { labelBackground(density, parseHex(hex), border) }
            .let { (bitmap, insets) -> image(bitmap, resizeOptions = insets) }
        val cases = colorHexes.sorted().map { hex -> case(hex, badge(hex)) }
        switch(
            input = feature["color"].asString(),
            cases = *cases.toTypedArray(),
            fallback = badge(FALLBACK_BADGE_HEX),
        )
    }
}

private fun parseHex(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Gray)

/**
 * A rounded rectangle with an optional border, as a stretchable image: everything but a strip
 * through the middle keeps its size, so the corners stay round however far the label stretches,
 * and MapLibre fits the text to that same strip.
 */
private fun labelBackground(
    density: Density,
    fill: Color,
    border: Color?,
): Pair<ImageBitmap, ImageResizeOptions> {
    val radius = with(density) { LABEL_CORNER_RADIUS.toPx() }
    val borderWidth = if (border != null) with(density) { LABEL_BORDER_WIDTH.toPx() } else 0f
    val corner = ceil(radius).toInt()
    val size = 2 * corner + STRETCH_PX
    val centre = size / 2f
    val straight = size / 2f - radius
    val pixels = IntArray(size * size) { index ->
        val x = abs(index % size + 0.5f - centre) - straight
        val y = abs(index / size + 0.5f - centre) - straight
        // Signed distance to the rounded rectangle's edge, negative inside.
        val distance = hypot(max(x, 0f), max(y, 0f)) + min(max(x, y), 0f) - radius
        // Half a pixel of coverage either side of each edge, so the curve is smooth.
        val coverage = (0.5f - distance).coerceIn(0f, 1f)
        val color = if (border == null) {
            fill
        } else {
            lerp(border, fill, (-distance - borderWidth + 0.5f).coerceIn(0f, 1f))
        }
        color.copy(alpha = color.alpha * coverage).toArgb()
    }
    val bitmap = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
    val inset = with(density) { corner.toDp() }
    return bitmap to ImageResizeOptions(inset, inset, inset, inset)
}

private val LABEL_CORNER_RADIUS = 6.dp
private val LABEL_BORDER_WIDTH = 1.dp

/** The stretchable middle of the background; any width works, since it is only ever stretched. */
private const val STRETCH_PX = 2

/** A badge for a feature whose colour is somehow not among the ones rasterised. */
private const val FALLBACK_BADGE_HEX = "#78909C"
