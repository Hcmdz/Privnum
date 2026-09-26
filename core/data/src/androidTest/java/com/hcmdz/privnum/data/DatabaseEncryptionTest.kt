package com.hcmdz.privnum.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hcmdz.privnum.data.db.MIGRATION_1_2
import com.hcmdz.privnum.data.db.PrivnumDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

private const val PLAIN_HEADER = "SQLite format 3"

private fun contact(full: String, national: String, name: String) = Contact(
    name = name,
    numbers = listOf(PhoneNumberRef(full, national, "FR", primary = true))
)

/**
 * The contact database is the app's whole value, so it is encrypted at rest.
 * These tests cover the transition: a database written before encryption
 * existed must be converted, keep every row, and stop being a plain SQLite
 * file. The schema is not touched by the conversion, so there is no migration
 * to assert here - only the file rewrite.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseEncryptionTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun databaseFile() = File(context.getDatabasePath("privnum.db").path)

    private fun header() = String(
        databaseFile().readBytes().copyOfRange(0, PLAIN_HEADER.length),
        Charsets.ISO_8859_1
    )

    @Before
    fun clearDatabase() {
        context.deleteDatabase("privnum.db")
    }

    @Test
    fun plaintextDatabaseIsEncryptedOnFirstOpenAndKeepsItsRows() {
        val stored = contact(full = "33612345678", national = "612345678", name = "Crypted")
        val plain = Room.databaseBuilder(context, PrivnumDatabase::class.java, "privnum.db")
            .addMigrations(MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        runBlocking {
            val id = plain.contactDao().insertContact(stored.toEntity().copy(id = 0))
            plain.contactDao().insertNumbers(stored.toNumberEntities(id))
        }
        plain.close()
        assertEquals(PLAIN_HEADER, header())

        val repository = ContactRepository(context, ContactPhotoStore(context))
        val found = runBlocking { repository.findByNumber("33612345678") }
        assertNotNull("rows must survive the conversion", found)
        assertEquals("Crypted", found?.name)
        assertFalse(
            "database must no longer be a plain SQLite file",
            header() == PLAIN_HEADER
        )
    }

    @Test
    fun reopeningAnEncryptedDatabaseDoesNotConvertItAgain() {
        val repository = ContactRepository(context, ContactPhotoStore(context))
        runBlocking { repository.add(contact(full = "33612345679", national = "612345679", name = "Second")) }
        val afterFirstOpen = databaseFile().readBytes()

        val reopened = ContactRepository(context, ContactPhotoStore(context))
        assertEquals(
            "Second",
            runBlocking { reopened.findByNumber("33612345679") }?.name
        )
        assertTrue(
            "a second open must leave the file alone",
            databaseFile().readBytes().contentEquals(afterFirstOpen)
        )
    }
}
