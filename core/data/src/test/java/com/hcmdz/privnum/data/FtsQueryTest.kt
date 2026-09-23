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
        assertEquals("\"CCTV\"*", buildFtsQuery("CCTV"))
    }

    @Test
    fun `multi-word query requires every token`() {
        assertEquals("\"Marie\"* \"Curie\"*", buildFtsQuery("  Marie   Curie "))
    }

    @Test
    fun `quotes are escaped`() {
        assertEquals("\"a\"\"b\"*", buildFtsQuery("a\"b"))
    }
}
