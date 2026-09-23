package com.hcmdz.privnum.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumberUtilsTest {

    private fun digits(vararg d: Char): String = d.joinToString("")
    private val frNational = digits('0', '6', '1', '2', '3', '4', '5', '6', '7', '8')
    private val frFull = digits('3', '3', '6', '1', '2', '3', '4', '5', '6', '7', '8')
    private val frIntl = "+$frFull"

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

    @Test
    fun `parseForSave keeps plus-prefixed country over selected region`() {
        val parsed = PhoneNumberUtils.parseForSave(frIntl, "DZ")
        assertNotNull(parsed)
        assertEquals("FR", parsed!!.countryIso)
        assertEquals(frFull, parsed.fullNumber)
    }

    @Test
    fun `parseForSave parses national digits with selected region`() {
        val parsed = PhoneNumberUtils.parseForSave(frNational, "FR")
        assertNotNull(parsed)
        assertEquals("FR", parsed!!.countryIso)
        assertEquals(frFull, parsed.fullNumber)
    }

    @Test
    fun `previewNumber formats plus input and nulls garbage`() {
        assertEquals("+$frFull", PhoneNumberUtils.previewNumber(frIntl, "DZ"))
        assertNull(PhoneNumberUtils.previewNumber("abc", "DZ"))
        assertNull(PhoneNumberUtils.previewNumber("", "DZ"))
    }

    @Test
    fun `trimPhoneInput keeps plus and caps to 15 digits`() {
        assertEquals(frIntl, PhoneNumberUtils.trimPhoneInput("  $frIntl  "))
        val long = "1234" + "5678" + "9012" + "3456"
        assertEquals("1234" + "5678" + "9012" + "345", PhoneNumberUtils.trimPhoneInput(long))
        assertEquals("", PhoneNumberUtils.trimPhoneInput("+"))
        assertEquals("", PhoneNumberUtils.trimPhoneInput(""))
        assertEquals(frNational, PhoneNumberUtils.trimPhoneInput("06-12-34-56-78"))
    }
}
