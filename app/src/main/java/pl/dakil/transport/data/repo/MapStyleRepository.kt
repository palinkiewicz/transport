package pl.dakil.transport.data.repo

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Custom Google-Maps-like restyle of OSM Liberty, exported from Maputnik. Re-exports can be
// dropped in as-is: everything environment-specific is patched at load time below.
private const val STYLE_ASSET = "gmaps_style.json"

// The Maputnik export points its vector source at MapTiler with a placeholder demo key; the app
// uses OpenFreeMap's tiles instead (same OpenMapTiles schema, no key needed).
private const val OPENMAPTILES_SOURCE = "openmaptiles"
private const val OPENMAPTILES_TILEJSON_URL = "https://tiles.openfreemap.org/planet"

// These rank-based POI layers would draw base transit stop icons underneath the app's own stop
// markers; their filters get a class exclusion added. Superset of the class names OpenMapTiles
// uses for transit POIs — excluding a name that never occurs is harmless.
private val POI_LAYER_IDS = setOf("poi_z15", "poi_z16")
private val TRANSIT_POI_CLASSES =
    listOf("airport", "bus", "rail", "railway", "harbor", "aerialway", "ferry_terminal")

// The style's glyph server (orangemug's font-glyphs) 404s on the bare "Roboto Condensed"
// fontstack; those labels would silently never render.
private const val MISSING_FONT = "Roboto Condensed"
private const val REPLACEMENT_FONT = "Roboto Condensed Regular"

@Singleton
class MapStyleRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) {

    /**
     * Loads the bundled map style and patches it for use in the app: vector tiles repointed to
     * OpenFreeMap, transit stops removed from the POI layers (the app draws its own stop
     * markers), and unavailable fontstacks replaced.
     */
    suspend fun transitFreeGmapsStyle(): String = withContext(Dispatchers.IO) {
        val body = context.assets.open(STYLE_ASSET).bufferedReader().use { it.readText() }
        val style = json.parseToJsonElement(body).jsonObject
        val layers = style.getValue("layers").jsonArray.map { layer -> patchLayer(layer.jsonObject) }
        val patched = JsonObject(
            style +
                ("sources" to patchSources(style.getValue("sources").jsonObject)) +
                ("layers" to JsonArray(layers)),
        )
        json.encodeToString(JsonObject.serializer(), patched)
    }

    private fun patchSources(sources: JsonObject): JsonObject {
        val openmaptiles = sources.getValue(OPENMAPTILES_SOURCE).jsonObject
        return JsonObject(
            sources +
                (OPENMAPTILES_SOURCE to JsonObject(openmaptiles + ("url" to JsonPrimitive(OPENMAPTILES_TILEJSON_URL)))),
        )
    }

    private fun patchLayer(layer: JsonObject): JsonObject {
        var patched = layer
        if (patched["id"]?.jsonPrimitive?.contentOrNull in POI_LAYER_IDS) {
            patched = JsonObject(patched + ("filter" to excludeTransitClasses(patched.getValue("filter"))))
        }
        patched["layout"]?.jsonObject?.get("text-font")?.jsonArray?.let { fonts ->
            if (fonts.any { it.jsonPrimitive.contentOrNull == MISSING_FONT }) {
                val fixedFonts = JsonArray(
                    fonts.map { font ->
                        if (font.jsonPrimitive.contentOrNull == MISSING_FONT) JsonPrimitive(REPLACEMENT_FONT) else font
                    },
                )
                val layout = JsonObject(patched.getValue("layout").jsonObject + ("text-font" to fixedFonts))
                patched = JsonObject(patched + ("layout" to layout))
            }
        }
        return patched
    }

    // The style uses legacy filter syntax, so the exclusion is a legacy "!in" combined via "all".
    private fun excludeTransitClasses(filter: JsonElement): JsonElement = buildJsonArray {
        add(JsonPrimitive("all"))
        add(filter)
        add(
            buildJsonArray {
                add(JsonPrimitive("!in"))
                add(JsonPrimitive("class"))
                TRANSIT_POI_CLASSES.forEach { add(JsonPrimitive(it)) }
            },
        )
    }
}
