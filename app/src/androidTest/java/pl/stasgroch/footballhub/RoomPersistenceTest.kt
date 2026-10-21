package pl.stasgroch.footballhub

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import pl.stasgroch.footballhub.data.local.*

class RoomPersistenceTest {
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
            db.close()
            db = open()
            assertEquals(2, db.userDao().entries().first().size)
            assertEquals(1, db.userDao().favorites().first().single().teamId)
            db.userDao().save(note.copy(text = "Zmieniona"))
            db.userDao().save(prediction.copy(homeScore = 3))
            assertEquals("Zmieniona", db.userDao().entries().first().first { it.id == note.id }.text)
            assertEquals(3, db.userDao().entries().first().first { it.id == prediction.id }.homeScore)
            db.userDao().delete(note.id)
            db.userDao().delete(prediction.id)
            db.userDao().removeFavorite(1)
            db.close()
            db = open()
            assertTrue(db.userDao().entries().first().isEmpty())
            assertTrue(db.userDao().favorites().first().isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }
}

