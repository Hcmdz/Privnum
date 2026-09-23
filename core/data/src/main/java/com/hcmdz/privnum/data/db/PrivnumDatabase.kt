package com.hcmdz.privnum.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ContactEntity::class, ContactFts::class],
    version = 1,
    exportSchema = true
)
abstract class PrivnumDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
}
