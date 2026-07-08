package pl.dakil.transport.data.repo

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
import okhttp3.OkHttpClient
import okhttp3.Request

const val LIBERTY_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

// Liberty draws transit stop icons not only in `poi_transit` (which the map screen replaces with
// its own stop layers) but also through these rank-based POI layers at zoom >= 15.
private val RANKED_POI_LAYER_IDS = setOf("poi_r1", "poi_r7", "poi_r20")

// Class list copied from Liberty's own `poi_transit` filter.
private val TRANSIT_POI_CLASSES = listOf("airport", "bus", "rail")

@Singleton
class MapStyleRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {

    /**
     * Downloads the Liberty base style and patches its rank-based POI layers so they no longer
     * render transit stops; the app draws its own stop markers instead.
     */
    suspend fun transitFreeLibertyStyle(): Result<String> = runCatching {
        val body = withContext(Dispatchers.IO) {
            okHttpClient.newCall(Request.Builder().url(LIBERTY_STYLE_URL).build()).execute().use { response ->
                check(response.isSuccessful) { "Style request failed: HTTP ${response.code}" }
                checkNotNull(response.body).string()
            }
        }
        val style = json.parseToJsonElement(body).jsonObject
        val layers = style.getValue("layers").jsonArray.map { layer ->
            val obj = layer.jsonObject
            if (obj["id"]?.jsonPrimitive?.contentOrNull in RANKED_POI_LAYER_IDS) {
                JsonObject(obj + ("filter" to excludeTransitClasses(obj.getValue("filter"))))
            } else {
                obj
            }
        }
        json.encodeToString(JsonObject.serializer(), JsonObject(style + ("layers" to JsonArray(layers))))
    }

    private fun excludeTransitClasses(filter: JsonElement): JsonElement = buildJsonArray {
        add(JsonPrimitive("all"))
        add(filter)
        add(
            buildJsonArray {
                add(JsonPrimitive("!"))
                add(
                    buildJsonArray {
                        add(JsonPrimitive("match"))
                        add(buildJsonArray { add(JsonPrimitive("get")); add(JsonPrimitive("class")) })
                        add(JsonArray(TRANSIT_POI_CLASSES.map(::JsonPrimitive)))
                        add(JsonPrimitive(true))
                        add(JsonPrimitive(false))
                    },
                )
            },
        )
    }
}
