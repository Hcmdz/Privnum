package com.hcmdz.privnum.caller

import android.provider.CallLog
import com.hcmdz.privnum.data.ContactRepository
import com.hcmdz.privnum.data.PhoneNumberUtils
import com.hcmdz.privnum.data.SettingsStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

enum class HistoryDirection {
    INCOMING,
    OUTGOING,
    MISSED,
    OTHER
}

data class HistoryEntry(
    val rawNumber: String,
    val contactId: Long?,
    val contactName: String?,
    val dateMillis: Long,
    val durationSeconds: Long,
    val direction: HistoryDirection
)

sealed interface HistoryResult {
    data class Available(val entries: List<HistoryEntry>) : HistoryResult
    data object Unavailable : HistoryResult
}

@Singleton
class CallHistoryRepository @Inject constructor(
    private val dataSource: CallLogDataSource,
    private val contacts: ContactRepository,
    private val settings: SettingsStore
) {
    suspend fun getRecent(limit: Int): HistoryResult {
        val rows = dataSource.getRecent(limit).getOrElse { return HistoryResult.Unavailable }
        val region = settings.defaultRegion.first()
        return HistoryResult.Available(rows.map { row ->
            val full = row.number.normalizeToFull(region)
                ?: row.number.filter { it == '+' || it.isDigit() }.takeIf { it.isNotEmpty() }
            val contact = full?.let { contacts.findByNumberAnyForm(it) }
            HistoryEntry(
                rawNumber = row.number,
                contactId = contact?.id,
                contactName = contact?.name,
                dateMillis = row.dateMillis,
                durationSeconds = row.durationSeconds,
                direction = row.type.toHistoryDirection()
            )
        })
    }

    private fun String.normalizeToFull(region: String?): String? {
        // Log numbers arrive as dialed (+ prefix, spaces, short codes):
        // normalize to the stored full form before matching. Without a
        // region (SIM-less device) parsing fails, so the caller falls back
        // to raw digits, which still match stored full numbers exactly.
        val parsed = PhoneNumberUtils.parseForSave(this, region ?: "") ?: return null
        return parsed.fullNumber
    }
}

internal fun Int.toHistoryDirection(): HistoryDirection = when (this) {
    CallLog.Calls.INCOMING_TYPE -> HistoryDirection.INCOMING
    CallLog.Calls.OUTGOING_TYPE -> HistoryDirection.OUTGOING
    CallLog.Calls.MISSED_TYPE -> HistoryDirection.MISSED
    else -> HistoryDirection.OTHER
}
