package com.hcmdz.privnum.data

data class PhoneNumberRef(
    val full: String,
    val national: String,
    val country: String,
    val primary: Boolean = false
)

data class Contact(
    val id: Long = 0,
    val name: String,
    val numbers: List<PhoneNumberRef> = emptyList(),
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

    fun primaryNumber(): PhoneNumberRef? =
        numbers.firstOrNull { it.primary } ?: numbers.firstOrNull()
}

/** Exactly one primary: first flagged wins, else the first number. */
fun List<PhoneNumberRef>.normalizePrimary(): List<PhoneNumberRef> {
    if (isEmpty()) return this
    val flagged = indexOfFirst { it.primary }
    val primaryIndex = if (flagged >= 0) flagged else 0
    return mapIndexed { i, n -> n.copy(primary = i == primaryIndex) }
}
