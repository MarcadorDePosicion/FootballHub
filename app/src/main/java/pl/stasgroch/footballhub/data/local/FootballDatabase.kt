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

@Entity(tableName = "favorite_players")
data class FavoritePlayerEntity(@PrimaryKey val playerId: Int)

@Dao
interface UserDao {
    @Query("SELECT * FROM entries ORDER BY updatedAt DESC")
    fun entries(): Flow<List<EntryEntity>>
    @Query("SELECT * FROM favorites")
    fun favorites(): Flow<List<FavoriteEntity>>
    @Query("SELECT * FROM favorite_players")
    fun favoritePlayers(): Flow<List<FavoritePlayerEntity>>
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addFavoritePlayer(favorite: FavoritePlayerEntity)
    @Query("DELETE FROM favorite_players WHERE playerId = :id")
    suspend fun removeFavoritePlayer(id: Int)
    @Upsert suspend fun save(entry: EntryEntity)
    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun delete(id: String)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addFavorite(favorite: FavoriteEntity)
    @Query("DELETE FROM favorites WHERE teamId = :id")
    suspend fun removeFavorite(id: Int)
}
@Database(entities = [EntryEntity::class, FavoriteEntity::class, FavoritePlayerEntity::class], version = 2, exportSchema = true)
abstract class FootballDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `favorite_players` (`playerId` INTEGER NOT NULL, PRIMARY KEY(`playerId`))")
            }
        }
        @Volatile private var instance: FootballDatabase? = null
        fun get(context: Context): FootballDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, FootballDatabase::class.java, "football-hub.db")
                .addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}

