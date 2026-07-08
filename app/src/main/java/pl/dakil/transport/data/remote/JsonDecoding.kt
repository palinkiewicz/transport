package pl.dakil.transport.data.remote

import kotlinx.serialization.json.Json
import okhttp3.ResponseBody

inline fun <reified T> Json.decode(body: ResponseBody): T =
    body.use { decodeFromString(it.string()) }
