package com.hcmdz.privnum.calllog

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.PhoneCallback
import androidx.compose.material.icons.automirrored.filled.PhoneMissed
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hcmdz.privnum.caller.CallerPermissions
import com.hcmdz.privnum.caller.HistoryDirection
import com.hcmdz.privnum.caller.HistoryEntry

private fun appSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onPreview: (Long) -> Unit,
    onAddNumber: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val locale = LocalLocale.current.platformLocale
    var deniedOnce by remember { mutableStateOf(false) }
    val activity = context as? Activity
    val grantLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.setPermission(true) else deniedOnce = true
    }
    // After a final denial the system silently drops further requests:
    // route to app settings instead of a dead button.
    val needsSettings = deniedOnce && activity != null &&
        !ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.READ_CALL_LOG
        )

    // The permission is never requested on entry: the rationale state carries
    // an explicit grant button instead. The resume effect re-checks after a
    // detour to system settings, which does not recreate this entry. It also
    // covers the first composition, so it is the only trigger: adding a
    // LaunchedEffect here too loaded the provider twice per visit.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.setPermission(CallerPermissions.hasCallLog(context))
    }

    Scaffold(
        modifier = Modifier.testTag("history_screen"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.history_back_description)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            when {
                state.isLoading -> CircularProgressIndicator()
                !state.hasPermission -> Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        stringResource(R.string.history_grant_rationale),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (needsSettings) {
                        Button(
                            modifier = Modifier.testTag("history_settings"),
                            onClick = { context.startActivity(appSettingsIntent(context)) }
                        ) {
                            Text(stringResource(R.string.history_open_settings))
                        }
                    } else {
                        Button(
                            modifier = Modifier.testTag("history_grant"),
                            onClick = { grantLauncher.launch(Manifest.permission.READ_CALL_LOG) }
                        ) {
                            Text(stringResource(R.string.history_grant))
                        }
                    }
                }
                state.unavailable -> Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        stringResource(R.string.history_unavailable),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        modifier = Modifier.testTag("history_retry"),
                        onClick = { viewModel.refresh() }
                    ) {
                        Text(stringResource(R.string.history_retry))
                    }
                }
                state.entries.isEmpty() -> Text(
                    stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.bodyMedium
                )
                else -> LazyColumn(modifier = Modifier.testTag("history_list")) {
                    itemsIndexed(state.entries) { index, entry ->
                        HistoryRow(
                            entry = entry,
                            index = index,
                            dateText = formatHistoryDate(entry.dateMillis, locale),
                            onPreview = onPreview,
                            onAddNumber = onAddNumber
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    index: Int,
    dateText: String,
    onPreview: (Long) -> Unit,
    onAddNumber: (String) -> Unit
) {
    val contactId = entry.contactId
    val (icon, description) = when (entry.direction) {
        HistoryDirection.INCOMING -> Icons.AutoMirrored.Filled.CallReceived to R.string.history_direction_incoming
        HistoryDirection.OUTGOING -> Icons.AutoMirrored.Filled.CallMade to R.string.history_direction_outgoing
        HistoryDirection.MISSED -> Icons.AutoMirrored.Filled.PhoneMissed to R.string.history_direction_missed
        HistoryDirection.OTHER -> Icons.AutoMirrored.Filled.PhoneCallback to R.string.history_direction_other
    }
    val subtitle = "$dateText · ${formatHistoryDuration(entry.durationSeconds)}"
    ListItem(
        modifier = Modifier
            .testTag("history_row_$index")
            .fillMaxWidth()
            .then(
                if (contactId != null) {
                    Modifier.clickable { onPreview(contactId) }
                } else {
                    Modifier
                }
            ),
        headlineContent = { Text(entry.contactName ?: entry.rawNumber) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(icon, contentDescription = stringResource(description))
        },
        trailingContent = {
            if (contactId == null) {
                IconButton(
                    modifier = Modifier.testTag("history_add_$index"),
                    onClick = { onAddNumber(entry.rawNumber) }
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.history_add_description)
                    )
                }
            }
        }
    )
}
