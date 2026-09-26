package com.hcmdz.privnum.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class PasscodePinTest {

    private val salt = ByteArray(16) { it.toByte() }

    private fun legacyHash(pin: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(salt + pin.toByteArray())
            .joinToString("") { "%02x".format(it) }

    @Test
    fun `pbkdf2 hash verifies its own pin`() {
        val hash = newPinHash("1234", salt)
        assertTrue(pinMatches("1234", salt, hash, PIN_KDF))
    }

    @Test
    fun `pbkdf2 rejects a wrong pin`() {
        val hash = newPinHash("1234", salt)
        assertFalse(pinMatches("4321", salt, hash, PIN_KDF))
    }

    @Test
    fun `same pin under another salt yields another hash`() {
        assertNotEquals(newPinHash("1234", salt), newPinHash("1234", ByteArray(16) { 7 }))
    }

    @Test
    fun `legacy sha256 install still verifies and reports the upgrade`() {
        var upgraded = false
        val ok = pinMatches("1234", salt, legacyHash("1234"), null) { upgraded = true }
        assertTrue(ok)
        assertTrue(upgraded)
    }

    @Test
    fun `legacy path is not taken once the kdf is recorded`() {
        var upgraded = false
        val ok = pinMatches("1234", salt, legacyHash("1234"), PIN_KDF) { upgraded = true }
        assertFalse(ok)
        assertFalse(upgraded)
    }

    @Test
    fun `corrupt stored hash fails closed`() {
        assertFalse(pinMatches("1234", salt, "zz", PIN_KDF))
        assertFalse(pinMatches("1234", salt, "", PIN_KDF))
    }
}
