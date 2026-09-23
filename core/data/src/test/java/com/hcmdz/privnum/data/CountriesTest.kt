package com.hcmdz.privnum.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
}
