package com.hcmdz.privnum.lock

import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hcmdz.privnum.ui.resolveText

@Composable
fun LockScreen(
    startSetup: Boolean = false,
    onUnlocked: () -> Unit,
    viewModel: LockViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val errorText = state.error?.resolveText()
    val biometricTitle = stringResource(R.string.lock_biometric_prompt_title)
    val usePinText = stringResource(R.string.lock_biometric_prompt_negative)

    if (startSetup) {
        // Setup flow: enter SETUP mode on entry, complete via setupDone.
        // Never consults the VERIFY gate below.
        LaunchedEffect(Unit) {
            viewModel.startSetup()
        }
        LaunchedEffect(state.setupComplete) {
            if (state.setupComplete) onUnlocked()
        }
        if (!state.setupComplete) {
            PinPad(
                title = if (state.mode == LockMode.CONFIRM) {
                    stringResource(R.string.lock_confirm_pin)
                } else {
                    stringResource(R.string.lock_choose_pin)
                },
                pin = state.pin,
                error = errorText,
                onDigit = { viewModel.digit(it) },
                onBackspace = { viewModel.backspace() },
                biometricRow = null
            )
        }
        return
    }

    var verificationReady by remember { mutableStateOf(false) }
    LaunchedEffect(state.unlocked, verificationReady) {
        if (verificationReady && state.unlocked) onUnlocked()
    }
    LaunchedEffect(Unit) {
        viewModel.reenterVerify()
        (context as? FragmentActivity)?.let { viewModel.refreshBiometric(it) }
        verificationReady = true
    }
    var autoPrompted by remember { mutableStateOf(false) }

    fun launchBiometric() {
        val activity = context as? FragmentActivity ?: return
        val crypto = viewModel.biometricCryptoCipher()?.let {
            BiometricPrompt.CryptoObject(it)
        }
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    viewModel.onBiometricSuccess(result.cryptoObject)
                }
            }
        )
        if (crypto != null) {
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(biometricTitle)
                    .setNegativeButtonText(usePinText)
                    .build(),
                crypto
            )
        } else {
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(biometricTitle)
                    .setNegativeButtonText(usePinText)
                    .build()
            )
        }
    }
    LaunchedEffect(state.biometricAvailable, state.biometricEnabled) {
        if (!autoPrompted &&
            shouldAutoPromptBiometric(state.biometricAvailable, state.biometricEnabled)
        ) {
            autoPrompted = true
            launchBiometric()
        }
    }

    if (state.unlocked) return

    PinPad(
        title = stringResource(R.string.lock_enter_pin),
        pin = state.pin,
        error = errorText,
        onDigit = { viewModel.digit(it) },
        onBackspace = { viewModel.backspace() },
        biometricRow = {
            if (state.biometricAvailable && state.biometricEnabled) {
                Button(onClick = { launchBiometric() }) {
                    Text(stringResource(R.string.lock_unlock_with_biometrics))
                }
            }
        }
    )
}

@Composable
private fun PinPad(
    title: String,
    pin: String,
    error: String?,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    biometricRow: (@Composable () -> Unit)?
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
        ) {
            Text(title)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(4) { index ->
                    Text(if (index < pin.length) "●" else "○")
                }
            }
            error?.let { Text(it) }
            val digits = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")
            digits.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { key ->
                        when (key) {
                            "" -> OutlinedButton(onClick = {}, enabled = false) { Text("") }
                            "⌫" -> OutlinedButton(onClick = onBackspace) { Text("⌫") }
                            else -> OutlinedButton(onClick = { onDigit(key) }) { Text(key) }
                        }
                    }
                }
            }
            biometricRow?.invoke()
        }
    }
}
