package com.hcmdz.privnum.data

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class HexCodecTest {

    @Test
    fun `hex round trip preserves high-bit bytes`() {
        val original = byteArrayOf(0xFF.toByte(), 0x80.toByte(), 0x00, 0x7F)
        assertArrayEquals(original, original.toHex().hexToBytes())
    }

    @Test
    fun `hex encoding is two chars per byte`() {
        val original = ByteArray(16) { 0xAB.toByte() }
        assert(original.toHex().length == 32)
    }
}
