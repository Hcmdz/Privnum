package com.hcmdz.privnum.caller

import android.content.Context
import android.provider.CallLog
import com.hcmdz.privnum.data.PhoneNumberUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class CallLogRow(
    val number: String,
    val dateMillis: Long,
    val durationSeconds: Long,
    val type: Int
)

@Singleton
class CallLogDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun getRecent(limit: Int): Result<List<CallLogRow>> = withContext(Dispatchers.IO) {
        try {
            val rows = mutableListOf<CallLogRow>()
            // The limit param is ignored by providers that predate it:
            // take(limit) below is the backstop, not the param.
            val uri = CallLog.Calls.CONTENT_URI.buildUpon()
                .appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY, limit.toString())
                .build()
            context.contentResolver.query(
                uri,
                // Explicit projection: a null projection returns provider-dependent
                // columns that break on some ROMs.
                arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION,
                    CallLog.Calls.TYPE
                ),
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                val numberIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)
                val durationIdx = cursor.getColumnIndex(CallLog.Calls.DURATION)
                val typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE)
                while (cursor.moveToNext()) {
                    // Providers may store locale digits: fold to ASCII first so
                    // display, matching and prefill all see the same string.
                    val number = PhoneNumberUtils.asciiDigits(
                        cursor.getString(numberIdx).orEmpty()
                    )
                    // A row without a single digit carries nothing actionable.
                    if (number.none { it.isDigit() }) continue
                    rows += CallLogRow(
                        number = number,
                        dateMillis = cursor.getLong(dateIdx),
                        // DURATION is seconds, not milliseconds.
                        durationSeconds = cursor.getLong(durationIdx),
                        type = cursor.getInt(typeIdx)
                    )
                    if (rows.size >= limit) break
                }
            }
            Result.success(rows)
        } catch (e: SecurityException) {
            // Granted-but-blocked happens on hardened ROMs: report, never crash.
            Result.failure(e)
        }
    }
}
