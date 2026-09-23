package com.hcmdz.privnum.lock

import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun LockScreen(
    startSetup: Boolean = false,
    onUnlocked: () -> Unit,
    viewModel: LockViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

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
                title = if (state.mode == LockMode.CONFIRM) "Confirm PIN" else "Choose a 4-digit PIN",
                pin = state.pin,
                error = state.error,
                onDigit = { viewModel.digit(it) },
                onBackspace = { viewModel.backspace() },
                biometricRow = null
            )
        }
        return
    }

    LaunchedEffect(state.unlocked) {
        if (state.unlocked) onUnlocked()
    }
    LaunchedEffect(Unit) {
        viewModel.reenterVerify()
        (context as? FragmentActivity)?.let { viewModel.refreshBiometric(it) }
    }

    if (state.unlocked) return

    PinPad(
        title = "Enter PIN",
        pin = state.pin,
        error = state.error,
        onDigit = { viewModel.digit(it) },
        onBackspace = { viewModel.backspace() },
        biometricRow = {
            if (state.biometricAvailable && state.biometricEnabled) {
                Button(onClick = {
                    val activity = context as? FragmentActivity ?: return@Button
                    val prompt = BiometricPrompt(
                        activity,
                        ContextCompat.getMainExecutor(activity),
                        object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(
                                result: BiometricPrompt.AuthenticationResult
                            ) {
                                viewModel.onBiometricSuccess()
                            }
                        }
                    )
                    prompt.authenticate(
                        BiometricPrompt.PromptInfo.Builder()
                            .setTitle("Unlock Privnum")
                            .setNegativeButtonText("Use PIN")
                            .build()
                    )
                }) {
                    Text("Unlock with biometrics")
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
