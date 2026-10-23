package pl.stasgroch.footballhub

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import pl.stasgroch.footballhub.data.local.*

class RoomPersistenceTest {
    @Test fun migrationPreservesExistingEntriesAndTeams() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "migration-player-favorites-test.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            old.execSQL("CREATE TABLE entries (id TEXT NOT NULL PRIMARY KEY, matchId INTEGER NOT NULL, kind TEXT NOT NULL, text TEXT NOT NULL, homeScore INTEGER, awayScore INTEGER, updatedAt INTEGER NOT NULL)")
            old.execSQL("CREATE INDEX index_entries_matchId ON entries(matchId)")
            old.execSQL("CREATE TABLE favorites (teamId INTEGER NOT NULL PRIMARY KEY)")
            old.execSQL("INSERT INTO entries VALUES ('kept', 1, 'NOTE', 'Zachowana', NULL, NULL, 1)")
            old.execSQL("INSERT INTO favorites VALUES (1)")
            old.version = 1
        }
        val db = Room.databaseBuilder(context, FootballDatabase::class.java, name).addMigrations(FootballDatabase.MIGRATION_1_2).build()
        try {
            assertEquals("Zachowana", db.userDao().entries().first().single().text)
            assertEquals(1, db.userDao().favorites().first().single().teamId)
            assertTrue(db.userDao().favoritePlayers().first().isEmpty())
            db.userDao().addFavoritePlayer(FavoritePlayerEntity(101))
            assertEquals(101, db.userDao().favoritePlayers().first().single().playerId)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun crudSurvivesDatabaseReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "persistence-test.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, FootballDatabase::class.java, name).build()
        var db = open()
        try {
            val note = EntryEntity("note-test", 1, "NOTE", "Pierwsza", null, null, 1)
            val prediction = EntryEntity("prediction-test", 1, "PREDICTION", "", 2, 1, 2)
            db.userDao().save(note)
            db.userDao().save(prediction)
            db.userDao().addFavorite(FavoriteEntity(1))
            db.userDao().addFavoritePlayer(FavoritePlayerEntity(101))
            db.close()
            db = open()
            assertEquals(101, db.userDao().favoritePlayers().first().single().playerId)
            assertEquals(2, db.userDao().entries().first().size)
            assertEquals(1, db.userDao().favorites().first().single().teamId)
            db.userDao().save(note.copy(text = "Zmieniona"))
            db.userDao().save(prediction.copy(homeScore = 3))
            assertEquals("Zmieniona", db.userDao().entries().first().first { it.id == note.id }.text)
            assertEquals(3, db.userDao().entries().first().first { it.id == prediction.id }.homeScore)
            db.userDao().delete(note.id)
            db.userDao().delete(prediction.id)
            db.userDao().removeFavorite(1)
            db.userDao().removeFavoritePlayer(101)
            db.close()
            db = open()
            assertTrue(db.userDao().favoritePlayers().first().isEmpty())
            assertTrue(db.userDao().entries().first().isEmpty())
            assertTrue(db.userDao().favorites().first().isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }
}

