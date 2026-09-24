package com.hcmdz.privnum.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val appointment: String = "",
    val location: String = "",
    val suffix: String = "",
    val prefix: String = "",
    val email: String = "",
    val notes: String = "",
    val website: String = "",
    val birthday: String = "",
    val labels: String = "",
    val nickname: String = "",
    val photo: String = ""
)

@Entity(
    tableName = "phone_numbers",
    foreignKeys = [
        ForeignKey(
            entity = ContactEntity::class,
            parentColumns = ["id"],
            childColumns = ["contact_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["contact_id"]),
        Index(value = ["full"], unique = true)
    ]
)
data class PhoneNumberEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "contact_id")
    val contactId: Long,
    val full: String,
    val national: String,
    val country: String,
    @ColumnInfo(name = "is_primary")
    val isPrimary: Boolean = false
)

@Entity(tableName = "contactsFts")
@Fts4(contentEntity = ContactEntity::class)
data class ContactFts(
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "nickname") val nickname: String,
    @ColumnInfo(name = "notes") val notes: String,
    @ColumnInfo(name = "email") val email: String,
    @ColumnInfo(name = "location") val location: String,
    @ColumnInfo(name = "appointment") val appointment: String
)

data class ContactWithNumbers(
    @Embedded val contact: ContactEntity,
    @Relation(parentColumn = "id", entityColumn = "contact_id")
    val numbers: List<PhoneNumberEntity>
)
