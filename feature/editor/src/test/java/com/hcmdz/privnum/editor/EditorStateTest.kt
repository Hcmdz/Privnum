package com.hcmdz.privnum.editor

import com.hcmdz.privnum.data.Countries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorStateTest {

    @Test
    fun `setting wins over SIM`() {
        assertEquals("FR", resolveDefaultCountry("FR", "DZ").code)
    }

    @Test
    fun `SIM wins over historical default`() {
        assertEquals("DZ", resolveDefaultCountry(null, "DZ").code)
        assertEquals("FR", resolveDefaultCountry(null, "FR").code)
    }

    @Test
    fun `unknown codes fall back to DZ`() {
        assertEquals("DZ", resolveDefaultCountry("XX", "YY").code)
        assertEquals("DZ", resolveDefaultCountry(null, "").code)
    }

    @Test
    fun `inputsEqual ignores transient flags`() {
        val base = EditorUiState(
            name = "Ann",
            numbers = listOf(NumberRow(country = Countries.getByCode("DZ")))
        )
        assertTrue(base.inputsEqual(base.copy(message = "hi", saved = true, notFound = true)))
        assertFalse(base.inputsEqual(base.copy(name = "Bob")))
        assertFalse(
            base.inputsEqual(
                base.copy(numbers = listOf(NumberRow(country = Countries.getByCode("FR"))))
            )
        )
        assertFalse(
            base.inputsEqual(
                base.copy(
                    numbers = base.numbers + NumberRow(country = Countries.getByCode("DZ"))
                )
            )
        )
    }

    @Test
    fun `birthday millis parses stored formats`() {
        assertTrue((birthdayToMillis("2023-05-04") ?: 0) > 0)
        assertTrue((birthdayToMillis("--05-04") ?: 0) > 0)
        assertEquals(null, birthdayToMillis("soon"))
        assertEquals(null, birthdayToMillis(""))
    }
}
