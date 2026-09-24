package com.hcmdz.privnum.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class BiometricTokenTest {

    private fun hashOf(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    @Test
    fun `matching token verifies`() {
        val raw = byteArrayOf(1, 2, 3, 4)
        assertTrue(biometricTokenMatches(raw, hashOf(raw)))
    }

    @Test
    fun `wrong bytes fail`() {
        val raw = byteArrayOf(1, 2, 3, 4)
        assertFalse(biometricTokenMatches(byteArrayOf(9, 9, 9), hashOf(raw)))
    }

    @Test
    fun `null and blank fail`() {
        assertFalse(biometricTokenMatches(null, hashOf(byteArrayOf(1))))
        assertFalse(biometricTokenMatches(byteArrayOf(1), ""))
        assertFalse(biometricTokenMatches(byteArrayOf(1), "zz"))
    }
}
