package com.hcmdz.privnum.preview

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hcmdz.privnum.data.Contact
import com.hcmdz.privnum.data.PhoneNumberRef
import com.hcmdz.privnum.data.PhoneNumberUtils
import com.hcmdz.privnum.ui.ContactAvatar
import com.hcmdz.privnum.ui.resolveText
import kotlinx.coroutines.launch
import java.util.Locale

fun isAppInstalled(context: Context, packageName: String): Boolean =
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(packageName, 0)
        }
        true
    }.getOrDefault(false)

/** Launches a VIEW intent, explicit when the target package is known. */
fun openAppIntent(context: Context, uri: String, packageName: String?) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                packageName?.let(::setPackage)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PreviewScreen(
    contactId: Long,
    onBack: () -> Unit,
    onEdit: (Contact) -> Unit,
    viewModel: PreviewViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val locale = LocalLocale.current.platformLocale
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var deleteConfirm by remember { mutableStateOf(false) }
    val message = state.message?.resolveText()
    val clipboardLabel = stringResource(R.string.preview_clipboard_label)
    val copiedMessage = stringResource(R.string.preview_copied_to_clipboard)
    val shareContactChooserTitle = stringResource(R.string.preview_share_contact_chooser)
    val shareFailedMessage = stringResource(R.string.preview_share_failed)

    LaunchedEffect(contactId) { viewModel.load(contactId) }
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }

    fun copy(text: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(clipboardLabel, text))
        scope.launch { snackbar.showSnackbar(copiedMessage) }
    }

    fun openUri(uri: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.preview_back)
                        )
                    }
                },
                actions = {
                    state.contact?.let { contact ->
                        IconButton(onClick = { onEdit(contact) }) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.preview_edit_contact)
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        val contact = state.contact
        when {
            state.notFound -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) { Text(stringResource(R.string.preview_contact_not_found)) }
            }

            contact == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }

            else -> {
                ContactDetails(
                    contact = contact,
                    locale = locale,
                    photoModel = viewModel.photoModel(contact.photo),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    onCall = {
                        contact.primaryNumber()?.let { openUri("tel:+${it.full}") }
                    },
                    onMessage = {
                        contact.primaryNumber()?.let { openUri("smsto:+${it.full}") }
                    },
                    onEmail = {
                        if (contact.email.isNotBlank()) openUri("mailto:${contact.email}")
                    },
                    onCopy = ::copy,
                    onOpenUri = ::openUri,
                    onShare = {
                        scope.launch {
                            viewModel.makeShareUri()?.let { uri ->
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/vcard"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(
                                    Intent.createChooser(intent, shareContactChooserTitle)
                                )
                            } ?: scope.launch {
                                snackbar.showSnackbar(shareFailedMessage)
                            }
                        }
                    },
                    onDelete = { deleteConfirm = true }
                )
            }
        }
    }

    if (deleteConfirm) {
        AlertDialog(
            onDismissRequest = { deleteConfirm = false },
            title = { Text(stringResource(R.string.preview_delete_contact_title)) },
            text = { Text(stringResource(R.string.preview_delete_contact_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete()
                        deleteConfirm = false
                    }
                ) {
                    Text(
                        stringResource(R.string.preview_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirm = false }) {
                    Text(stringResource(R.string.preview_cancel))
                }
            }
        )
    }
}

@Composable
private fun ContactDetails(
    contact: Contact,
    locale: Locale,
    photoModel: Any?,
    modifier: Modifier = Modifier,
    onCall: () -> Unit,
    onMessage: () -> Unit,
    onEmail: () -> Unit,
    onCopy: (String) -> Unit,
    onOpenUri: (String) -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val letter = contact.displayName().firstOrNull()
        ?.let { it.toString().uppercase(locale).firstOrNull() } ?: '#'

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val (container, onContainer) = when (avatarRoleIndex(letter)) {
            1 -> MaterialTheme.colorScheme.secondaryContainer to
                MaterialTheme.colorScheme.onSecondaryContainer

            2 -> MaterialTheme.colorScheme.tertiaryContainer to
                MaterialTheme.colorScheme.onTertiaryContainer

            else -> MaterialTheme.colorScheme.primaryContainer to
                MaterialTheme.colorScheme.onPrimaryContainer
        }
        ContactAvatar(
            model = photoModel,
            name = contact.displayName(),
            size = 72.dp,
            containerColor = container,
            contentColor = onContainer,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Text(
            contact.displayName(),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {},
                    onLongClick = { onCopy(contact.name) }
                )
        )
        if (contact.nickname.isNotBlank()) {
            Text(
                contact.nickname,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { onCopy(contact.nickname) }
                    )
            )
        }
        if (contact.appointment.isNotBlank()) {
            Text(
                contact.appointment,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { onCopy(contact.appointment) }
                    )
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ActionButton(stringResource(R.string.preview_call), Icons.Filled.Phone, onCall)
            ActionButton(stringResource(R.string.preview_message), Icons.Filled.Message, onMessage)
            if (contact.email.isNotBlank()) {
                ActionButton(stringResource(R.string.preview_email), Icons.Filled.Email, onEmail)
            }
        }

        Card {
            Column {
                Text(
                    stringResource(R.string.preview_contact_info),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )
                contact.numbers.forEachIndexed { index, number ->
                    val formatted = "+" + PhoneNumberUtils.formatNational(
                        number.full,
                        number.country
                    )
                    ListItem(
                        headlineContent = { Text(formatted) },
                        supportingContent = {
                            Text(
                                pluralStringResource(
                                    R.plurals.preview_mobile_number,
                                    index + 1,
                                    index + 1
                                )
                            )
                        },
                        leadingContent = {
                            Icon(Icons.Filled.Phone, contentDescription = null)
                        },
                        modifier = Modifier.combinedClickable(
                            onClick = { onOpenUri("tel:+${number.full}") },
                            onLongClick = { onCopy("+" + number.full) }
                        )
                    )
                }
                if (contact.email.isNotBlank()) {
                    ListItem(
                        headlineContent = { Text(contact.email) },
                        leadingContent = {
                            Icon(Icons.Filled.Email, contentDescription = null)
                        },
                        modifier = Modifier.combinedClickable(
                            onClick = onEmail,
                            onLongClick = { onCopy(contact.email) }
                        )
                    )
                }
                if (contact.location.isNotBlank()) {
                    ListItem(
                        headlineContent = { Text(contact.location) },
                        leadingContent = {
                            Icon(Icons.Filled.Place, contentDescription = null)
                        },
                        modifier = Modifier.combinedClickable(
                            onClick = {},
                            onLongClick = { onCopy(contact.location) }
                        )
                    )
                }
            }
        }

        Card {
            Column {
                Text(
                    stringResource(R.string.preview_connected_apps),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )
                ExpandableAppRow(
                    name = "WhatsApp",
                    icon = Icons.Filled.Message,
                    numbers = contact.numbers,
                    onAction = { number, profile ->
                        val installed = isAppInstalled(context, "com.whatsapp")
                        openAppIntent(
                            context,
                            whatsappUri(number, installed),
                            whatsappPackage(installed)
                        )
                    }
                )
                ExpandableAppRow(
                    name = "Telegram",
                    icon = Icons.Filled.Send,
                    numbers = contact.numbers,
                    onAction = { number, profile ->
                        val installed = isAppInstalled(context, "org.telegram.messenger")
                        openAppIntent(
                            context,
                            telegramUri(number, profile, installed),
                            telegramPackage(installed)
                        )
                    }
                )
            }
        }

        if (contact.website.isNotBlank() ||
            contact.birthday.isNotBlank() ||
            contact.notes.isNotBlank()
        ) {
            Card {
                Column {
                    Text(
                        stringResource(
                            R.string.preview_about_contact,
                            contact.name.split(" ").firstOrNull().orEmpty()
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                    if (contact.website.isNotBlank()) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    contact.website,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            },
                            leadingContent = {
                                Icon(Icons.Filled.Link, contentDescription = null)
                            },
                            modifier = Modifier.combinedClickable(
                                onClick = { onOpenUri(contact.website) },
                                onLongClick = { onCopy(contact.website) }
                            )
                        )
                    }
                    if (contact.birthday.isNotBlank()) {
                        ListItem(
                            headlineContent = {
                                Text(formatContactDate(contact.birthday, locale))
                            },
                            supportingContent = {
                                Text(stringResource(R.string.preview_birthday))
                            },
                            leadingContent = {
                                Icon(Icons.Filled.Cake, contentDescription = null)
                            },
                            modifier = Modifier.combinedClickable(
                                onClick = {},
                                onLongClick = {
                                    onCopy(formatContactDate(contact.birthday, locale))
                                }
                            )
                        )
                    }
                    if (contact.notes.isNotBlank()) {
                        ListItem(
                            headlineContent = { Text(contact.notes) },
                            leadingContent = {
                                Icon(Icons.Filled.Notes, contentDescription = null)
                            },
                            modifier = Modifier.combinedClickable(
                                onClick = {},
                                onLongClick = { onCopy(contact.notes) }
                            )
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(
                onClick = onShare,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.preview_share_contact_action)) }
            Button(
                onClick = onDelete,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.preview_delete_contact_action)) }
        }
    }
}

@Composable
private fun ActionButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(64.dp)) {
            Icon(icon, contentDescription = label)
        }
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ExpandableAppRow(
    name: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    numbers: List<PhoneNumberRef>,
    onAction: (number: String, profile: Boolean) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column {
        ListItem(
            headlineContent = { Text(name, style = MaterialTheme.typography.titleLarge) },
            leadingContent = {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(25.dp)
                )
            },
            modifier = Modifier.clickable { expanded = !expanded }
        )
        if (expanded) {
            numbers.forEach { number ->
                val formatted = "+${number.full}"
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.preview_message_number, formatted))
                    },
                    modifier = Modifier.clickable { onAction(number.full, false) }
                )
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.preview_voice_call_number, formatted))
                    },
                    modifier = Modifier.clickable { onAction(number.full, true) }
                )
            }
        }
    }
}
