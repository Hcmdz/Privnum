package com.hcmdz.privnum.lock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BiometricPromptTest {

    @Test
    fun `prompts only when available and enabled`() {
        assertTrue(shouldAutoPromptBiometric(true, true))
        assertFalse(shouldAutoPromptBiometric(false, true))
        assertFalse(shouldAutoPromptBiometric(true, false))
        assertFalse(shouldAutoPromptBiometric(false, false))
    }
}
