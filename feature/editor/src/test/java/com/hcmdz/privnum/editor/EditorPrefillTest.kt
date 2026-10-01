package com.hcmdz.privnum.editor

import com.hcmdz.privnum.data.Countries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorPrefillTest {

    private fun digits(vararg d: Char): String = d.joinToString("")
    private val fr = Countries.getByCode("FR")!!

    @Test
    fun `parseable history number seeds country and digits as primary`() {
        val full = digits('3', '3', '6', '1', '2', '3', '4', '5', '6', '7', '8')
        val row = "+$full".toPrefillRow(fr)
        assertEquals(digits('6', '1', '2', '3', '4', '5', '6', '7', '8'), row.nationalNumber)
        assertEquals("FR", row.country?.code)
        assertTrue(row.primary)
    }

    @Test
    fun `unparseable history number stays editable as raw text`() {
        val row = "not-a-number".toPrefillRow(fr)
        assertEquals("not-a-number", row.nationalNumber)
        assertEquals("FR", row.country?.code)
        assertFalse(row.primary)
    }

    @Test
    fun `locale digits prefill parses to ascii`() {
        val row = "３３６１２３４５６７８".toPrefillRow(fr)
        assertEquals(digits('6', '1', '2', '3', '4', '5', '6', '7', '8'), row.nationalNumber)
        assertEquals("FR", row.country?.code)
        assertTrue(row.primary)
    }
}
