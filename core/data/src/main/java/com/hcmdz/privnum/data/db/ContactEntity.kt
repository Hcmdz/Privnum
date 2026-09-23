package com.hcmdz.privnum.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "contacts",
    indices = [Index(value = ["fullPhoneNumber"], unique = true)]
)
data class ContactEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fullPhoneNumber: String,
    val phoneNumber: String,
    val countryCode: String,
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

@Entity(tableName = "contactsFts")
@Fts4(contentEntity = ContactEntity::class)
data class ContactFts(
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "fullPhoneNumber") val fullPhoneNumber: String,
    @ColumnInfo(name = "phoneNumber") val phoneNumber: String,
    @ColumnInfo(name = "nickname") val nickname: String,
    @ColumnInfo(name = "notes") val notes: String
)
