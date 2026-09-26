package com.hcmdz.privnum.settings

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.biometric.BiometricManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hcmdz.privnum.data.AutoLockTimeout
import com.hcmdz.privnum.data.ContactPhotoStore
import com.hcmdz.privnum.data.ContactRepository
import com.hcmdz.privnum.data.PasscodeStore
import com.hcmdz.privnum.data.SettingsStore
import com.hcmdz.privnum.data.ThemeMode
import com.hcmdz.privnum.data.VcfMapper
import com.hcmdz.privnum.ui.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    val passcodeSet: Boolean = false,
    val biometricEnabled: Boolean = false,
    val biometricAvailable: Boolean = false,
    val defaultRegion: String? = null,
    val dynamicColor: Boolean = true,
    val amoledBlack: Boolean = false,
    val message: UiText? = null
)

enum class PinCheck { OK, INVALID, LOCKED_OUT }

/** ISO regions offered as parsing default (null = automatic from SIM). */
val DEFAULT_REGION_OPTIONS = listOf("DZ", "FR", "MA", "TN", "ES", "IT", "DE", "GB", "US", "CA")

fun AutoLockTimeout.label(): UiText = when (this) {
    AutoLockTimeout.DISABLED -> UiText.Resource(R.string.settings_auto_lock_disabled)
    AutoLockTimeout.IMMEDIATELY -> UiText.Resource(R.string.settings_auto_lock_immediately)
    AutoLockTimeout.MIN_1 -> UiText.Resource(R.string.settings_auto_lock_minute)
    AutoLockTimeout.MIN_5 -> UiText.Resource(R.string.settings_auto_lock_minutes)
    AutoLockTimeout.HOUR_1 -> UiText.Resource(R.string.settings_auto_lock_hour)
    AutoLockTimeout.HOUR_5 -> UiText.Resource(R.string.settings_auto_lock_hours)
}

private data class SecurityState(
    val passcodeSet: Boolean = false,
    val biometricEnabled: Boolean = false,
    val biometricAvailable: Boolean = false
)

private data class AuxState(
    val lock: AutoLockTimeout = AutoLockTimeout.MIN_5,
    val region: String? = null,
    val sec: SecurityState = SecurityState(),
    val msg: UiText? = null,
    val dynamicColor: Boolean = true,
    val amoledBlack: Boolean = false
)

private data class ThemePrefs(
    val dynamicColor: Boolean = true,
    val amoledBlack: Boolean = false
)

private fun mergeAux(aux: AuxState, prefs: ThemePrefs): AuxState =
    aux.copy(dynamicColor = prefs.dynamicColor, amoledBlack = prefs.amoledBlack)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val passcode: PasscodeStore,
    private val repository: ContactRepository,
    private val photos: ContactPhotoStore,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val autoLock = MutableStateFlow(passcode.autoLockTimeout)
    private val security = MutableStateFlow(readSecurity())
    private val message = MutableStateFlow<UiText?>(null)

    val uiState: StateFlow<SettingsUiState> =
        combine(
            settings.themeMode,
            settings.incomingPopup,
            settings.outgoingPopup,
            repository.observeContacts(),
            combine(
                combine(autoLock, settings.defaultRegion, security, message, ::AuxState),
                combine(settings.dynamicColor, settings.amoledBlack, ::ThemePrefs),
                ::mergeAux
            )
        ) { theme, incoming, outgoing, contacts, aux ->
            SettingsUiState(
                themeMode = theme,
                incomingPopup = incoming,
                outgoingPopup = outgoing,
                contactCount = contacts.size,
                autoLockTimeout = aux.lock,
                passcodeSet = aux.sec.passcodeSet,
                biometricEnabled = aux.sec.biometricEnabled,
                biometricAvailable = aux.sec.biometricAvailable,
                defaultRegion = aux.region,
                dynamicColor = aux.dynamicColor,
                amoledBlack = aux.amoledBlack,
                message = aux.msg
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settings.setDynamicColor(enabled) }
    }

    fun setAmoledBlack(enabled: Boolean) {
        viewModelScope.launch { settings.setAmoledBlack(enabled) }
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

    fun setDefaultRegion(region: String?) {
        viewModelScope.launch { settings.setDefaultRegion(region) }
    }

    fun setBiometric(enabled: Boolean) {
        passcode.biometricEnabled = enabled
        security.value = readSecurity()
    }

    /** Re-read PIN/biometric state (PIN can change via the lock setup screen). */
    fun refreshSecurity() {
        security.value = readSecurity()
    }

    fun isLockedOutNow(): Boolean = passcode.isLockedOut()

    fun consumeMessage() {
        message.value = null
    }

    fun importVcf(resolver: ContentResolver, uri: Uri, defaultRegion: String) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
            }
            if (text.isNullOrBlank()) {
                message.value = UiText.Resource(R.string.settings_message_cannot_read_file)
                return@launch
            }
            val contacts = VcfMapper.parseVcf(text, defaultRegion)
            if (contacts.isEmpty()) {
                message.value = UiText.Resource(R.string.settings_message_no_contacts_found)
                return@launch
            }
            val normalized = withContext(Dispatchers.IO) {
                contacts.map { contact ->
                    if (contact.photo.startsWith("data:image/")) {
                        photos.importDataUri(contact.photo)?.let { contact.copy(photo = it) }
                            ?: contact
                    } else contact
                }
            }
            val added = repository.addAll(normalized)
            message.value = UiText.Plural(
                R.plurals.settings_contacts_imported,
                added,
                listOf(added)
            )
        }
    }

    fun exportVcf(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            val contacts = repository.getAll()
            if (contacts.isEmpty()) {
                message.value = UiText.Resource(R.string.settings_message_no_contacts_to_export)
                return@launch
            }
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val withPhotos = contacts.map { contact ->
                        contact.copy(photo = photos.photoDataUri(contact.photo).orEmpty())
                    }
                    resolver.openOutputStream(uri)?.bufferedWriter()?.use {
                        it.write(VcfMapper.contactsToVcf(withPhotos))
                    } != null
                }.getOrDefault(false)
            }
            message.value = if (ok) {
                UiText.Plural(
                    R.plurals.settings_contacts_exported,
                    contacts.size,
                    listOf(contacts.size)
                )
            } else {
                UiText.Resource(R.string.settings_message_export_failed)
            }
        }
    }

    fun clearAllContacts() {
        viewModelScope.launch {
            repository.clearAll()
            message.value = UiText.Resource(R.string.settings_message_contacts_deleted)
        }
    }

    fun checkPin(pin: String, done: (PinCheck) -> Unit) {
        viewModelScope.launch {
            if (passcode.isLockedOut()) {
                done(PinCheck.LOCKED_OUT)
                return@launch
            }
            done(
                if (withContext(Dispatchers.Default) { passcode.verifyPin(pin) })
                    PinCheck.OK
                else PinCheck.INVALID
            )
        }
    }

    fun removePasscode(pin: String, done: (PinCheck) -> Unit) {
        viewModelScope.launch {
            if (passcode.isLockedOut()) {
                done(PinCheck.LOCKED_OUT)
                return@launch
            }
            if (!withContext(Dispatchers.Default) { passcode.verifyPin(pin) }) {
                done(PinCheck.INVALID)
                return@launch
            }
            passcode.clear()
            security.value = readSecurity()
            message.value = UiText.Resource(R.string.settings_message_passcode_removed)
            done(PinCheck.OK)
        }
    }

    private fun readSecurity(): SecurityState {
        val available = BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS
        return SecurityState(
            passcodeSet = passcode.passcodeEnabled && passcode.hasPin(),
            biometricEnabled = passcode.biometricEnabled,
            biometricAvailable = available
        )
    }
}
