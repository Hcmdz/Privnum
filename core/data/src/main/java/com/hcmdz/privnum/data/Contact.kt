package com.hcmdz.privnum.data

data class Contact(
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
) {
    fun displayName(): String = buildString {
        if (prefix.isNotBlank()) append(prefix.trim()).append(' ')
        append(name.trim())
        if (suffix.isNotBlank()) append(", ").append(suffix.trim())
    }
}
