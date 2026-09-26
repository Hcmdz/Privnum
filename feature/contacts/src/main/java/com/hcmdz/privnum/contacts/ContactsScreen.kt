package com.hcmdz.privnum.contacts

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hcmdz.privnum.caller.ScreeningRole
import com.hcmdz.privnum.data.Contact
import com.hcmdz.privnum.data.VcfMapper
import com.hcmdz.privnum.data.shareFile
import com.hcmdz.privnum.ui.ContactAvatar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ContactsScreen(
    onAdd: () -> Unit,
    onPreview: (Contact) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onLockNow: () -> Unit,
    viewModel: ContactsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val locale = LocalLocale.current.platformLocale
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var deleteConfirm by remember { mutableStateOf(false) }
    var passcodeSet by remember { mutableStateOf(false) }
    var screeningHeld by remember { mutableStateOf(true) }

    val contactsTitle = stringResource(R.string.contacts_title)
    val selectedTitle = pluralStringResource(
        R.plurals.contacts_selected_count,
        state.selectedCount,
        state.selectedCount
    )
    val clipboardLabel = stringResource(R.string.contacts_clipboard_label)
    val copiedMessage = stringResource(R.string.contacts_copied_to_clipboard)
    val shareChooserTitle = stringResource(R.string.contacts_share_chooser_title)
    val deleteMessage = pluralStringResource(
        R.plurals.contacts_delete_count,
        state.selectedCount,
        state.selectedCount
    )
    val clipboardLabels = ContactClipboardLabels(
        name = stringResource(R.string.contacts_clipboard_name),
        phone = stringResource(R.string.contacts_clipboard_phone),
        additionalPhone = stringResource(R.string.contacts_clipboard_additional_phone),
        email = stringResource(R.string.contacts_clipboard_email),
        appointment = stringResource(R.string.contacts_clipboard_appointment),
        location = stringResource(R.string.contacts_clipboard_location),
        notes = stringResource(R.string.contacts_clipboard_notes),
        nickname = stringResource(R.string.contacts_clipboard_nickname),
        website = stringResource(R.string.contacts_clipboard_website),
        birthday = stringResource(R.string.contacts_clipboard_birthday),
        labels = stringResource(R.string.contacts_clipboard_labels),
        prefix = stringResource(R.string.contacts_clipboard_prefix),
        suffix = stringResource(R.string.contacts_clipboard_suffix)
    )

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        passcodeSet = viewModel.isPasscodeSet()
        screeningHeld = ScreeningRole.isHeld(context)
    }
    BackHandler(enabled = state.selectionMode) {
        viewModel.clearSelection()
    }

    fun selectedContacts(): List<Contact> =
        state.items.filter { it.selected }.map { it.contact }

    fun copySelected() {
        val clipboard =
            context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                clipboardLabel,
                contactClipboardLines(selectedContacts(), clipboardLabels)
            )
        )
        scope.launch { snackbar.showSnackbar(copiedMessage) }
    }

    fun shareSelected() {
        val vcf = VcfMapper.contactsToVcf(selectedContacts())
        if (vcf.isBlank()) return
        runCatching {
            val file = shareFile(context, "shared_contacts.vcf")
            file.writeText(vcf)
            val uri = FileProvider.getUriForFile(
                context, "com.hcmdz.privnum.fileprovider", file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/vcard"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, shareChooserTitle))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (state.selectionMode) selectedTitle else contactsTitle)
                },
                actions = {
                    if (state.selectionMode) {
                        IconButton(onClick = { copySelected() }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.contacts_copy_selected_description))
                        }
                        IconButton(onClick = { shareSelected() }) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.contacts_share_selected_description))
                        }
                        IconButton(onClick = { deleteConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.contacts_delete_selected_description))
                        }
                    } else {
                        if (passcodeSet) {
                            IconButton(onClick = onLockNow) {
                                Icon(Icons.Default.Lock, contentDescription = stringResource(R.string.contacts_lock_now_description))
                            }
                        }
                        IconButton(onClick = onOpenSearch) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.contacts_search_description))
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.contacts_settings_description))
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (state.selectionMode) viewModel.clearSelection() else onAdd()
            }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.contacts_add_contact_description))
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        if (state.items.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.contacts_empty_title))
                Text(stringResource(R.string.contacts_empty_subtitle))
                if (!screeningHeld) {
                    Text(stringResource(R.string.contacts_call_detection_off))
                    OutlinedButton(onClick = onOpenSettings) {
                        Text(stringResource(R.string.contacts_enable_in_settings))
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                var lastLetter = ""
                state.items.forEach { item ->
                    val letter = item.contact.displayName().firstOrNull()?.uppercase(locale) ?: "#"
                    if (letter != lastLetter) {
                        lastLetter = letter
                        stickyHeader {
                            Text(
                                text = letter,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                    item(key = item.contact.id) {
                        ContactRow(
                            item = item,
                            photoModel = viewModel.photoModel(item.contact.photo),
                            onClick = {
                                if (state.selectionMode) {
                                    viewModel.toggleSelection(item.contact.id)
                                } else {
                                    onPreview(item.contact)
                                }
                            },
                            onLongClick = {
                                viewModel.toggleSelection(item.contact.id)
                            }
                        )
                    }
                }
            }
        }
    }

    if (deleteConfirm) {
        AlertDialog(
            onDismissRequest = { deleteConfirm = false },
            title = { Text(stringResource(R.string.contacts_delete_dialog_title)) },
            text = { Text(deleteMessage) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSelected()
                        deleteConfirm = false
                    }
                ) { Text(stringResource(R.string.contacts_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirm = false }) {
                    Text(stringResource(R.string.contacts_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactRow(
    item: ContactListItem,
    photoModel: Any?,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        ),
        headlineContent = { Text(item.contact.displayName()) },
        supportingContent = {
            Text(
                item.contact.primaryNumber()?.let { "+${it.full}" }.orEmpty()
            )
        },
        leadingContent = {
            ContactAvatar(
                model = photoModel,
                name = item.contact.displayName(),
                size = 40.dp
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.selected) {
                    Checkbox(checked = true, onCheckedChange = { onClick() })
                }
            }
        }
    )
}
