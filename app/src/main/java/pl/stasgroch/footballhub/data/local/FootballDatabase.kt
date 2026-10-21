package pl.stasgroch.footballhub.data.local

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "entries", indices = [Index("matchId")])
data class EntryEntity(
    @PrimaryKey val id: String,
    val matchId: Int,
    val kind: String,
    val text: String,
    val homeScore: Int?,
    val awayScore: Int?,
    val updatedAt: Long
)
@Entity(tableName = "favorites")
data class FavoriteEntity(@PrimaryKey val teamId: Int)

@Dao
interface UserDao {
    @Query("SELECT * FROM entries ORDER BY updatedAt DESC")
    fun entries(): Flow<List<EntryEntity>>
    @Query("SELECT * FROM favorites")
    fun favorites(): Flow<List<FavoriteEntity>>
    @Upsert suspend fun save(entry: EntryEntity)
    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun delete(id: String)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addFavorite(favorite: FavoriteEntity)
    @Query("DELETE FROM favorites WHERE teamId = :id")
    suspend fun removeFavorite(id: Int)
}
@Database(entities = [EntryEntity::class, FavoriteEntity::class], version = 1, exportSchema = true)
abstract class FootballDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    companion object {
        @Volatile private var instance: FootballDatabase? = null
        fun get(context: Context): FootballDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, FootballDatabase::class.java, "football-hub.db")
                .build().also { instance = it }
        }
    }
}

