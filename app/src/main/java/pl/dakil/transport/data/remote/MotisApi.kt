package pl.dakil.transport.data.remote

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Raw MOTIS endpoints. Responses are returned as [ResponseBody] and decoded manually
 * with kotlinx.serialization (see [MotisApi.decode]) to avoid a Retrofit converter dependency.
 */
interface MotisApi {

    @GET("v1/geocode")
    suspend fun geocode(
        @Query("text") text: String,
        @Query("place") place: String? = null,
        @Query("numResults") numResults: Int? = null,
    ): ResponseBody

    @GET("v6/plan")
    suspend fun plan(
        @Query("fromPlace") fromPlace: String,
        @Query("toPlace") toPlace: String,
        @Query("time") time: String? = null,
        @Query("maxTransfers") maxTransfers: Int? = null,
        @Query("pageCursor") pageCursor: String? = null,
    ): ResponseBody

    @GET("v6/stoptimes")
    suspend fun stoptimes(
        @Query("stopId") stopId: String? = null,
        @Query("center") center: String? = null,
        @Query("radius") radius: Int? = null,
        @Query("time") time: String? = null,
        @Query("arriveBy") arriveBy: Boolean? = null,
        @Query("n") n: Int? = null,
        @Query("pageCursor") pageCursor: String? = null,
    ): ResponseBody

    @GET("v6/map/stops")
    suspend fun mapStops(
        @Query("min") min: String,
        @Query("max") max: String,
    ): ResponseBody
}
