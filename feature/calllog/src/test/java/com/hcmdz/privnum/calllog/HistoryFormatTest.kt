package com.hcmdz.privnum.calllog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class HistoryFormatTest {

    @Test
    fun `duration is seconds not milliseconds`() {
        assertEquals("0s", formatHistoryDuration(0))
        assertEquals("5s", formatHistoryDuration(5))
        assertEquals("1m 5s", formatHistoryDuration(65))
        assertEquals("1h 1m 1s", formatHistoryDuration(3661))
        assertEquals("0s", formatHistoryDuration(-3))
    }

    @Test
    fun `date formats with locale`() {
        val epoch = 1_700_000_000_000L
        val us = formatHistoryDate(epoch, Locale.US)
        val fr = formatHistoryDate(epoch, Locale.FRANCE)
        assertTrue(us.isNotBlank())
        assertTrue(fr.isNotBlank())
    }
}
