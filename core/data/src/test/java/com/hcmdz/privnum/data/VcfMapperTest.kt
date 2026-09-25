package com.hcmdz.privnum.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

private fun sampleContact() = Contact(
    name = "Jean Dupont",
    numbers = listOf(
        PhoneNumberRef("33612345678", "612345678", "FR", primary = true),
        PhoneNumberRef("1650" + "2530000", "650" + "2530000", "US", primary = false)
    ),
    appointment = "Plombier",
    location = "Lyon",
    suffix = "Jr",
    prefix = "Mr",
    email = "jean.dupont@example.com",
    notes = "CCTV install",
    website = "https://example.com",
    birthday = "1990-05-06",
    nickname = "JD",
    photo = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString("fake-bytes".toByteArray())
)

class VcfMapperTest {

    @Test
    fun `round trip preserves every field`() {
        val original = sampleContact()
        val parsed = VcfMapper.parseVcf(VcfMapper.toVcf(original), "FR").single()
        assertEquals(original.numbers, parsed.numbers)
        assertEquals(original.primaryNumber(), parsed.primaryNumber())
        assertEquals(original.name, parsed.name)
        assertEquals(original.prefix, parsed.prefix)
        assertEquals(original.suffix, parsed.suffix)
        assertEquals(original.email, parsed.email)
        assertEquals(original.appointment, parsed.appointment)
        assertEquals(original.location, parsed.location)
        assertEquals(original.notes, parsed.notes)
        assertEquals(original.website, parsed.website)
        assertEquals(original.birthday, parsed.birthday)
        assertEquals(original.nickname, parsed.nickname)
        assertEquals(original.photo, parsed.photo)
    }

    @Test
    fun `parses legacy TS-format VCF`() {
        val tsFormat = "BEGIN:VCARD\r\n" +
            "VERSION:2.1\r\n" +
            "N:;Marie Curie;;Mme;Dr\r\n" +
            "FN:Mme Marie Curie, Dr\r\n" +
            "X-ANDROID-CUSTOM:vnd.android.cursor.item/nickname;MC;1;;;;;;;;;;;;;\r\n" +
            "TEL;CELL;PREF:+33698765432\r\n" +
            "EMAIL;PREF;HOME:marie.curie@example.com\r\n" +
            "TITLE:Physicienne\r\n" +
            "NOTE:Prix Nobel\r\n" +
            "BDAY:1867-11-07\r\n" +
            "END:VCARD"
        val parsed = VcfMapper.parseVcf(tsFormat, "FR").single()
        assertEquals("33698765432", parsed.primaryNumber()?.full)
        assertEquals("Marie Curie", parsed.name)
        assertEquals("MC", parsed.nickname)
        assertEquals("marie.curie@example.com", parsed.email)
        assertEquals("1867-11-07", parsed.birthday)
    }

    @Test
    fun `drops cards without number or without name`() {
        val noTel = "BEGIN:VCARD\r\nVERSION:2.1\r\nFN:Ghost\r\nEND:VCARD"
        val noName = "BEGIN:VCARD\r\nVERSION:2.1\r\nTEL;CELL:+33698765432\r\nEND:VCARD"
        assertTrue(VcfMapper.parseVcf(noTel, "FR").isEmpty())
        assertTrue(VcfMapper.parseVcf(noName, "FR").isEmpty())
    }

    @Test
    fun `partial birthday survives`() {
        val vcf = "BEGIN:VCARD\r\nVERSION:2.1\r\nFN:Anniv\r\n" +
            "TEL;CELL:+33612345678\r\nBDAY:--05-06\r\nEND:VCARD"
        val parsed = VcfMapper.parseVcf(vcf, "FR").single()
        assertTrue(parsed.birthday.contains("05-06"))
    }

    @Test
    fun `removed Israeli number is skipped on import`() {
        val unsupported = "+" + listOf(
            '9', '7', '2', '5', '0', '1', '2', '3', '4', '5', '6', '7'
        ).joinToString("")
        val vcf = "BEGIN:VCARD\r\nVERSION:2.1\r\nFN:Test\r\nTEL:$unsupported\r\nEND:VCARD"
        assertTrue(VcfMapper.parseVcf(vcf, "US").isEmpty())
    }

    @Test
    fun `mixed VCF keeps supported numbers only`() {
        val unsupported = "+" + listOf(
            '9', '7', '2', '5', '0', '1', '2', '3', '4', '5', '6', '7'
        ).joinToString("")
        val supported = "+" + listOf(
            '3', '3', '6', '1', '2', '3', '4', '5', '6', '7', '8'
        ).joinToString("")
        val vcf = "BEGIN:VCARD\r\nVERSION:2.1\r\nFN:Test\r\n" +
            "TEL:$unsupported\r\nTEL:$supported\r\nEND:VCARD"
        assertEquals(
            listOf("FR"),
            VcfMapper.parseVcf(vcf, "US").single().numbers.map { it.country }
        )
    }

    @Test
    fun `garbage input returns empty list`() {
        assertTrue(VcfMapper.parseVcf("not a vcard at all", "FR").isEmpty())
    }
}
