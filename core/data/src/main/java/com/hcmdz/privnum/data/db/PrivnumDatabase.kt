package com.hcmdz.privnum.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ContactEntity::class, PhoneNumberEntity::class, ContactFts::class],
    version = 2,
    exportSchema = true
)
abstract class PrivnumDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Exact Room-generated DDL (see PrivnumDatabase_Impl.createAllTables).
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `phone_numbers` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`contact_id` INTEGER NOT NULL, `full` TEXT NOT NULL, " +
                "`national` TEXT NOT NULL, `country` TEXT NOT NULL, " +
                "`is_primary` INTEGER NOT NULL, " +
                "FOREIGN KEY(`contact_id`) REFERENCES `contacts`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_phone_numbers_contact_id` " +
                "ON `phone_numbers` (`contact_id`)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_phone_numbers_full` " +
                "ON `phone_numbers` (`full`)"
        )
        db.execSQL(
            "INSERT INTO phone_numbers (contact_id, full, national, country, is_primary) " +
                "SELECT id, fullPhoneNumber, phoneNumber, countryCode, 1 FROM contacts"
        )
        db.execSQL(
            "CREATE TABLE contacts_new (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `appointment` TEXT NOT NULL, " +
                "`location` TEXT NOT NULL, `suffix` TEXT NOT NULL, " +
                "`prefix` TEXT NOT NULL, `email` TEXT NOT NULL, " +
                "`notes` TEXT NOT NULL, `website` TEXT NOT NULL, " +
                "`birthday` TEXT NOT NULL, `labels` TEXT NOT NULL, " +
                "`nickname` TEXT NOT NULL, `photo` TEXT NOT NULL)"
        )
        db.execSQL(
            "INSERT INTO contacts_new (id, name, appointment, location, suffix, prefix, " +
                "email, notes, website, birthday, labels, nickname, photo) " +
                "SELECT id, name, appointment, location, suffix, prefix, " +
                "email, notes, website, birthday, labels, nickname, photo FROM contacts"
        )
        db.execSQL("DROP TABLE contacts")
        db.execSQL("ALTER TABLE contacts_new RENAME TO contacts")
        db.execSQL("DROP TABLE IF EXISTS contactsFts")
        db.execSQL("DROP TABLE IF EXISTS contactsFts_data")
        db.execSQL("DROP TABLE IF EXISTS contactsFts_idx")
        db.execSQL("DROP TABLE IF EXISTS contactsFts_content")
        db.execSQL("DROP TABLE IF EXISTS contactsFts_docsize")
        db.execSQL("DROP TABLE IF EXISTS contactsFts_segments")
        db.execSQL("DROP TABLE IF EXISTS contactsFts_segdir")
        db.execSQL("DROP TABLE IF EXISTS contactsFts_stat")
        db.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS `contactsFts` USING FTS4(" +
                "`name` TEXT NOT NULL, `nickname` TEXT NOT NULL, `notes` TEXT NOT NULL, " +
                "`email` TEXT NOT NULL, `location` TEXT NOT NULL, " +
                "`appointment` TEXT NOT NULL, content=`contacts`)"
        )
        db.execSQL(
            "INSERT INTO contactsFts(rowid, name, nickname, notes, email, location, appointment) " +
                "SELECT id, name, nickname, notes, email, location, appointment FROM contacts"
        )
        // Room drops FTS sync triggers before migrating and recreates them
        // after; recreate here too so the table stays synced on every path.
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_contactsFts_BEFORE_UPDATE " +
                "BEFORE UPDATE ON `contacts` BEGIN DELETE FROM `contactsFts` " +
                "WHERE `docid`=OLD.`rowid`; END"
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_contactsFts_BEFORE_DELETE " +
                "BEFORE DELETE ON `contacts` BEGIN DELETE FROM `contactsFts` " +
                "WHERE `docid`=OLD.`rowid`; END"
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_contactsFts_AFTER_UPDATE " +
                "AFTER UPDATE ON `contacts` BEGIN INSERT INTO `contactsFts`(" +
                "`docid`, `name`, `nickname`, `notes`, `email`, `location`, `appointment`) " +
                "VALUES (NEW.`rowid`, NEW.`name`, NEW.`nickname`, NEW.`notes`, " +
                "NEW.`email`, NEW.`location`, NEW.`appointment`); END"
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_contactsFts_AFTER_INSERT " +
                "AFTER INSERT ON `contacts` BEGIN INSERT INTO `contactsFts`(" +
                "`docid`, `name`, `nickname`, `notes`, `email`, `location`, `appointment`) " +
                "VALUES (NEW.`rowid`, NEW.`name`, NEW.`nickname`, NEW.`notes`, " +
                "NEW.`email`, NEW.`location`, NEW.`appointment`); END"
        )
    }
}
