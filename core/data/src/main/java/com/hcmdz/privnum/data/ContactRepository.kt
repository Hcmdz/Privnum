package com.hcmdz.privnum.data

import com.hcmdz.privnum.data.db.ContactDao
import com.hcmdz.privnum.data.db.ContactEntity
import com.hcmdz.privnum.data.db.ContactWithNumbers
import com.hcmdz.privnum.data.db.PhoneNumberEntity
import com.hcmdz.privnum.data.db.PrivnumDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Inject
import javax.inject.Singleton

fun ContactWithNumbers.toContact() = Contact(
    id = contact.id,
    name = contact.name,
    numbers = numbers.sortedBy { it.id }.map {
        PhoneNumberRef(
            full = it.full,
            national = it.national,
            country = it.country,
            primary = it.isPrimary
        )
    },
    appointment = contact.appointment,
    location = contact.location,
    suffix = contact.suffix,
    prefix = contact.prefix,
    email = contact.email,
    notes = contact.notes,
    website = contact.website,
    birthday = contact.birthday,
    labels = contact.labels,
    nickname = contact.nickname,
    photo = contact.photo
)

fun Contact.toEntity() = ContactEntity(
    id = id,
    name = name,
    appointment = appointment,
    location = location,
    suffix = suffix,
    prefix = prefix,
    email = email,
    notes = notes,
    website = website,
    birthday = birthday,
    labels = labels,
    nickname = nickname,
    photo = photo
)

fun Contact.toNumberEntities(contactId: Long) =
    numbers.normalizePrimary().map {
        PhoneNumberEntity(
            contactId = contactId,
            full = it.full,
            national = it.national,
            country = it.country,
            isPrimary = it.primary
        )
    }

sealed interface SaveResult {
    data object Saved : SaveResult
    data object NotFound : SaveResult
    data class DuplicateNumber(val ownerName: String) : SaveResult
}

@Singleton
class ContactRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val photos: ContactPhotoStore
) {
    private companion object {
        const val DATABASE_NAME = "privnum.db"
    }

    private val db: PrivnumDatabase by lazy {
        loadSqlCipher()
        // An existing plaintext database is encrypted before Room opens it; the
        // schema is unchanged, so this is a file rewrite and not a migration.
        // Both the keystore round trip and the rewrite are pushed off the
        // calling thread, which is main for the flows collected during startup.
        // A cold first launch after the update waits here for the copy to
        // finish, bounded by the contact count; a visible wait would need the
        // open to move behind a suspending gate in the callers.
        val passphrase = runBlocking(Dispatchers.IO) {
            DatabasePassphrase.get(context).also {
                encryptDatabaseIfPlaintext(
                    context,
                    context.getDatabasePath(DATABASE_NAME),
                    it
                )
            }
        }
        Room.databaseBuilder(context, PrivnumDatabase::class.java, DATABASE_NAME)
            .addMigrations(com.hcmdz.privnum.data.db.MIGRATION_1_2)
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            .build()
    }
    private val dao: ContactDao get() = db.contactDao()

    fun observeContacts(): Flow<List<Contact>> =
        dao.observeAll().map { list -> list.map { it.toContact() } }

    suspend fun getAll(): List<Contact> = dao.getAll().map { it.toContact() }

    suspend fun getById(id: Long): Contact? = dao.getById(id)?.toContact()

    suspend fun findByNumber(full: String): Contact? {
        val id = dao.findContactIdByFull(full) ?: return null
        return dao.getById(id)?.toContact()
    }

    fun findByNumberSync(full: String): Contact? {
        return try {
            kotlinx.coroutines.runBlocking { findByNumber(full) }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun search(rawQuery: String): List<Contact> {
        val (textQuery, digitPrefixes) = splitSearchTokens(rawQuery)
        if (textQuery.isEmpty() && digitPrefixes.isEmpty()) return emptyList()
        var ids: Set<Long>? = null
        if (textQuery.isNotEmpty()) {
            ids = dao.searchTextIds(textQuery).toSet()
        }
        for (prefix in digitPrefixes) {
            val prefixIds = dao.findIdsByNumberPrefix(prefix).toSet()
            ids = ids?.intersect(prefixIds) ?: prefixIds
        }
        val finalIds = ids ?: return emptyList()
        if (finalIds.isEmpty()) return emptyList()
        return dao.getByIds(finalIds.toList())
            .map { it.toContact() }
            .sortedBy { it.name.lowercase() }
    }

    suspend fun add(contact: Contact): SaveResult {
        val fulls = contact.numbers.map { it.full }
        if (fulls.isEmpty()) return SaveResult.Saved
        val ownerId = dao.findContactIdsByFulls(fulls).firstOrNull()
        if (ownerId != null) {
            val owner = dao.getById(ownerId)?.contact?.name.orEmpty()
            return SaveResult.DuplicateNumber(owner)
        }
        val id = dao.insertContact(contact.toEntity().copy(id = 0))
        dao.insertNumbers(contact.toNumberEntities(id))
        return SaveResult.Saved
    }

    suspend fun addAll(contacts: List<Contact>): Int {
        var added = 0
        for (contact in contacts) {
            if (add(contact) is SaveResult.Saved) added++
        }
        return added
    }

    suspend fun update(id: Long, contact: Contact): SaveResult {
        val existing = dao.getById(id) ?: return SaveResult.NotFound
        val fulls = contact.numbers.map { it.full }
        val ownerId = if (fulls.isEmpty()) null
            else dao.findContactIdsByFulls(fulls).firstOrNull { it != id }
        if (ownerId != null) {
            val owner = dao.getById(ownerId)?.contact?.name.orEmpty()
            return SaveResult.DuplicateNumber(owner)
        }
        dao.updateContact(contact.toEntity().copy(id = existing.contact.id))
        dao.deleteNumbersByContact(existing.contact.id)
        dao.insertNumbers(contact.toNumberEntities(existing.contact.id))
        return SaveResult.Saved
    }

    suspend fun delete(id: Long): Boolean {
        val photo = dao.getById(id)?.contact?.photo.orEmpty()
        return (dao.deleteContactById(id) > 0).also { ok ->
            if (ok) photos.deletePhoto(photo)
        }
    }

    suspend fun deleteMultiple(ids: List<Long>): Boolean {
        if (ids.isEmpty()) return false
        val photosToDrop = dao.getByIds(ids).map { it.contact.photo }
        return (dao.deleteContactsByIds(ids) > 0).also { ok ->
            if (ok) photosToDrop.forEach { photos.deletePhoto(it) }
        }
    }

    suspend fun clearAll() {
        dao.clearNumbers()
        dao.clearContacts()
        photos.deleteAllPhotos()
    }
}

internal fun buildFtsQuery(rawQuery: String): String {
    val operators = setOf("AND", "OR", "NOT", "NEAR")
    return rawQuery.trim()
        .split("\\s+".toRegex())
        .flatMap { chunk -> chunk.split(Regex("[^\\p{L}\\p{Nd}]+")) }
        .map { it.trim() }
        .filter { it.isNotEmpty() && it.uppercase() !in operators }
        .joinToString(" ") { "$it*" }
}

/** Splits a search string into an FTS text query plus digit prefixes for number lookup. */
internal fun splitSearchTokens(rawQuery: String): Pair<String, List<String>> {
    val operators = setOf("AND", "OR", "NOT", "NEAR")
    val tokens = rawQuery.trim()
        .split("\\s+".toRegex())
        .flatMap { chunk -> chunk.split(Regex("[^\\p{L}\\p{Nd}]+")) }
        .map { it.trim() }
        .filter { it.isNotEmpty() && it.uppercase() !in operators }
    val digits = tokens.filter { it.all(Char::isDigit) }
    val text = tokens.filterNot { it.all(Char::isDigit) }.joinToString(" ") { "$it*" }
    return text to digits
}
