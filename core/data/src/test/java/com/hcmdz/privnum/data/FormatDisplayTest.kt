package com.hcmdz.privnum.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatDisplayTest {

    private fun digits(vararg d: Char): String = d.joinToString("")
    private val frFull = digits('3', '3', '6', '1', '2', '3', '4', '5', '6', '7', '8')
    private val usFull = digits('1', '6', '5', '0', '9', '8', '7', '6', '5', '4')

    @Test
    fun `a foreign number keeps its country code`() {
        val out = PhoneNumberUtils.formatDisplay(usFull, "US", "FR")
        assertTrue("expected a leading + but got a national form", out.startsWith("+1"))
    }

    @Test
    fun `a local number uses the national form`() {
        val out = PhoneNumberUtils.formatDisplay(frFull, "FR", "FR")
        assertTrue("national form must not repeat the +33 prefix", !out.startsWith("+33"))
        assertTrue("national form must not start with + at all", !out.startsWith("+"))
    }

    @Test
    fun `an unknown user region falls back to the international form`() {
        val out = PhoneNumberUtils.formatDisplay(frFull, "FR", null)
        assertTrue(out.startsWith("+33"))
    }

    @Test
    fun `an unparseable number still shows a country code`() {
        assertEquals("+xyz", PhoneNumberUtils.formatDisplay("xyz", "FR", "FR"))
    }

    @Test
    fun `the caller must not need to prepend a plus`() {
        // Both forms carry the marker themselves: national none, international one.
        val local = PhoneNumberUtils.formatDisplay(frFull, "FR", "FR")
        val foreign = PhoneNumberUtils.formatDisplay(usFull, "US", "FR")
        assertEquals(0, local.count { it == '+' })
        assertEquals(1, foreign.count { it == '+' })
    }
}