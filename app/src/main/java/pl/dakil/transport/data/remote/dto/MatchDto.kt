package pl.dakil.transport.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class MatchDto(
    val type: String,
    val name: String,
    val id: String,
    val lat: Double,
    val lon: Double,
    val level: Double? = null,
    val street: String? = null,
    val houseNumber: String? = null,
    val country: String? = null,
    val zip: String? = null,
    val tz: String? = null,
    val score: Double? = null,
    val modes: List<String>? = null,
    val importance: Double? = null,
)
