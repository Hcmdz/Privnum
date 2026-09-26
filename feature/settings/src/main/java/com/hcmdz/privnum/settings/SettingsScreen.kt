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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.app.LocaleManagerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hcmdz.privnum.caller.CallerPermissions
import com.hcmdz.privnum.caller.ScreeningRole
import com.hcmdz.privnum.data.AutoLockTimeout
import com.hcmdz.privnum.data.Countries
import com.hcmdz.privnum.data.ThemeMode
import com.hcmdz.privnum.data.displayName
import com.hcmdz.privnum.ui.ContactAvatar
import com.hcmdz.privnum.ui.UiText
import com.hcmdz.privnum.ui.resolveText

private enum class PinDialogMode { EXPORT, REMOVE }

private data class LanguageOption(val tag: String, val label: String?)

private val LANGUAGE_OPTIONS = listOf(
    LanguageOption("", null),
    LanguageOption("en", "English"),
    LanguageOption("fr", "Français"),
    LanguageOption("es", "Español"),
    LanguageOption("de", "Deutsch"),
    LanguageOption("pt-BR", "Português (Brasil)"),
    LanguageOption("ar", "العربية"),
    LanguageOption("hi", "हिन्दी"),
    LanguageOption("id", "Bahasa Indonesia"),
    LanguageOption("ja", "日本語"),
    LanguageOption("ko", "한국어"),
    LanguageOption("zh-Hans", "简体中文")
)

private fun currentLanguageTag(context: Context): String {
    val locales = LocaleManagerCompat.getApplicationLocales(context)
    return if (locales.isEmpty) "" else locales.get(0)?.toLanguageTag().orEmpty()
}

@Composable
private fun languageLabel(tag: String): String =
    if (tag.isEmpty()) {
        stringResource(R.string.language_system)
    } else {
        LANGUAGE_OPTIONS.firstOrNull { it.tag == tag }?.label ?: tag
    }

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

@Composable
private fun defaultRegionLabel(region: String?): String {
    if (region == null) return stringResource(R.string.settings_default_region_automatic)
    val locale = LocalLocale.current.platformLocale
    return Countries.getByCode(region)?.displayName(locale) ?: region
}

private fun simRegion(context: Context): String = Countries.simRegion(context)

private fun appVersion(context: Context): UiText {
    val info = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }.getOrNull() ?: return UiText.Resource(R.string.settings_version_unknown)
    @Suppress("DEPRECATION")
    val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        info.longVersionCode
    } else {
        info.versionCode.toLong()
    }
    return UiText.Resource(
        R.string.settings_version,
        listOf(info.versionName.orEmpty(), code)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenLock: () -> Unit,
    onLanguageSelected: (String?) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val snackbar = remember { SnackbarHostState() }
    val message = state.message?.resolveText()
    val contactsAvailable = pluralStringResource(
        R.plurals.settings_contacts_available,
        state.contactCount,
        state.contactCount
    )
    val deleteContactsMessage = pluralStringResource(
        R.plurals.settings_delete_contacts_message,
        state.contactCount,
        state.contactCount
    )

    var callStatus by remember { mutableStateOf(readCallStatus(context)) }
    var autoLockDialog by remember { mutableStateOf(false) }
    var regionDialog by remember { mutableStateOf(false) }
    var pinDialog by remember { mutableStateOf<PinDialogMode?>(null) }
    var deleteConfirm by remember { mutableStateOf(false) }
    var overlayRationale by remember { mutableStateOf(false) }
    var languageDialog by remember { mutableStateOf(false) }
    var noticesDialog by remember { mutableStateOf(false) }
    var selectedLanguageTag by remember { mutableStateOf("") }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshSecurity()
        callStatus = readCallStatus(context)
        selectedLanguageTag = currentLanguageTag(context)
    }

    LaunchedEffect(message) {
        message?.let {
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
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back)
                        )
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
            Text(stringResource(R.string.settings_general), style = MaterialTheme.typography.titleSmall)
            SwitchRow(
                stringResource(R.string.settings_show_incoming_popup),
                state.incomingPopup
            ) { viewModel.setIncoming(it) }
            SwitchRow(
                stringResource(R.string.settings_show_outgoing_popup),
                state.outgoingPopup
            ) { viewModel.setOutgoing(it) }
            ListItem(
                headlineContent = { Text(stringResource(R.string.language)) },
                supportingContent = { Text(languageLabel(selectedLanguageTag)) },
                modifier = Modifier.clickable { languageDialog = true }
            )

            Text(stringResource(R.string.settings_call_detection), style = MaterialTheme.typography.titleSmall)
            StatusRow(
                title = stringResource(R.string.settings_call_screening_role),
                granted = callStatus.screeningRole,
                actionLabel = stringResource(R.string.settings_request),
                onAction = {
                    ScreeningRole.requestIntent(context)?.let(systemSettingsLauncher::launch)
                }
            )
            StatusRow(
                title = stringResource(R.string.settings_display_over_other_apps),
                granted = callStatus.overlay,
                actionLabel = stringResource(R.string.settings_grant),
                onAction = { overlayRationale = true }
            )
            StatusRow(
                title = stringResource(R.string.settings_phone_state_permission),
                granted = callStatus.phoneState,
                actionLabel = stringResource(R.string.settings_grant),
                onAction = {
                    phoneStateLauncher.launch(android.Manifest.permission.READ_PHONE_STATE)
                }
            )

            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = state.themeMode == mode,
                        onClick = { viewModel.setTheme(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                        label = {
                            Text(
                                when (mode) {
                                    ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                                    ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                                    ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
                                }
                            )
                        }
                    )
                }
            }
            val dynamicSupported =
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
            SwitchRow(
                title = stringResource(R.string.settings_dynamic_colors),
                supporting = if (dynamicSupported) null else stringResource(R.string.settings_android_12_plus_only),
                checked = state.dynamicColor,
                enabled = dynamicSupported,
                onChange = { viewModel.setDynamicColor(it) }
            )
            val darkEffective = state.themeMode == ThemeMode.DARK ||
                (state.themeMode == ThemeMode.SYSTEM &&
                    androidx.compose.foundation.isSystemInDarkTheme())
            SwitchRow(
                title = stringResource(R.string.settings_pure_black_amoled),
                supporting = if (darkEffective) null else stringResource(R.string.settings_dark_theme_only),
                checked = state.amoledBlack,
                enabled = darkEffective,
                onChange = { viewModel.setAmoledBlack(it) }
            )

            Text(stringResource(R.string.settings_security), style = MaterialTheme.typography.titleSmall)
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_passcode_lock)) },
                supportingContent = {
                    Text(
                        stringResource(
                            if (state.passcodeSet) R.string.settings_on else R.string.settings_off
                        )
                    )
                },
                trailingContent = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.passcodeSet) {
                            OutlinedButton(onClick = { pinDialog = PinDialogMode.REMOVE }) {
                                Text(stringResource(R.string.settings_remove))
                            }
                        }
                        OutlinedButton(onClick = onOpenLock) {
                            Text(
                                stringResource(
                                    if (state.passcodeSet) R.string.settings_change else R.string.settings_set_up
                                )
                            )
                        }
                    }
                }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_auto_lock)) },
                supportingContent = {
                    Text(
                        if (state.passcodeSet) {
                            state.autoLockTimeout.label().resolveText()
                        } else {
                            stringResource(R.string.settings_set_passcode_first)
                        }
                    )
                },
                trailingContent = {
                    OutlinedButton(
                        onClick = { autoLockDialog = true },
                        enabled = state.passcodeSet
                    ) { Text(stringResource(R.string.settings_change)) }
                }
            )
            if (state.passcodeSet) {
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.settings_unlock_with_biometrics))
                    },
                    supportingContent = {
                        if (!state.biometricAvailable) {
                            Text(stringResource(R.string.settings_not_available_on_device))
                        }
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

            Text(stringResource(R.string.settings_number_parsing), style = MaterialTheme.typography.titleSmall)
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_default_region)) },
                supportingContent = { Text(defaultRegionLabel(state.defaultRegion)) },
                trailingContent = {
                    OutlinedButton(onClick = { regionDialog = true }) {
                        Text(stringResource(R.string.settings_change))
                    }
                }
            )

            Text(stringResource(R.string.settings_contacts_backup), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("text/vcard", "text/x-vcard")) },
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.settings_import_vcf)) }
                Button(
                    onClick = {
                        if (state.passcodeSet) pinDialog = PinDialogMode.EXPORT
                        else exportLauncher.launch("contacts.vcf")
                    },
                    enabled = state.contactCount > 0,
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.settings_export_vcf)) }
            }
            Text(contactsAvailable)
            if (state.contactCount > 0) {
                OutlinedButton(
                    onClick = { deleteConfirm = true },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(stringResource(R.string.settings_delete_all_contacts)) }
            }

            Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleSmall)
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_app_name)) },
                supportingContent = {
                    Column {
                        Text(appVersion(context).resolveText())
                        Text(stringResource(R.string.settings_based_on))
                    }
                }
            )
            // The database engine's licence requires its notice to be reachable
            // by users, not only in the repository. The labels below are
            // translated; the notice body stays English because it is legal
            // text, which is why it lives in res/raw rather than a string.
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_open_source_notices)) },
                supportingContent = { Text(stringResource(R.string.settings_open_source_notices_summary)) },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null
                    )
                },
                modifier = Modifier.clickable { noticesDialog = true }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = { uriHandler.openUri("https://github.com/Hcmdz/Privnum?tab=readme-ov-file") }) {
                    Text(stringResource(R.string.settings_readme))
                }
                TextButton(onClick = { uriHandler.openUri("https://github.com/Hcmdz/Privnum/issues") }) {
                    Text(stringResource(R.string.settings_report_issue))
                }
                TextButton(onClick = { uriHandler.openUri("https://github.com/sponsors/Hcmdz") }) {
                    Text(stringResource(R.string.settings_support))
                }
            }
            Text(stringResource(R.string.settings_legal), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = { uriHandler.openUri("https://hcmdz.github.io/Privnum/privacy/") }) {
                    Text(stringResource(R.string.settings_privacy))
                }
                TextButton(onClick = { uriHandler.openUri("https://hcmdz.github.io/Privnum/terms/") }) {
                    Text(stringResource(R.string.settings_terms))
                }
            }
            Text(stringResource(R.string.settings_developed_by), style = MaterialTheme.typography.titleSmall)
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_author)) },
                leadingContent = {
                    ContactAvatar(model = null, name = "P", size = 48.dp)
                },
                trailingContent = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = { uriHandler.openUri("https://github.com/Hcmdz/Privnum/issues") },
                            label = { Text(stringResource(R.string.settings_contact)) }
                        )
                        AssistChip(
                            onClick = { uriHandler.openUri("https://github.com/Hcmdz") },
                            label = { Text(stringResource(R.string.settings_github)) }
                        )
                    }
                }
            )
        }
    }

    if (noticesDialog) {
        val notices = remember(noticesDialog) {
            context.resources.openRawResource(R.raw.open_source_notices)
                .bufferedReader()
                .use { it.readText() }
        }
        AlertDialog(
            onDismissRequest = { noticesDialog = false },
            title = { Text(stringResource(R.string.settings_open_source_notices)) },
            text = {
                // The notice is a few screens long; without this the tail of the
                // licence, which is the part users need, would be unreachable.
                Text(
                    notices,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = { noticesDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    if (languageDialog) {
        AlertDialog(
            onDismissRequest = { languageDialog = false },
            title = { Text(stringResource(R.string.language)) },
            text = {
                Column(
                    Modifier
                        .selectableGroup()
                        .verticalScroll(rememberScrollState())
                ) {
                    LANGUAGE_OPTIONS.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selectedLanguageTag == option.tag,
                                    onClick = {
                                        onLanguageSelected(option.tag.ifEmpty { null })
                                        languageDialog = false
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedLanguageTag == option.tag,
                                onClick = null
                            )
                            Text(
                                languageLabel(option.tag),
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { languageDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    if (autoLockDialog) {
        AlertDialog(
            onDismissRequest = { autoLockDialog = false },
            title = { Text(stringResource(R.string.settings_auto_lock)) },
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
                            Text(
                                timeout.label().resolveText(),
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { autoLockDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    if (regionDialog) {
        AlertDialog(
            onDismissRequest = { regionDialog = false },
            title = { Text(stringResource(R.string.settings_default_region)) },
            text = {
                Column(
                    Modifier
                        .selectableGroup()
                        .verticalScroll(rememberScrollState())
                ) {
                    RegionRow(
                        label = stringResource(R.string.settings_default_region_automatic),
                        selected = state.defaultRegion == null,
                        onClick = {
                            viewModel.setDefaultRegion(null)
                            regionDialog = false
                        }
                    )
                    DEFAULT_REGION_OPTIONS.forEach { region ->
                        RegionRow(
                            label = defaultRegionLabel(region),
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
                TextButton(onClick = { regionDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    pinDialog?.let { mode ->
        VerifyPinDialog(
            title = stringResource(
                if (mode == PinDialogMode.EXPORT) {
                    R.string.settings_pin_export_title
                } else {
                    R.string.settings_pin_remove_title
                }
            ),
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
            title = { Text(stringResource(R.string.settings_display_over_other_apps)) },
            text = { Text(stringResource(R.string.settings_overlay_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        overlayRationale = false
                        systemSettingsLauncher.launch(CallerPermissions.overlaySettingsIntent())
                    }
                ) { Text(stringResource(R.string.settings_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { overlayRationale = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            }
        )
    }

    if (deleteConfirm) {
        AlertDialog(
            onDismissRequest = { deleteConfirm = false },
            title = { Text(stringResource(R.string.settings_delete_contacts_title)) },
            text = { Text(deleteContactsMessage) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllContacts()
                        deleteConfirm = false
                    }
                ) {
                    Text(
                        stringResource(R.string.settings_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirm = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            }
        )
    }
}

private fun errorFor(result: PinCheck): UiText? = when (result) {
    PinCheck.INVALID -> UiText.Resource(R.string.settings_error_invalid_pin)
    PinCheck.LOCKED_OUT -> UiText.Resource(R.string.settings_error_too_many_attempts)
    PinCheck.OK -> null
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
        supportingContent = {
            Text(
                stringResource(
                    if (granted) R.string.settings_granted else R.string.settings_not_granted
                )
            )
        },
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
    onConfirm: (String, (UiText?) -> Unit) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember {
        mutableStateOf<UiText?>(if (lockedOut) errorFor(PinCheck.LOCKED_OUT) else null)
    }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) pin = it },
                    label = { Text(stringResource(R.string.settings_pin_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    enabled = !lockedOut && !busy,
                    isError = error != null
                )
                error?.let {
                    Text(it.resolveText(), color = MaterialTheme.colorScheme.error)
                }
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
            ) { Text(stringResource(R.string.settings_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        }
    )
}
