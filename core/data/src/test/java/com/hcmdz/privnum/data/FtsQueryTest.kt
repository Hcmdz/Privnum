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
}
