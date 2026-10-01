package com.hcmdz.privnum.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AsciiDigitsTest {

    private fun digits(vararg d: Char): String = d.joinToString("")

    @Test
    fun `ascii digits pass through`() {
        val full = digits('3', '3', '6', '1', '2', '3', '4', '5', '6', '7', '8')
        assertEquals(full, PhoneNumberUtils.asciiDigits(full))
        assertEquals("+$full", PhoneNumberUtils.asciiDigits("+33 6 12 34 56 78"))
    }

    @Test
    fun `full-width digits fold to ascii`() {
        val fullWidth = "３３６１２３４５６７８"
        assertEquals("33612345678", PhoneNumberUtils.asciiDigits(fullWidth))
    }

    @Test
    fun `arabic-indic digits fold to ascii`() {
        val indicative = "٣٣٦١٢٣٤٥٦٧٨"
        assertEquals("33612345678", PhoneNumberUtils.asciiDigits(indicative))
    }

    @Test
    fun `letters and blanks are dropped`() {
        assertEquals("", PhoneNumberUtils.asciiDigits("Unknown"))
        assertEquals("1", PhoneNumberUtils.asciiDigits("-1"))
    }
}
