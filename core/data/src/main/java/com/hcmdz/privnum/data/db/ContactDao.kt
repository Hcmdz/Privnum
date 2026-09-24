package com.hcmdz.privnum.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Transaction
    @Query("SELECT * FROM contacts ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ContactWithNumbers>>

    @Transaction
    @Query("SELECT * FROM contacts ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAll(): List<ContactWithNumbers>

    @Transaction
    @Query("SELECT * FROM contacts WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ContactWithNumbers?

    @Transaction
    @Query("SELECT * FROM contacts WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<ContactWithNumbers>

    @Query("SELECT contact_id FROM phone_numbers WHERE full = :full LIMIT 1")
    suspend fun findContactIdByFull(full: String): Long?

    @Query("SELECT DISTINCT contact_id FROM phone_numbers WHERE full IN (:fulls)")
    suspend fun findContactIdsByFulls(fulls: List<String>): List<Long>

    @Query("SELECT DISTINCT contact_id FROM phone_numbers WHERE full LIKE :prefix || '%'")
    suspend fun findIdsByNumberPrefix(prefix: String): List<Long>

    @Query(
        """SELECT contacts.id FROM contacts JOIN contactsFts fts
           ON contacts.id = fts.rowid
           WHERE contactsFts MATCH :query"""
    )
    suspend fun searchTextIds(query: String): List<Long>

    @Query("SELECT * FROM phone_numbers WHERE contact_id = :contactId ORDER BY id ASC")
    suspend fun getNumbersByContact(contactId: Long): List<PhoneNumberEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertContact(contact: ContactEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNumbers(numbers: List<PhoneNumberEntity>): List<Long>

    @Update
    suspend fun updateContact(contact: ContactEntity)

    @Query("DELETE FROM phone_numbers WHERE contact_id = :contactId")
    suspend fun deleteNumbersByContact(contactId: Long)

    @Query("DELETE FROM phone_numbers WHERE full IN (:fulls)")
    suspend fun deleteNumbersByFulls(fulls: List<String>): Int

    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun deleteContactById(id: Long): Int

    @Query("DELETE FROM contacts WHERE id IN (:ids)")
    suspend fun deleteContactsByIds(ids: List<Long>): Int

    @Delete
    suspend fun deleteContact(contact: ContactEntity)

    @Query("DELETE FROM contacts")
    suspend fun clearContacts()

    @Query("DELETE FROM phone_numbers")
    suspend fun clearNumbers()
}
