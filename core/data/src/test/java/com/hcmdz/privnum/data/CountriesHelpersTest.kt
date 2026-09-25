package com.hcmdz.privnum.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class CountriesHelpersTest {

    @Test
    fun `filter matches name case-insensitively`() {
        val codes = filterCountries("fran").map { it.code }
        assertTrue(codes.contains("FR"))
    }

    @Test
    fun `filter matches localized country name`() {
        assertTrue(filterCountries("France", Locale.FRENCH).map { it.code }.contains("FR"))
        assertTrue(filterCountries("مصر", Locale.forLanguageTag("ar")).map { it.code }.contains("EG"))
    }

    @Test
    fun `filter matches ISO code`() {
        assertEquals(listOf("DZ"), filterCountries("dz").map { it.code })
    }

    @Test
    fun `filter strips plus spaces and dashes`() {
        val codes = filterCountries(" +21-3 ").map { it.code }
        assertTrue(codes.contains("DZ"))
    }

    @Test
    fun `filter empty query returns all`() {
        assertEquals(Countries.all.size, filterCountries("  ").size)
    }

    @Test
    fun `filter garbage returns empty`() {
        assertTrue(filterCountries("xyzzy").isEmpty())
    }

    @Test
    fun `suggest proposes unique prefix when invalid locally`() {
        val digits = listOf('2', '1', '3', '1', '2').joinToString("")
        assertEquals("DZ", suggestCountryFor(digits, "US")?.code)
    }

    @Test
    fun `suggest silent when valid in current region`() {
        val digits = listOf('6', '5', '0', '2', '5', '3', '0', '0', '0', '0').joinToString("")
        assertNull(suggestCountryFor(digits, "US"))
    }

    @Test
    fun `suggest silent on collision empty and plus input`() {
        val digits = listOf('1', '2', '3', '4', '5', '6', '7', '8', '9', '0').joinToString("")
        val plusFr = '+' + listOf('3', '3', '6', '1', '2', '3', '4', '5', '6', '7', '8').joinToString("")
        assertNull(suggestCountryFor(digits, "US"))
        assertNull(suggestCountryFor("", "US"))
        assertNull(suggestCountryFor(plusFr, "US"))
    }
}
