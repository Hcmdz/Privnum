package com.hcmdz.privnum.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewHelpersTest {

    @Test
    fun `month-day formats without year`() {
        assertEquals("May 4", formatContactDate("--05-04"))
    }

    @Test
    fun `full date formats with year`() {
        assertEquals("May 4, 2023", formatContactDate("2023-05-04"))
    }

    @Test
    fun `garbage date passes through`() {
        assertEquals("soon", formatContactDate("soon"))
    }

    @Test
    fun `whatsapp uri prefers app when installed`() {
        assertTrue(whatsappUri("1", true).startsWith("whatsapp://"))
        assertTrue(whatsappUri("1", false).startsWith("https://wa.me/"))
    }

    @Test
    fun `telegram uri covers profile flag`() {
        assertTrue(telegramUri("1", true, true).endsWith("&profile"))
        assertTrue(telegramUri("1", false, true).endsWith("phone=1"))
        assertTrue(telegramUri("1", true, false).contains("?profile"))
    }

    @Test
    fun `avatar index stable in range`() {
        ('A'..'Z').forEach {
            val index = avatarRoleIndex(it)
            assertTrue(index in 0..2)
            assertEquals(index, avatarRoleIndex(it.lowercaseChar()))
        }
    }

    @Test
    fun `messaging packages explicit only when installed`() {
        assertEquals("com.whatsapp", whatsappPackage(true))
        assertEquals("org.telegram.messenger", telegramPackage(true))
        assertEquals(null, whatsappPackage(false))
        assertEquals(null, telegramPackage(false))
    }
}
