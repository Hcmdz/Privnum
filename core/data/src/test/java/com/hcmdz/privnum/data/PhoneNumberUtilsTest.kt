package com.hcmdz.privnum.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumberUtilsTest {

    @Test
    fun `parse national FR number gives full international digits`() {
        val parsed = PhoneNumberUtils.parse("0612345678", "FR")
        assertNotNull(parsed)
        assertEquals("FR", parsed!!.countryIso)
        assertEquals("612345678", parsed.nationalNumber)
        assertEquals("33612345678", parsed.fullNumber)
    }

    @Test
    fun `parse E164 input keeps country`() {
        val parsed = PhoneNumberUtils.parse("+33612345678", "US")
        assertNotNull(parsed)
        assertEquals("FR", parsed!!.countryIso)
        assertEquals("33612345678", parsed.fullNumber)
    }

    @Test
    fun `parse garbage returns null`() {
        assertNull(PhoneNumberUtils.parse("not-a-number", "FR"))
    }

    @Test
    fun `isValid accepts real number rejects short`() {
        assertTrue(PhoneNumberUtils.isValid("0612345678", "FR"))
        assertEquals(false, PhoneNumberUtils.isValid("123", "FR"))
    }

    @Test
    fun `formatNational falls back to input on garbage`() {
        assertEquals("xyz", PhoneNumberUtils.formatNational("xyz", "FR"))
    }
}
