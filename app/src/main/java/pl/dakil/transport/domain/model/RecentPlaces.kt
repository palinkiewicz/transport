package pl.dakil.transport.domain.model

import kotlinx.serialization.Serializable

/**
 * The places the user has picked lately, newest first.
 *
 * Deliberately one list rather than one per picker target: a stop used as yesterday's destination
 * is just as likely to be today's start, and splitting the history by the field it happened to
 * fill would hide it from the field that needs it.
 */
@Serializable
data class RecentPlaces(val places: List<TransitLocation> = emptyList()) {

    /**
     * [location] moved to the front, trimmed to [limit].
     *
     * Re-picking a place promotes it rather than repeating it, so the list stays a history of
     * *places* rather than of taps. A [limit] of zero empties the list, which is what makes
     * turning the setting off actually forget what was stored.
     */
    fun record(location: TransitLocation, limit: Int): RecentPlaces =
        copy(
            places = (listOf(location) + places.filterNot { it.favoriteKey == location.favoriteKey })
                .take(limit.coerceAtLeast(0)),
        )

    companion object {
        val EMPTY = RecentPlaces()
    }
}
