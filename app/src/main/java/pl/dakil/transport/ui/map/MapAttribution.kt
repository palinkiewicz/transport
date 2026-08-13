package pl.dakil.transport.ui.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import org.maplibre.compose.camera.CameraState
import org.maplibre.compose.material3.AttributionButtonStyle
import org.maplibre.compose.material3.AttributionLinks
import org.maplibre.compose.material3.ExpandingAttributionButton
import org.maplibre.compose.style.StyleState
import pl.dakil.transport.R

/** Where the Transitous usage guidelines require the app to link its data sources. */
private const val TRANSITOUS_SOURCES_URL = "https://transitous.org/sources/"

/** The domain is the link text and is not translated. */
private const val TRANSITOUS_HOST = "transitous.org"

private const val ANCHOR_CLOSE = "</a>"

/**
 * Splits one source's attribution HTML into the pieces a flow layout is allowed to wrap between.
 *
 * A source hands over its whole credit as a single string — OpenFreeMap's is
 * `<a>OpenFreeMap</a> <a>© OpenMapTiles</a> Data from <a>OpenStreetMap</a>` — and rendering that
 * as one run means the line can break anywhere in it, so "Data from" ends up stranded on the row
 * above "OpenStreetMap". Cutting after each `</a>` gives one chunk per credit, with any plain
 * text preceding a link ("Data from") kept together with the link it introduces, because that
 * text falls at the start of the following chunk. A credit with no links at all stays whole.
 */
private fun splitAttribution(html: String): List<String> {
    val chunks = mutableListOf<String>()
    var start = 0
    while (true) {
        val end = html.indexOf(ANCHOR_CLOSE, start)
        if (end < 0) break
        chunks += html.substring(start, end + ANCHOR_CLOSE.length)
        start = end + ANCHOR_CLOSE.length
    }
    chunks += html.substring(start)
    return chunks.map(String::trim).filter(String::isNotEmpty)
}

/**
 * The map's single attribution control: the basemap's own credits and the Transitous data-source
 * link in one expanding row, rather than two separate things in opposite corners.
 *
 * Merging them works because the library's expanded content is handed the sources' raw
 * attribution HTML and nothing else — appending one more anchor puts the Transitous link through
 * the same renderer, the same link styling and the same flow layout as OpenFreeMap's and
 * OpenStreetMap's, so it reads as one credit line instead of a widget plus a floating label.
 *
 * Two constraints shape what can be done here. The basemap credit is not optional: OSM data is
 * ODbL and OpenFreeMap's terms require it, so this control can be restyled but not removed. And
 * the widget is deliberately left **expanded on entry** (the library collapses it on the first
 * map gesture): the Transitous guidelines ask for a *visible* link, which a permanently collapsed
 * icon would not be.
 *
 * Colours key off [darkMap] rather than `MaterialTheme`, for the same reason the stop labels do —
 * what this sits on is the basemap, not an app surface. See [mapLabelColor].
 */
@Composable
fun MapAttributionButton(
    cameraState: CameraState,
    styleState: StyleState,
    darkMap: Boolean,
    modifier: Modifier = Modifier,
) {
    // Only the domain is the link; the words introducing it are plain text, so the credit reads
    // as a sentence rather than as one long underlined run.
    val transitousHtml = stringResource(
        R.string.map_attribution,
        "<a href=\"$TRANSITOUS_SOURCES_URL\">$TRANSITOUS_HOST</a>",
    )
    val contentColor = mapLabelColor(darkMap)
    // Translucent so the map still reads through the pill, and dark-on-light / light-on-dark
    // regardless of what the app theme is doing.
    val containerColor = if (darkMap) Color(0xE6141414) else Color(0xE6FFFFFF)
    val linkStyles = TextLinkStyles(
        SpanStyle(
            color = if (darkMap) Color(0xFF8AB4F8) else Color(0xFF1A56C4),
            textDecoration = TextDecoration.Underline,
        ),
    )

    ExpandingAttributionButton(
        cameraState = cameraState,
        styleState = styleState,
        modifier = modifier,
        contentAlignment = Alignment.BottomStart,
        // A plain clickable Box rather than IconButton: IconButton enforces a 48dp layout size,
        // which is a lot of furniture for a secondary control sitting on top of the map.
        toggleButton = { onClick ->
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.map_attribution_sources),
                    tint = contentColor,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
        expandedContent = { attributions ->
            val credits = remember(attributions, transitousHtml) {
                (attributions.flatMap(::splitAttribution) + transitousHtml).distinct()
            }
            ProvideTextStyle(MaterialTheme.typography.labelSmall) {
                AttributionLinks(
                    attributions = credits,
                    linkStyles = linkStyles,
                    spacing = 6.dp,
                    // Left false deliberately: each entry is now one credit, and holding it to a
                    // single line is the whole point of splitting them. The flow layout wraps
                    // between credits instead, which is where a break belongs.
                    breakWithinAttribution = false,
                )
            }
        },
        expandedStyle = AttributionButtonStyle(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        // Collapsed is just the icon on bare map; the toggle button tints itself.
        collapsedStyle = AttributionButtonStyle(
            containerColor = Color.Transparent,
            contentColor = contentColor,
        ),
    )
}
