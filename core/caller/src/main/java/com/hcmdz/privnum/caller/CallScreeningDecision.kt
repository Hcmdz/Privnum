package com.hcmdz.privnum.caller

import com.hcmdz.privnum.data.Contact

enum class CallDirection {
    INCOMING,
    OUTGOING,
    UNKNOWN
}

data class ScreeningDecision(
    val allow: Boolean,
    val contact: Contact?
)

object CallScreeningDecision {
    fun decide(contact: Contact?, direction: CallDirection): ScreeningDecision {
        return ScreeningDecision(allow = true, contact = contact)
    }

    fun shouldShowOverlay(decision: ScreeningDecision, incomingPopup: Boolean, outgoingPopup: Boolean, direction: CallDirection): Boolean {
        if (decision.contact == null) return false
        return when (direction) {
            CallDirection.INCOMING -> incomingPopup
            CallDirection.OUTGOING -> outgoingPopup
            CallDirection.UNKNOWN -> false
        }
    }
}
