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
        val base = EditorUiState(name = "Ann", country = Countries.getByCode("DZ"))
        assertTrue(base.inputsEqual(base.copy(message = "hi", saved = true, notFound = true)))
        assertFalse(base.inputsEqual(base.copy(name = "Bob")))
        assertFalse(base.inputsEqual(base.copy(country = Countries.getByCode("FR"))))
    }
}
