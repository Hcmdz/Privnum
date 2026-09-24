package com.hcmdz.privnum.caller

import com.hcmdz.privnum.data.Contact
import com.hcmdz.privnum.data.PhoneNumberRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun contact() = Contact(
    name = "Test",
    numbers = listOf(PhoneNumberRef("1", "1", "US", primary = true))
)

class CallScreeningDecisionTest {

    @Test
    fun `known caller is always allowed`() {
        val decision = CallScreeningDecision.decide(contact(), CallDirection.INCOMING)
        assertTrue(decision.allow)
        assertEquals(contact(), decision.contact)
    }

    @Test
    fun `unknown caller is allowed with null contact`() {
        val decision = CallScreeningDecision.decide(null, CallDirection.OUTGOING)
        assertTrue(decision.allow)
    }

    @Test
    fun `overlay shows only for known contact with matching toggle`() {
        val known = CallScreeningDecision.decide(contact(), CallDirection.INCOMING)
        assertTrue(CallScreeningDecision.shouldShowOverlay(known, true, false, CallDirection.INCOMING))
        assertFalse(CallScreeningDecision.shouldShowOverlay(known, false, false, CallDirection.INCOMING))
        assertFalse(CallScreeningDecision.shouldShowOverlay(known, false, true, CallDirection.INCOMING))
    }

    @Test
    fun `overlay never shows for unknown number or unknown direction`() {
        val unknown = CallScreeningDecision.decide(null, CallDirection.INCOMING)
        assertFalse(CallScreeningDecision.shouldShowOverlay(unknown, true, true, CallDirection.INCOMING))
        val known = CallScreeningDecision.decide(contact(), CallDirection.UNKNOWN)
        assertFalse(CallScreeningDecision.shouldShowOverlay(known, true, true, CallDirection.UNKNOWN))
    }

    @Test
    fun `outgoing toggle gates outgoing overlay`() {
        val known = CallScreeningDecision.decide(contact(), CallDirection.OUTGOING)
        assertTrue(CallScreeningDecision.shouldShowOverlay(known, false, true, CallDirection.OUTGOING))
        assertFalse(CallScreeningDecision.shouldShowOverlay(known, true, false, CallDirection.OUTGOING))
    }
}
