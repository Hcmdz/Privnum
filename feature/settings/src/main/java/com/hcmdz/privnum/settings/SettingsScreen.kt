package com.hcmdz.privnum.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hcmdz.privnum.data.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenLock: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val region = (context.getSystemService(android.content.Context.TELEPHONY_SERVICE)
                as android.telephony.TelephonyManager).simCountryIso?.uppercase() ?: "US"
            context.contentResolver.openInputStream(uri)?.use { stream ->
                viewModel.importVcf(stream, region) { message = it }
            }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/vcard")
    ) { uri ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                viewModel.exportVcf(stream) { message = it }
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("General")
            SwitchRow("Show incoming popup", state.incomingPopup) { viewModel.setIncoming(it) }
            SwitchRow("Show outgoing popup", state.outgoingPopup) { viewModel.setOutgoing(it) }

            Text("Theme")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = state.themeMode == mode,
                        onClick = { viewModel.setTheme(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                        label = {
                            Text(
                                when (mode) {
                                    ThemeMode.LIGHT -> "Light"
                                    ThemeMode.DARK -> "Dark"
                                    ThemeMode.SYSTEM -> "System"
                                }
                            )
                        }
                    )
                }
            }

            Text("Security")
            ListItem(
                headlineContent = { Text("Passcode lock") },
                trailingContent = {
                    OutlinedButton(onClick = onOpenLock) { Text("Manage") }
                }
            )
            var autoLockExpanded by remember { mutableStateOf(false) }
            ListItem(
                headlineContent = { Text("Auto-lock") },
                supportingContent = {
                    Text(
                        when (state.autoLockTimeout) {
                            com.hcmdz.privnum.data.AutoLockTimeout.DISABLED -> "Disabled"
                            com.hcmdz.privnum.data.AutoLockTimeout.IMMEDIATELY -> "Immediately"
                            com.hcmdz.privnum.data.AutoLockTimeout.MIN_1 -> "1 minute"
                            com.hcmdz.privnum.data.AutoLockTimeout.MIN_5 -> "5 minutes"
                            com.hcmdz.privnum.data.AutoLockTimeout.HOUR_1 -> "1 hour"
                            com.hcmdz.privnum.data.AutoLockTimeout.HOUR_5 -> "5 hours"
                        }
                    )
                },
                trailingContent = {
                    OutlinedButton(onClick = { autoLockExpanded = true }) { Text("Change") }
                }
            )
            androidx.compose.material3.DropdownMenu(
                expanded = autoLockExpanded,
                onDismissRequest = { autoLockExpanded = false }
            ) {
                com.hcmdz.privnum.data.AutoLockTimeout.entries.forEach { timeout ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(timeout.name) },
                        onClick = {
                            viewModel.setAutoLock(timeout)
                            autoLockExpanded = false
                        }
                    )
                }
            }

            Text("Contacts backup")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("text/*")) },
                    modifier = Modifier.weight(1f)
                ) { Text("Import VCF") }
                Button(
                    onClick = { exportLauncher.launch("contacts.vcf") },
                    enabled = state.contactCount > 0,
                    modifier = Modifier.weight(1f)
                ) { Text("Export VCF") }
            }
            Text("${state.contactCount} contacts available")

            message?.let { Text(it) }

            Text("Useful links")
            Text("README: github.com/Hcmdz/Privnum")
            Text("Issues: github.com/Hcmdz/Privnum/issues")
        }
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = checked, onCheckedChange = onChange)
            }
        }
    )
}
