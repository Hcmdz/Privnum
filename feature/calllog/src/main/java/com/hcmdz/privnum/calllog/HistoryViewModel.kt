package com.hcmdz.privnum.calllog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hcmdz.privnum.caller.CallHistoryRepository
import com.hcmdz.privnum.caller.HistoryEntry
import com.hcmdz.privnum.caller.HistoryResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class HistoryUiState(
    val isLoading: Boolean = true,
    val entries: List<HistoryEntry> = emptyList(),
    val hasPermission: Boolean = false,
    val unavailable: Boolean = false
)

fun formatHistoryDuration(totalSeconds: Long): String {
    if (totalSeconds <= 0) return "0s"
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return buildString {
        if (hours > 0) append("${hours}h ")
        if (minutes > 0 || hours > 0) append("${minutes}m ")
        append("${seconds}s")
    }.trim()
}

fun formatHistoryDate(epochMillis: Long, locale: Locale): String =
    runCatching {
        val dateTime = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
        dateTime.format(DateTimeFormatter.ofPattern("MMM d, HH:mm", locale))
    }.getOrDefault("")

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val history: CallHistoryRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    fun setPermission(granted: Boolean) {
        if (granted) {
            refresh()
        } else {
            _uiState.update { it.copy(isLoading = false, hasPermission = false) }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, unavailable = false) }
            when (val result = history.getRecent(100)) {
                is HistoryResult.Available -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        entries = result.entries,
                        hasPermission = true,
                        unavailable = false
                    )
                }
                HistoryResult.Unavailable -> _uiState.update {
                    it.copy(isLoading = false, hasPermission = true, unavailable = true)
                }
            }
        }
    }
}
