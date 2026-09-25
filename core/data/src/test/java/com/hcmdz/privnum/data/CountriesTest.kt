package com.hcmdz.privnum.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CountriesTest {

    @Test
    fun `list is complete and well-formed`() {
        assertTrue(Countries.all.size >= 190)
        assertTrue(Countries.all.all {
            it.name.isNotBlank() && it.code.length == 2 && it.dialCode.all { c -> c.isDigit() }
        })
    }

    @Test
    fun `lookups resolve both directions`() {
        assertEquals("213", Countries.getByCode("DZ")?.dialCode)
        assertEquals("DZ", Countries.getByDialCode("213")?.code)
        assertNotNull(Countries.getByCode("fr"))
    }

    @Test
    fun `Israel is not available as a supported country`() {
        assertNull(Countries.getByCode("IL"))
        assertNull(Countries.getByDialCode("972"))
        assertTrue(filterCountries("Israel").isEmpty())
        assertTrue(filterCountries("+972").isEmpty())
    }
}
