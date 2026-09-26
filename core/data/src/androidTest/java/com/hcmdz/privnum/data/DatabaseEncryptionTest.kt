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
 *
 * The empty-database case is the one that used to crash: with no row to write,
 * Room never materialised the target file, the conversion reported success and
 * Room then opened the untouched plaintext file with a key.
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

    private fun openPlaintext() = Room.databaseBuilder(
        context,
        PrivnumDatabase::class.java,
        "privnum.db"
    )
        .addMigrations(MIGRATION_1_2)
        .allowMainThreadQueries()
        .build()

    @Test
    fun plaintextDatabaseIsEncryptedOnFirstOpenAndKeepsItsRows() {
        val stored = contact(full = "33612345678", national = "612345678", name = "Crypted")
        val plain = openPlaintext()
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
    fun existingButEmptyDatabaseIsEncryptedInsteadOfLeftInTheClear() {
        // A file that exists and holds the schema but no row: reachable by
        // inserting then deleting, since Room only writes the file on a write.
        val plain = openPlaintext()
        runBlocking {
            val id = plain.contactDao()
                .insertContact(contact("33600000000", "600000000", "Doomed").toEntity())
            plain.contactDao().insertNumbers(
                contact("33600000000", "600000000", "Doomed").toNumberEntities(id)
            )
            plain.contactDao().clearNumbers()
            plain.contactDao().clearContacts()
        }
        plain.close()
        assertTrue("the plaintext file must exist before the conversion", databaseFile().length() > 0L)
        assertEquals(PLAIN_HEADER, header())

        val repository = ContactRepository(context, ContactPhotoStore(context))
        assertTrue(
            "an empty source must convert to an empty database, not crash",
            runBlocking { repository.getAll() }.isEmpty()
        )
        assertFalse(
            "database must no longer be a plain SQLite file",
            header() == PLAIN_HEADER
        )
    }

    @Test
    fun fullTextSearchStillUpdatesInAConvertedDatabase() {
        val plain = openPlaintext()
        runBlocking {
            val seed = contact("33611111111", "611111111", "Seed")
            val id = plain.contactDao().insertContact(seed.toEntity().copy(id = 0))
            plain.contactDao().insertNumbers(seed.toNumberEntities(id))
        }
        plain.close()

        val repository = ContactRepository(context, ContactPhotoStore(context))
        assertEquals(
            "rows present before the conversion must be searchable",
            1,
            runBlocking { repository.search("Seed") }.size
        )

        // Written after the conversion: proves the FTS triggers are still live
        // in the rewritten file, not just populated by the copy.
        runBlocking {
            repository.add(contact("33622222222", "622222222", "Grown"))
        }
        assertEquals(
            "a contact added after the conversion must be indexed",
            1,
            runBlocking { repository.search("Grown") }.size
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
