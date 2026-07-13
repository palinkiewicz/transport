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

    // Array params (transitModes etc.) are passed pre-joined as "A,B,C" — the MOTIS spec
    // declares them `explode: false`, so a repeated-@Query list would be the wrong encoding.
    @GET("v6/plan")
    suspend fun plan(
        @Query("fromPlace") fromPlace: String,
        @Query("toPlace") toPlace: String,
        @Query("time") time: String? = null,
        @Query("arriveBy") arriveBy: Boolean? = null,
        @Query("maxTransfers") maxTransfers: Int? = null,
        @Query("transitModes") transitModes: String? = null,
        @Query("minTransferTime") minTransferTime: Int? = null,
        @Query("additionalTransferTime") additionalTransferTime: Int? = null,
        @Query("transferTimeFactor") transferTimeFactor: Double? = null,
        @Query("maxTravelTime") maxTravelTime: Int? = null,
        @Query("useRoutedTransfers") useRoutedTransfers: Boolean? = null,
        @Query("pedestrianProfile") pedestrianProfile: String? = null,
        @Query("pedestrianSpeed") pedestrianSpeed: Double? = null,
        @Query("cyclingSpeed") cyclingSpeed: Double? = null,
        @Query("elevationCosts") elevationCosts: String? = null,
        @Query("requireBikeTransport") requireBikeTransport: Boolean? = null,
        @Query("requireCarTransport") requireCarTransport: Boolean? = null,
        @Query("directModes") directModes: String? = null,
        @Query("preTransitModes") preTransitModes: String? = null,
        @Query("postTransitModes") postTransitModes: String? = null,
        @Query("maxDirectTime") maxDirectTime: Int? = null,
        @Query("maxPreTransitTime") maxPreTransitTime: Int? = null,
        @Query("maxPostTransitTime") maxPostTransitTime: Int? = null,
        @Query("directRentalFormFactors") directRentalFormFactors: String? = null,
        @Query("preTransitRentalFormFactors") preTransitRentalFormFactors: String? = null,
        @Query("postTransitRentalFormFactors") postTransitRentalFormFactors: String? = null,
        @Query("directRentalPropulsionTypes") directRentalPropulsionTypes: String? = null,
        @Query("preTransitRentalPropulsionTypes") preTransitRentalPropulsionTypes: String? = null,
        @Query("postTransitRentalPropulsionTypes") postTransitRentalPropulsionTypes: String? = null,
        @Query("ignoreDirectRentalReturnConstraints") ignoreDirectRentalReturnConstraints: Boolean? = null,
        @Query("ignorePreTransitRentalReturnConstraints") ignorePreTransitRentalReturnConstraints: Boolean? = null,
        @Query("ignorePostTransitRentalReturnConstraints") ignorePostTransitRentalReturnConstraints: Boolean? = null,
        @Query("searchWindow") searchWindow: Int? = null,
        @Query("numItineraries") numItineraries: Int? = null,
        @Query("slowDirect") slowDirect: Boolean? = null,
        @Query("fastestDirectFactor") fastestDirectFactor: Double? = null,
        @Query("passengers") passengers: Int? = null,
        @Query("luggage") luggage: Int? = null,
        @Query("pageCursor") pageCursor: String? = null,
    ): ResponseBody

    @GET("v6/stoptimes")
    suspend fun stoptimes(
        @Query("stopId") stopId: String? = null,
        @Query("center") center: String? = null,
        @Query("radius") radius: Int? = null,
        @Query("time") time: String? = null,
        @Query("arriveBy") arriveBy: Boolean? = null,
        @Query("mode") mode: String? = null,
        @Query("n") n: Int? = null,
        @Query("pageCursor") pageCursor: String? = null,
    ): ResponseBody

    @GET("v6/trip")
    suspend fun trip(
        @Query("tripId") tripId: String,
        @Query("detailedLegs") detailedLegs: Boolean? = null,
    ): ResponseBody

    @GET("v6/map/stops")
    suspend fun mapStops(
        @Query("min") min: String,
        @Query("max") max: String,
        @Query("grouped") grouped: Boolean? = null,
    ): ResponseBody

    @GET("v6/map/trips")
    suspend fun mapTrips(
        @Query("min") min: String,
        @Query("max") max: String,
        @Query("zoom") zoom: Double,
        @Query("startTime") startTime: String,
        @Query("endTime") endTime: String,
        @Query("precision") precision: Int? = null,
    ): ResponseBody
}
