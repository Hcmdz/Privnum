package com.hcmdz.privnum.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FtsQueryTest {

    @Test
    fun `blank query builds empty string`() {
        assertEquals("", buildFtsQuery("   "))
        assertEquals("", buildFtsQuery(""))
    }

    @Test
    fun `single token gets prefix wildcard`() {
        assertEquals("CCTV*", buildFtsQuery("CCTV"))
    }

    @Test
    fun `multi-word query requires every token`() {
        assertEquals("Marie* Curie*", buildFtsQuery("  Marie   Curie "))
    }

    @Test
    fun `punctuation splits into adjacent tokens`() {
        assertEquals("jean* dupont*", buildFtsQuery("jean-dupont"))
        assertEquals("a* b*", buildFtsQuery("a\"b"))
        assertEquals("555* 123*", buildFtsQuery("(555) 123"))
    }

    @Test
    fun `bare operators are dropped`() {
        assertEquals("", buildFtsQuery("OR"))
        assertEquals("x*", buildFtsQuery("x OR"))
    }

    @Test
    fun `split separates digit prefixes from text`() {
        assertEquals("" to listOf("1555"), splitSearchTokens("1555"))
        assertEquals("Test*" to listOf("1555"), splitSearchTokens("Test 1555"))
        assertEquals("Test*" to emptyList<String>(), splitSearchTokens("Test"))
        assertEquals("" to emptyList<String>(), splitSearchTokens("  OR "))
    }

    @Test
    fun `normalize primary keeps exactly one`() {
        val numbers = listOf(
            PhoneNumberRef("a", "a", "FR"),
            PhoneNumberRef("b", "b", "FR"),
            PhoneNumberRef("c", "c", "FR")
        )
        val defaulted = numbers.normalizePrimary()
        assertEquals(listOf(true, false, false), defaulted.map { it.primary })
        val flagged = listOf(
            PhoneNumberRef("a", "a", "FR"),
            PhoneNumberRef("b", "b", "FR", primary = true),
            PhoneNumberRef("c", "c", "FR", primary = true)
        ).normalizePrimary()
        assertEquals(listOf(false, true, false), flagged.map { it.primary })
        assertEquals(emptyList<PhoneNumberRef>(), emptyList<PhoneNumberRef>().normalizePrimary())
    }
}
