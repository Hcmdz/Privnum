package com.hcmdz.privnum.settings

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hcmdz.privnum.caller.CallerPermissions
import com.hcmdz.privnum.caller.ScreeningRole
import com.hcmdz.privnum.data.AutoLockTimeout
import com.hcmdz.privnum.data.Countries
import com.hcmdz.privnum.data.ThemeMode
import com.hcmdz.privnum.ui.ContactAvatar

private enum class PinDialogMode { EXPORT, REMOVE }

private data class CallStatus(
    val phoneState: Boolean = false,
    val overlay: Boolean = false,
    val screeningRole: Boolean = false
)

private fun readCallStatus(context: Context) = CallStatus(
    phoneState = CallerPermissions.hasPhoneState(context),
    overlay = CallerPermissions.hasOverlay(context),
    screeningRole = ScreeningRole.isHeld(context)
)

private fun defaultRegionLabel(region: String?): String =
    region ?: "Automatic (SIM)"

private fun simRegion(context: Context): String = Countries.simRegion(context)

private fun appVersion(context: Context): String {
    val info = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }.getOrNull() ?: return "unknown"
    @Suppress("DEPRECATION")
    val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        info.longVersionCode
    } else {
        info.versionCode.toLong()
    }
    return "${info.versionName} ($code)"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenLock: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val snackbar = remember { SnackbarHostState() }

    var callStatus by remember { mutableStateOf(readCallStatus(context)) }
    var autoLockDialog by remember { mutableStateOf(false) }
    var regionDialog by remember { mutableStateOf(false) }
    var pinDialog by remember { mutableStateOf<PinDialogMode?>(null) }
    var deleteConfirm by remember { mutableStateOf(false) }
    var overlayRationale by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshSecurity()
        callStatus = readCallStatus(context)
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val region = state.defaultRegion ?: simRegion(context)
            viewModel.importVcf(context.contentResolver, uri, region)
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/vcard")
    ) { uri ->
        if (uri != null) viewModel.exportVcf(context.contentResolver, uri)
    }
    val phoneStateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { callStatus = readCallStatus(context) }
    val systemSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { callStatus = readCallStatus(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("General", style = MaterialTheme.typography.titleSmall)
            SwitchRow("Show incoming popup", state.incomingPopup) { viewModel.setIncoming(it) }
            SwitchRow("Show outgoing popup", state.outgoingPopup) { viewModel.setOutgoing(it) }

            Text("Call detection", style = MaterialTheme.typography.titleSmall)
            StatusRow(
                title = "Call screening role",
                granted = callStatus.screeningRole,
                actionLabel = "Request",
                onAction = {
                    ScreeningRole.requestIntent(context)?.let(systemSettingsLauncher::launch)
                }
            )
            StatusRow(
                title = "Display over other apps",
                granted = callStatus.overlay,
                actionLabel = "Grant",
                onAction = { overlayRationale = true }
            )
            StatusRow(
                title = "Phone state permission",
                granted = callStatus.phoneState,
                actionLabel = "Grant",
                onAction = {
                    phoneStateLauncher.launch(android.Manifest.permission.READ_PHONE_STATE)
                }
            )

            Text("Theme", style = MaterialTheme.typography.titleSmall)
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
            val dynamicSupported =
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
            SwitchRow(
                title = "Dynamic colors",
                supporting = if (dynamicSupported) null else "Android 12+ only",
                checked = state.dynamicColor,
                enabled = dynamicSupported,
                onChange = { viewModel.setDynamicColor(it) }
            )
            val darkEffective = state.themeMode == ThemeMode.DARK ||
                (state.themeMode == ThemeMode.SYSTEM &&
                    androidx.compose.foundation.isSystemInDarkTheme())
            SwitchRow(
                title = "Pure black (AMOLED)",
                supporting = if (darkEffective) null else "Dark theme only",
                checked = state.amoledBlack,
                enabled = darkEffective,
                onChange = { viewModel.setAmoledBlack(it) }
            )

            Text("Security", style = MaterialTheme.typography.titleSmall)
            ListItem(
                headlineContent = { Text("Passcode lock") },
                supportingContent = { Text(if (state.passcodeSet) "On" else "Off") },
                trailingContent = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.passcodeSet) {
                            OutlinedButton(onClick = { pinDialog = PinDialogMode.REMOVE }) {
                                Text("Remove")
                            }
                        }
                        OutlinedButton(onClick = onOpenLock) {
                            Text(if (state.passcodeSet) "Change" else "Set up")
                        }
                    }
                }
            )
            ListItem(
                headlineContent = { Text("Auto-lock") },
                supportingContent = {
                    Text(
                        if (state.passcodeSet) state.autoLockTimeout.label()
                        else "Set a passcode first"
                    )
                },
                trailingContent = {
                    OutlinedButton(
                        onClick = { autoLockDialog = true },
                        enabled = state.passcodeSet
                    ) { Text("Change") }
                }
            )
            if (state.passcodeSet) {
                ListItem(
                    headlineContent = { Text("Unlock with biometrics") },
                    supportingContent = {
                        if (!state.biometricAvailable) Text("Not available on this device")
                    },
                    trailingContent = {
                        Switch(
                            checked = state.biometricEnabled,
                            enabled = state.biometricAvailable,
                            onCheckedChange = { viewModel.setBiometric(it) }
                        )
                    }
                )
            }

            Text("Number parsing", style = MaterialTheme.typography.titleSmall)
            ListItem(
                headlineContent = { Text("Default region") },
                supportingContent = { Text(defaultRegionLabel(state.defaultRegion)) },
                trailingContent = {
                    OutlinedButton(onClick = { regionDialog = true }) { Text("Change") }
                }
            )

            Text("Contacts backup", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("text/vcard", "text/x-vcard")) },
                    modifier = Modifier.weight(1f)
                ) { Text("Import VCF") }
                Button(
                    onClick = {
                        if (state.passcodeSet) pinDialog = PinDialogMode.EXPORT
                        else exportLauncher.launch("contacts.vcf")
                    },
                    enabled = state.contactCount > 0,
                    modifier = Modifier.weight(1f)
                ) { Text("Export VCF") }
            }
            Text("${state.contactCount} contacts available")
            if (state.contactCount > 0) {
                OutlinedButton(
                    onClick = { deleteConfirm = true },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete all contacts") }
            }

            Text("About", style = MaterialTheme.typography.titleSmall)
            ListItem(headlineContent = { Text("Privnum") },
                supportingContent = {
                    Column {
                        Text("Version ${appVersion(context)}")
                        Text("Based on Alternate by BioHazard786")
                    }
                })
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = { uriHandler.openUri("https://github.com/Hcmdz/Privnum?tab=readme-ov-file") }) {
                    Text("README")
                }
                TextButton(onClick = { uriHandler.openUri("https://github.com/Hcmdz/Privnum/issues") }) {
                    Text("Report issue")
                }
                TextButton(onClick = { uriHandler.openUri("https://github.com/sponsors/Hcmdz") }) {
                    Text("Support")
                }
            }
            Text("Legal", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = { uriHandler.openUri("https://hcmdz.github.io/Privnum/privacy/") }) {
                    Text("Privacy")
                }
                TextButton(onClick = { uriHandler.openUri("https://hcmdz.github.io/Privnum/terms/") }) {
                    Text("Terms")
                }
            }
            Text("Developed by", style = MaterialTheme.typography.titleSmall)
            ListItem(
                headlineContent = { Text("Hcmdz") },
                leadingContent = {
                    ContactAvatar(model = null, name = "P", size = 48.dp)
                },
                trailingContent = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = { uriHandler.openUri("https://github.com/Hcmdz/Privnum/issues") },
                            label = { Text("Contact") }
                        )
                        AssistChip(
                            onClick = { uriHandler.openUri("https://github.com/Hcmdz") },
                            label = { Text("Github") }
                        )
                    }
                }
            )
        }
    }

    if (autoLockDialog) {
        AlertDialog(
            onDismissRequest = { autoLockDialog = false },
            title = { Text("Auto-lock") },
            text = {
                Column(Modifier.selectableGroup()) {
                    AutoLockTimeout.entries.forEach { timeout ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = state.autoLockTimeout == timeout,
                                    onClick = {
                                        viewModel.setAutoLock(timeout)
                                        autoLockDialog = false
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = state.autoLockTimeout == timeout,
                                onClick = null
                            )
                            Text(timeout.label(), modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { autoLockDialog = false }) { Text("Close") }
            }
        )
    }

    if (regionDialog) {
        AlertDialog(
            onDismissRequest = { regionDialog = false },
            title = { Text("Default region") },
            text = {
                Column(
                    Modifier
                        .selectableGroup()
                        .verticalScroll(rememberScrollState())
                ) {
                    RegionRow(
                        label = "Automatic (SIM)",
                        selected = state.defaultRegion == null,
                        onClick = {
                            viewModel.setDefaultRegion(null)
                            regionDialog = false
                        }
                    )
                    DEFAULT_REGION_OPTIONS.forEach { region ->
                        RegionRow(
                            label = region,
                            selected = state.defaultRegion == region,
                            onClick = {
                                viewModel.setDefaultRegion(region)
                                regionDialog = false
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { regionDialog = false }) { Text("Close") }
            }
        )
    }

    pinDialog?.let { mode ->
        VerifyPinDialog(
            title = if (mode == PinDialogMode.EXPORT) "Enter PIN to export" else "Enter PIN to remove",
            lockedOut = viewModel.isLockedOutNow(),
            onDismiss = { pinDialog = null },
            onConfirm = { pin, done ->
                if (mode == PinDialogMode.EXPORT) {
                    viewModel.checkPin(pin) { result ->
                        if (result == PinCheck.OK) {
                            done(null)
                            pinDialog = null
                            exportLauncher.launch("contacts.vcf")
                        } else {
                            done(errorFor(result))
                        }
                    }
                } else {
                    viewModel.removePasscode(pin) { result ->
                        done(if (result == PinCheck.OK) null else errorFor(result))
                        if (result == PinCheck.OK) pinDialog = null
                    }
                }
            }
        )
    }

    if (overlayRationale) {
        AlertDialog(
            onDismissRequest = { overlayRationale = false },
            title = { Text("Display over other apps") },
            text = { Text("Privnum needs this permission to show the caller popup during incoming calls.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        overlayRationale = false
                        systemSettingsLauncher.launch(CallerPermissions.overlaySettingsIntent())
                    }
                ) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { overlayRationale = false }) { Text("Cancel") }
            }
        )
    }

    if (deleteConfirm) {
        AlertDialog(
            onDismissRequest = { deleteConfirm = false },
            title = { Text("Delete all contacts?") },
            text = {
                Text("${state.contactCount} contacts will be permanently deleted. This cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllContacts()
                        deleteConfirm = false
                    }
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

private fun errorFor(result: PinCheck): String = when (result) {
    PinCheck.INVALID -> "Invalid PIN"
    PinCheck.LOCKED_OUT -> "Too many attempts, try again later"
    PinCheck.OK -> ""
}

@Composable
private fun RegionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    supporting: String? = null,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { supporting?.let { Text(it) } },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = null, enabled = enabled)
        },
        modifier = if (enabled) {
            Modifier.clickable(
                role = Role.Switch,
                onClick = { onChange(!checked) }
            )
        } else {
            Modifier
        }
    )
}

@Composable
private fun StatusRow(title: String, granted: Boolean, actionLabel: String, onAction: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(if (granted) "Granted" else "Not granted") },
        trailingContent = {
            if (!granted) {
                OutlinedButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    )
}

@Composable
private fun VerifyPinDialog(
    title: String,
    lockedOut: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, (String?) -> Unit) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(if (lockedOut) errorFor(PinCheck.LOCKED_OUT) else null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) pin = it },
                    label = { Text("PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    enabled = !lockedOut && !busy,
                    isError = error != null
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = pin.length == 4 && !lockedOut && !busy,
                onClick = {
                    busy = true
                    onConfirm(pin) { err ->
                        busy = false
                        if (err != null) {
                            error = err
                            pin = ""
                        }
                    }
                }
            ) { Text("Confirm") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
