package pl.stasgroch.footballhub.data.repository

import pl.stasgroch.footballhub.data.model.*

/** Shared resolver for a backend mapper: names are never used as identity keys.
 * The backend supplies and persists stable internal IDs; unknown external IDs stay unresolved. */
class PlayerIdentityMap(players: List<Player>) {
    private val ids = players.mapNotNull { player -> player.externalId?.let { it to player.id } }
    init {
        require(players.map { it.id }.distinct().size == players.size) { "Duplicate internal player ID" }
        require(ids.map { it.first }.distinct().size == ids.size) { "Duplicate external player ID" }
    }
    private val byExternal = ids.toMap()
    fun resolve(reference: ExternalId): Int? = byExternal[reference]
}
