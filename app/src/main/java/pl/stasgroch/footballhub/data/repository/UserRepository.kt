package pl.stasgroch.footballhub.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pl.stasgroch.footballhub.data.local.*
import pl.stasgroch.footballhub.data.model.JournalEntry

interface UserRepository {
    val entries: Flow<List<JournalEntry>>
    val favorites: Flow<Set<Int>>
    suspend fun save(entry: JournalEntry)
    suspend fun delete(id: String)
    suspend fun favorite(teamId: Int, enabled: Boolean)
}
class RoomUserRepository(private val dao: UserDao) : UserRepository {
    override val entries = dao.entries().map { rows ->
        rows.map { JournalEntry(it.id, it.matchId, it.kind, it.text, it.homeScore, it.awayScore, it.updatedAt) }
    }
    override val favorites = dao.favorites().map { rows -> rows.map { it.teamId }.toSet() }
    override suspend fun save(entry: JournalEntry) = dao.save(
        EntryEntity(entry.id, entry.matchId, entry.kind, entry.text, entry.homeScore, entry.awayScore, entry.updatedAt)
    )
    override suspend fun delete(id: String) = dao.delete(id)
    override suspend fun favorite(teamId: Int, enabled: Boolean) {
        if (enabled) dao.addFavorite(FavoriteEntity(teamId)) else dao.removeFavorite(teamId)
    }
}

