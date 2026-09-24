package com.hcmdz.privnum.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hcmdz.privnum.data.db.MIGRATION_1_2
import com.hcmdz.privnum.data.db.PrivnumDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration1To2Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PrivnumDatabase::class.java
    )

    @Test
    fun migrate1To2MovesNumberAndKeepsSearchAlive() {
        var db = helper.createDatabase(TEST_DB, 1)
        db.execSQL(
            "INSERT INTO contacts (name, fullPhoneNumber, phoneNumber, countryCode, " +
                "appointment, location, suffix, prefix, email, notes, website, " +
                "birthday, labels, nickname, photo) VALUES " +
                "('Test', '33612345678', '612345678', 'FR', '', '', '', '', '', '', '', '', '', '', '')"
        )
        db.close()

        db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        db.query(
            "SELECT contact_id, full, national, country, is_primary FROM phone_numbers",
            emptyArray()
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("33612345678", c.getString(1))
            assertEquals("612345678", c.getString(2))
            assertEquals("FR", c.getString(3))
            assertEquals(1, c.getInt(4))
        }
        db.query(
            "SELECT rowid FROM contactsFts WHERE contactsFts MATCH 'Test*'",
            emptyArray()
        ).use { c -> assertTrue(c.moveToFirst()) }
        db.execSQL(
            "INSERT INTO contacts (name, appointment, location, suffix, prefix, email, " +
                "notes, website, birthday, labels, nickname, photo) VALUES " +
                "('Trigger', '', '', '', '', '', '', '', '', '', '', '')"
        )
        db.query(
            "SELECT rowid FROM contactsFts WHERE contactsFts MATCH 'Trigger*'",
            emptyArray()
        ).use { c -> assertTrue(c.moveToFirst()) }
        db.close()
    }

    companion object {
        private const val TEST_DB = "migration-1-2-test"
    }
}
