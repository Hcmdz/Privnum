package com.hcmdz.privnum.data

import com.hcmdz.privnum.data.db.ContactDao
import com.hcmdz.privnum.data.db.ContactEntity
import com.hcmdz.privnum.data.db.PrivnumDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

fun ContactEntity.toContact() = Contact(
    id = id,
    fullPhoneNumber = fullPhoneNumber,
    phoneNumber = phoneNumber,
    countryCode = countryCode,
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

fun Contact.toEntity() = ContactEntity(
    id = id,
    fullPhoneNumber = fullPhoneNumber,
    phoneNumber = phoneNumber,
    countryCode = countryCode,
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

@Singleton
class ContactRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val db: PrivnumDatabase by lazy {
        Room.databaseBuilder(context, PrivnumDatabase::class.java, "privnum.db").build()
    }
    private val dao: ContactDao get() = db.contactDao()

    fun observeContacts(): Flow<List<Contact>> =
        dao.observeAll().map { list -> list.map { it.toContact() } }

    suspend fun getAll(): List<Contact> = dao.getAll().map { it.toContact() }

    suspend fun getByFullNumber(fullPhoneNumber: String): Contact? =
        dao.getByFullNumber(fullPhoneNumber)?.toContact()

    fun getByFullNumberSync(fullPhoneNumber: String): Contact? {
        return try {
            kotlinx.coroutines.runBlocking {
                dao.getByFullNumber(fullPhoneNumber)?.toContact()
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun search(rawQuery: String): List<Contact> {
        val query = buildFtsQuery(rawQuery).ifEmpty { return emptyList() }
        return dao.search(query).map { it.toContact() }
    }

    suspend fun add(contact: Contact): Boolean {
        if (dao.getByFullNumber(contact.fullPhoneNumber) != null) return false
        dao.insert(contact.toEntity())
        return true
    }

    suspend fun addAll(contacts: List<Contact>): Int {
        val existing = dao.getAll().map { it.fullPhoneNumber }.toSet()
        val fresh = contacts.filter { it.fullPhoneNumber !in existing }
        if (fresh.isEmpty()) return 0
        dao.insertAll(fresh.map { it.toEntity() })
        return fresh.size
    }

    suspend fun update(originalFullNumber: String, contact: Contact): Boolean {
        val existing = dao.getByFullNumber(originalFullNumber) ?: return false
        if (originalFullNumber != contact.fullPhoneNumber) {
            dao.deleteByFullNumber(originalFullNumber)
        }
        dao.upsert(contact.toEntity().copy(id = existing.id))
        return true
    }

    suspend fun delete(fullPhoneNumber: String): Boolean =
        dao.deleteByFullNumber(fullPhoneNumber) > 0

    suspend fun deleteMultiple(fullPhoneNumbers: List<String>): Boolean =
        dao.deleteByFullNumbers(fullPhoneNumbers) > 0
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
