package com.hcmdz.privnum.lock

import androidx.biometric.BiometricManager
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hcmdz.privnum.data.AutoLockTimeout
import com.hcmdz.privnum.data.PasscodeStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LockUiState(
    val mode: LockMode = LockMode.VERIFY,
    val pin: String = "",
    val error: String? = null,
    val biometricAvailable: Boolean = false,
    val unlocked: Boolean = false,
    val autoLockTimeout: AutoLockTimeout = AutoLockTimeout.MIN_5,
    val passcodeEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val setupComplete: Boolean = false
)

enum class LockMode { VERIFY, SETUP, CONFIRM }

@HiltViewModel
class LockViewModel @Inject constructor(
    private val store: PasscodeStore
) : ViewModel() {
    private val _state = MutableStateFlow(
        LockUiState(
            passcodeEnabled = store.passcodeEnabled,
            biometricEnabled = store.biometricEnabled,
            autoLockTimeout = store.autoLockTimeout,
            unlocked = !store.passcodeEnabled || !store.shouldLock()
        )
    )
    val state: StateFlow<LockUiState> = _state.asStateFlow()

    private var firstPin = ""

    fun refreshBiometric(activity: FragmentActivity) {
        val manager = BiometricManager.from(activity)
        val available = manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS
        _state.update { it.copy(biometricAvailable = available) }
    }

    fun digit(d: String) {
        val current = _state.value
        if (current.pin.length >= 4) return
        val pin = current.pin + d
        _state.update { it.copy(pin = pin, error = null) }
        if (pin.length == 4) submit(pin)
    }

    fun backspace() {
        _state.update { it.copy(pin = it.pin.dropLast(1)) }
    }

    private fun submit(pin: String) {
        val current = _state.value
        when (current.mode) {
            LockMode.VERIFY -> {
                if (store.isLockedOut()) {
                    _state.update { it.copy(error = "Too many attempts, try again later", pin = "") }
                    return
                }
                viewModelScope.launch {
                    if (store.verifyPin(pin)) {
                        store.lastUnlockedAt = System.currentTimeMillis()
                        _state.update { it.copy(unlocked = true, pin = "") }
                    } else {
                        _state.update { it.copy(error = "Invalid PIN", pin = "") }
                    }
                }
            }
            LockMode.SETUP -> {
                firstPin = pin
                _state.update { it.copy(mode = LockMode.CONFIRM, pin = "") }
            }
            LockMode.CONFIRM -> {
                if (pin == firstPin) {
                    viewModelScope.launch {
                        store.setPin(pin)
                        store.passcodeEnabled = true
                        store.lastUnlockedAt = System.currentTimeMillis()
                        _state.update {
                            it.copy(
                                unlocked = true, pin = "", passcodeEnabled = true,
                                mode = LockMode.VERIFY, setupComplete = true
                            )
                        }
                    }
                } else {
                    firstPin = ""
                    _state.update { it.copy(error = "PINs do not match", pin = "", mode = LockMode.SETUP) }
                }
            }
        }
    }

    fun startSetup() {
        firstPin = ""
        _state.update { it.copy(mode = LockMode.SETUP, pin = "", error = null, setupComplete = false) }
    }

    /**
     * Called when entering verify mode: a stale unlocked=true from a previous
     * unlock (shared ViewModelStoreOwner) would pop the screen instantly.
     */
    fun reenterVerify() {
        _state.update { it.copy(unlocked = false, pin = "", error = null) }
    }

    fun disable() {
        viewModelScope.launch {
            store.clear()
            _state.update {
                it.copy(passcodeEnabled = false, biometricEnabled = false, unlocked = true, setupComplete = false)
            }
        }
    }

    fun setBiometric(enabled: Boolean) {
        store.biometricEnabled = enabled
        _state.update { it.copy(biometricEnabled = enabled) }
    }

    fun setAutoLock(timeout: AutoLockTimeout) {
        store.autoLockTimeout = timeout
        _state.update { it.copy(autoLockTimeout = timeout) }
    }

    fun onBiometricSuccess() {
        store.failedAttempts = 0
        store.forceLocked = false
        store.lastUnlockedAt = System.currentTimeMillis()
        _state.update { it.copy(unlocked = true) }
    }
}
