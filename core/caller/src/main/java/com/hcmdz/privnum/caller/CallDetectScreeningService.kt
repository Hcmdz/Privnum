package com.hcmdz.privnum.caller

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log

class CallDetectScreeningService : CallScreeningService() {
    override fun onScreenCall(details: Call.Details) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (details.callDirection == Call.Details.DIRECTION_INCOMING || details.callDirection == Call.Details.DIRECTION_OUTGOING) {
                val response = CallResponse.Builder()
                response.setDisallowCall(false)
                response.setRejectCall(false)
                response.setSilenceCall(false)
                response.setSkipCallLog(false)
                response.setSkipNotification(false)

                val callType = if (details.callDirection == Call.Details.DIRECTION_INCOMING) {
                    "Incoming"
                } else {
                    "Outgoing"
                }

                Log.d(
                    "CallDetectScreeningService",
                    "$callType call detected: ${details.handle.schemeSpecificPart}"
                )

                // Store the phone number for both incoming and outgoing calls
                CallReceiver.callServiceNumber = details.handle.schemeSpecificPart

                respondToCall(details, response.build())
            }
        }
    }
}