package com.hcmdz.privnum.caller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalizeIncomingNumberTest {

    private fun digits(vararg d: Char): String = d.joinToString("")

    private val frNational = digits('0', '6', '1', '2', '3', '4', '5', '6', '7', '8')
    private val frE164 = digits('3', '3', '6', '1', '2', '3', '4', '5', '6', '7', '8')

    @Test
    fun `a national number reaches the stored international form`() {
        // The failing case: stripping the leading zero left 9 digits that could
        // never match the 11 stored ones.
        assertEquals(frE164, normalizeIncomingNumber(frNational, "FR"))
    }

    @Test
    fun `an international number normalizes to the same value`() {
        assertEquals(frE164, normalizeIncomingNumber("+$frE164", "FR"))
    }

    @Test
    fun `formatted input reaches the stored form`() {
        assertEquals(frE164, normalizeIncomingNumber("+33 6 12 34 56 78", "FR"))
        assertEquals(frE164, normalizeIncomingNumber("06 12 34 56 78", "FR"))
    }

    @Test
    fun `a foreign number keeps its own calling code`() {
        val usE164 = digits('1', '6', '5', '0', '9', '8', '7', '6', '5', '4')
        assertEquals(usE164, normalizeIncomingNumber("+1 650 987 654", "FR"))
    }

    @Test
    fun `locale digits are folded to ascii first`() {
        val fullWidth = "０６１２３４５６７８"
        assertEquals(frE164, normalizeIncomingNumber(fullWidth, "FR"))
    }

    @Test
    fun `an empty or masked number stays empty`() {
        // The platform sends an empty string, or a negative marker, for a
        // withheld caller: the popup must be skipped, never opened blank.
        assertTrue(normalizeIncomingNumber("", "FR").isEmpty())
        assertTrue(normalizeIncomingNumber("   ", "FR").isEmpty())
        assertTrue(normalizeIncomingNumber("-1", "FR").isEmpty())
        assertTrue(normalizeIncomingNumber("-2", "FR").isEmpty())
        assertTrue(normalizeIncomingNumber("-3", "FR").isEmpty())
        assertTrue(normalizeIncomingNumber("5", "FR").isEmpty())
    }

    @Test
    fun `a short national number takes the region calling code`() {
        // libphonenumber accepts it as a national number, so it is prefixed
        // rather than rejected.
        assertEquals(digits('3', '3', '1', '2', '3'), normalizeIncomingNumber("123", "FR"))
    }

    @Test
    fun `a number with no parseable form keeps its digits`() {
        // A region that cannot claim the number leaves it untouched rather than
        // dropping it, so the popup still shows something.
        val raw = digits('9', '0', '0', '9', '0', '0', '0', '9', '0', '0', '0')
        assertEquals(raw, normalizeIncomingNumber(raw, null))
    }
}