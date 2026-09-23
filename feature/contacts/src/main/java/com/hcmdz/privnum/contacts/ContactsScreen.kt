package com.hcmdz.privnum.contacts

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hcmdz.privnum.data.Contact
import com.hcmdz.privnum.data.VcfMapper
import com.hcmdz.privnum.ui.ContactAvatar
import kotlinx.coroutines.launch
import java.io.File

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
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var deleteConfirm by remember { mutableStateOf(false) }
    var passcodeSet by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        passcodeSet = viewModel.isPasscodeSet()
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
            ClipData.newPlainText("contacts", contactClipboardLines(selectedContacts()))
        )
        scope.launch { snackbar.showSnackbar("Copied to clipboard") }
    }

    fun shareSelected() {
        val vcf = VcfMapper.contactsToVcf(selectedContacts())
        if (vcf.isBlank()) return
        runCatching {
            val file = File(context.cacheDir, "shared_contacts.vcf")
            file.writeText(vcf)
            val uri = FileProvider.getUriForFile(
                context, "com.hcmdz.privnum.fileprovider", file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/vcard"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share contacts"))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (state.selectionMode) "${state.selectedCount} selected" else "Contacts")
                },
                actions = {
                    if (state.selectionMode) {
                        IconButton(onClick = { copySelected() }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy selected")
                        }
                        IconButton(onClick = { shareSelected() }) {
                            Icon(Icons.Default.Share, contentDescription = "Share selected")
                        }
                        IconButton(onClick = { deleteConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete selected")
                        }
                    } else {
                        if (passcodeSet) {
                            IconButton(onClick = onLockNow) {
                                Icon(Icons.Default.Lock, contentDescription = "Lock now")
                            }
                        }
                        IconButton(onClick = onOpenSearch) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (state.selectionMode) viewModel.clearSelection() else onAdd()
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add contact")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        if (state.items.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp)
            ) {
                Text("No contacts found")
                Text("Add your first contact")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                var lastLetter = ""
                state.items.forEach { item ->
                    val letter = item.contact.name.firstOrNull()?.uppercase() ?: "#"
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
                    item(key = item.contact.fullPhoneNumber) {
                        ContactRow(
                            item = item,
                            photoModel = viewModel.photoModel(item.contact.photo),
                            onClick = {
                                if (state.selectionMode) {
                                    viewModel.toggleSelection(item.contact.fullPhoneNumber)
                                } else {
                                    onPreview(item.contact)
                                }
                            },
                            onLongClick = {
                                viewModel.toggleSelection(item.contact.fullPhoneNumber)
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
            title = { Text("Delete contacts?") },
            text = {
                Text("${state.selectedCount} contacts will be permanently deleted.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSelected()
                        deleteConfirm = false
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirm = false }) { Text("Cancel") }
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
        supportingContent = { Text("+${item.contact.fullPhoneNumber}") },
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
