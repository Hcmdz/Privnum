package com.hcmdz.privnum.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAll(): List<ContactEntity>

    @Query("SELECT * FROM contacts WHERE fullPhoneNumber = :fullPhoneNumber LIMIT 1")
    suspend fun getByFullNumber(fullPhoneNumber: String): ContactEntity?

    @Query(
        """SELECT c.* FROM contacts c JOIN contactsFts fts
           ON c.id = fts.rowid
           WHERE contactsFts MATCH :query
           ORDER BY c.name COLLATE NOCASE ASC"""
    )
    suspend fun search(query: String): List<ContactEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(contact: ContactEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(contacts: List<ContactEntity>): List<Long>

    @Update
    suspend fun update(contact: ContactEntity)

    @Upsert
    suspend fun upsert(contact: ContactEntity)

    @Query("DELETE FROM contacts WHERE fullPhoneNumber = :fullPhoneNumber")
    suspend fun deleteByFullNumber(fullPhoneNumber: String): Int

    @Query("DELETE FROM contacts WHERE fullPhoneNumber IN (:fullPhoneNumbers)")
    suspend fun deleteByFullNumbers(fullPhoneNumbers: List<String>): Int

    @Query("DELETE FROM contacts")
    suspend fun clearAll()
}
