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
    val unavailable: Boolean = false,
    val loadedOnce: Boolean = false
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

    /**
     * The entry re-composes on every visit, so this runs more than once per
     * screen. Re-reading the provider each time showed a spinner and dropped
     * the visible list, so the read only happens while there is nothing to
     * show. The ViewModel is scoped to the activity, so the entries survive
     * leaving the screen; a revoked permission still clears the view.
     */
    fun setPermission(granted: Boolean) {
        if (granted) {
            // loadedOnce also covers the unavailable case: an empty list is
            // not proof that nothing has been read yet.
            if (!_uiState.value.loadedOnce) refresh()
        } else {
            _uiState.value = HistoryUiState(isLoading = false, hasPermission = false)
        }
    }

    fun refresh() {
        // Keep the current list visible while re-reading: only a first load
        // has nothing to fall back on.
        val silent = _uiState.value.entries.isNotEmpty()
        viewModelScope.launch {
            if (!silent) _uiState.update { it.copy(isLoading = true, unavailable = false) }
            when (val result = history.getRecent(100)) {
                is HistoryResult.Available -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        entries = result.entries,
                        hasPermission = true,
                        unavailable = false,
                        loadedOnce = true
                    )
                }
                HistoryResult.Unavailable -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        hasPermission = true,
                        unavailable = true,
                        loadedOnce = true
                    )
                }
            }
        }
    }
}
