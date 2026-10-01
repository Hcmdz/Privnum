package com.hcmdz.privnum.caller

import android.provider.CallLog
import org.junit.Assert.assertEquals
import org.junit.Test

class CallHistoryMappingTest {

    @Test
    fun `system types map to directions`() {
        assertEquals(HistoryDirection.INCOMING, CallLog.Calls.INCOMING_TYPE.toHistoryDirection())
        assertEquals(HistoryDirection.OUTGOING, CallLog.Calls.OUTGOING_TYPE.toHistoryDirection())
        assertEquals(HistoryDirection.MISSED, CallLog.Calls.MISSED_TYPE.toHistoryDirection())
    }

    @Test
    fun `voicemail rejected blocked and unknown map to other`() {
        assertEquals(HistoryDirection.OTHER, CallLog.Calls.VOICEMAIL_TYPE.toHistoryDirection())
        assertEquals(HistoryDirection.OTHER, CallLog.Calls.REJECTED_TYPE.toHistoryDirection())
        assertEquals(HistoryDirection.OTHER, CallLog.Calls.BLOCKED_TYPE.toHistoryDirection())
        assertEquals(
            HistoryDirection.OTHER,
            CallLog.Calls.ANSWERED_EXTERNALLY_TYPE.toHistoryDirection()
        )
        assertEquals(HistoryDirection.OTHER, 0.toHistoryDirection())
        assertEquals(HistoryDirection.OTHER, 99.toHistoryDirection())
    }
}
