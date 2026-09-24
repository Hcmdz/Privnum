package com.hcmdz.privnum.contacts

import com.hcmdz.privnum.data.Contact
import com.hcmdz.privnum.data.PhoneNumberRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactClipboardTest {

    private fun contact(
        name: String = "Ann",
        email: String = "a@b.c",
        notes: String = ""
    ) = Contact(
        name = name,
        numbers = listOf(
            PhoneNumberRef("336000000", "6000000", "FR", primary = true),
            PhoneNumberRef("1650" + "2530000", "650" + "2530000", "US", primary = false)
        ),
        email = email,
        notes = notes
    )

    @Test
    fun `non-empty fields only`() {
        val lines = contactClipboardLines(listOf(contact()))
        assertTrue(lines.contains("Name - Ann"))
        assertTrue(lines.contains("Phone - +336000000"))
        assertTrue(lines.contains("Phone 2 - +1650"))
        assertTrue(lines.contains("Email - a@b.c"))
        assertTrue(!lines.contains("Notes"))
        assertTrue(!lines.contains("Nickname"))
    }

    @Test
    fun `contacts separated by blank line`() {
        val lines = contactClipboardLines(listOf(contact("Ann"), contact("Bob")))
        assertEquals(2, lines.split("\n\n").size)
    }
}
