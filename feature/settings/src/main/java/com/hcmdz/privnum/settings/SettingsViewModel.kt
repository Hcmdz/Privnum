package com.hcmdz.privnum.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hcmdz.privnum.data.AutoLockTimeout
import com.hcmdz.privnum.data.ContactRepository
import com.hcmdz.privnum.data.PasscodeStore
import com.hcmdz.privnum.data.SettingsStore
import com.hcmdz.privnum.data.ThemeMode
import com.hcmdz.privnum.data.VcfMapper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val incomingPopup: Boolean = true,
    val outgoingPopup: Boolean = true,
    val contactCount: Int = 0,
    val autoLockTimeout: AutoLockTimeout = AutoLockTimeout.MIN_5,
    val message: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val passcode: PasscodeStore,
    private val repository: ContactRepository
) : ViewModel() {
    private val autoLock = MutableStateFlow(passcode.autoLockTimeout)

    val uiState: StateFlow<SettingsUiState> =
        combine(
            settings.themeMode,
            settings.incomingPopup,
            settings.outgoingPopup,
            repository.observeContacts(),
            autoLock
        ) { theme, incoming, outgoing, contacts, lock ->
            SettingsUiState(theme, incoming, outgoing, contacts.size, lock)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun setIncoming(enabled: Boolean) {
        viewModelScope.launch { settings.setIncomingPopup(enabled) }
    }

    fun setOutgoing(enabled: Boolean) {
        viewModelScope.launch { settings.setOutgoingPopup(enabled) }
    }

    fun setAutoLock(timeout: AutoLockTimeout) {
        passcode.autoLockTimeout = timeout
        autoLock.value = timeout
    }

    fun importVcf(
        resolver: ContentResolver,
        uri: Uri,
        defaultRegion: String,
        done: (String) -> Unit
    ) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
            }
            if (text.isNullOrBlank()) {
                done("Cannot read the selected file")
                return@launch
            }
            val contacts = VcfMapper.parseVcf(text, defaultRegion)
            if (contacts.isEmpty()) {
                done("No contacts found in the selected file")
                return@launch
            }
            val added = repository.addAll(contacts)
            done("$added contacts imported")
        }
    }

    fun exportVcf(resolver: ContentResolver, uri: Uri, done: (String) -> Unit) {
        viewModelScope.launch {
            val contacts = repository.getAll()
            if (contacts.isEmpty()) {
                done("No contacts to export")
                return@launch
            }
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    resolver.openOutputStream(uri)?.bufferedWriter()?.use {
                        it.write(VcfMapper.contactsToVcf(contacts))
                    } != null
                }.getOrDefault(false)
            }
            done(if (ok) "${contacts.size} contacts exported" else "Export failed")
        }
    }
}
