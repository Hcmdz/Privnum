package com.hcmdz.privnum.editor

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hcmdz.privnum.data.Country
import com.hcmdz.privnum.data.PhoneNumberUtils
import com.hcmdz.privnum.data.filterCountries
import com.hcmdz.privnum.data.suggestCountryFor
import com.hcmdz.privnum.ui.ContactAvatar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EditorScreen(
    contactId: Long?,
    entryNonce: Int,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val recents by viewModel.recentCountries.collectAsStateWithLifecycle()
    var showCountries by remember { mutableStateOf(false) }
    var countryRowTarget by remember { mutableStateOf(0) }
    var countryQuery by rememberSaveable { mutableStateOf("") }
    var moreExpanded by rememberSaveable { mutableStateOf(false) }
    var abandonConfirm by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val focus = LocalFocusManager.current
    val dirty = remember(state) { viewModel.isDirty() }

    BackHandler(enabled = dirty && !state.saved) { abandonConfirm = true }

    LaunchedEffect(entryNonce, contactId) {
        viewModel.enter(contactId)
    }
    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val firstRow = state.numbers.getOrNull(0)
    val preview = remember(firstRow?.nationalNumber, firstRow?.country) {
        firstRow?.country?.let { PhoneNumberUtils.previewNumber(firstRow.nationalNumber, it.code) }
    }
    val suggestion = remember(firstRow?.nationalNumber, firstRow?.country) {
        val current = firstRow?.country
        suggestCountryFor(firstRow?.nationalNumber.orEmpty(), current?.code ?: "")
            ?.takeIf { it.code != current?.code }
    }
    val next = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) })
    val photoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) viewModel.setPhotoUri(uri) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        if (ok) cameraUri?.let { viewModel.setPhotoUri(it) }
    }
    var photoChoice by remember { mutableStateOf(false) }
    var birthdayDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (contactId == null) "New contact" else "Edit contact") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (dirty && !state.saved) abandonConfirm = true else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.notFound) {
                item {
                    Text(
                        "Contact not found",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            item {
                val photoModel = state.pendingPhotoUri
                    ?: state.photo.takeIf { it.isNotBlank() }?.let { viewModel.photoModel(it) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ContactAvatar(
                        model = photoModel,
                        name = state.name,
                        size = 72.dp
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { photoChoice = true }) {
                            Text(if (photoModel == null) "Add photo" else "Change photo")
                        }
                        if (photoModel != null) {
                            TextButton(onClick = { viewModel.removePhoto() }) {
                                Text("Remove")
                            }
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = { value ->
                        viewModel.update { s -> s.copy(name = value, nameError = null) }
                    },
                    label = { Text("Name *") },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = next,
                    isError = state.nameError != null,
                    supportingText = { state.nameError?.let { Text(it) } },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("editor_name")
                )
            }
            state.numbers.forEachIndexed { index, row ->
                item(key = "number_$index") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val country = row.country
                        // Transparent overlay first in tap dispatch: the text field
                        // consumes taps even when read-only and unfocusable.
                        Box(modifier = Modifier.weight(0.55f)) {
                            OutlinedTextField(
                                value = country?.let { "${it.flag} +${it.dialCode}" }
                                    ?: "",
                                onValueChange = {},
                                label = { Text("Country *") },
                                trailingIcon = {
                                    Icon(
                                        Icons.Filled.ArrowDropDown,
                                        contentDescription = null
                                    )
                                },
                                readOnly = true,
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusProperties { canFocus = false }
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .testTag(if (index == 0) "editor_country" else "editor_country_$index")
                                    .semantics {
                                        contentDescription =
                                            "Selected country: ${country?.name ?: "none"}"
                                    }
                                    .clickable(
                                        role = Role.DropdownList,
                                        onClick = {
                                            countryRowTarget = index
                                            showCountries = true
                                        }
                                    )
                            )
                        }
                        OutlinedTextField(
                            value = row.nationalNumber,
                            onValueChange = {
                                viewModel.updateRow(index) { r ->
                                    r.copy(nationalNumber = PhoneNumberUtils.trimPhoneInput(it))
                                }
                            },
                            label = { Text("Phone number *") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Phone,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = next,
                            isError = state.numberErrorRow == index,
                            supportingText = {
                                if (state.numberErrorRow == index) {
                                    state.numberError?.let { Text(it) }
                                } else if (index == 0) {
                                    preview?.let { Text("Will be saved as $it") }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag(if (index == 0) "editor_number" else "editor_number_$index")
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        RadioButton(
                            selected = row.primary,
                            onClick = { viewModel.setPrimaryRow(index) }
                        )
                        Text(
                            "Primary",
                            modifier = Modifier.clickable { viewModel.setPrimaryRow(index) }
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (state.numbers.size > 1) {
                            TextButton(onClick = { viewModel.removeNumberRow(index) }) {
                                Text("Remove")
                            }
                        }
                    }
                }
            }
            item {
                TextButton(onClick = { viewModel.addNumberRow() }) {
                    Text("Add another number")
                }
            }
            if (suggestion != null) {
                item {
                    AssistChip(
                        onClick = { viewModel.setRowCountry(0, suggestion) },
                        label = {
                            Text("Switch to +${suggestion.dialCode} (${suggestion.name})?")
                        }
                    )
                }
            }
            item {
                Field(
                    "Appointment", state.appointment,
                    KeyboardOptions(imeAction = ImeAction.Next), next
                ) {
                    viewModel.update { s -> s.copy(appointment = it) }
                }
            }
            item {
                Field(
                    "Location", state.location,
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    ), next
                ) {
                    viewModel.update { s -> s.copy(location = it) }
                }
            }
            item {
                Field(
                    "Email", state.email,
                    KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    next
                ) {
                    viewModel.update { s -> s.copy(email = it) }
                }
            }
            item {
                Field(
                    "Notes", state.notes,
                    KeyboardOptions(imeAction = ImeAction.Done),
                    KeyboardActions(onDone = { focus.clearFocus() })
                ) {
                    viewModel.update { s -> s.copy(notes = it) }
                }
            }
            item {
                TextButton(onClick = { moreExpanded = !moreExpanded }) {
                    Text(if (moreExpanded) "Fewer fields" else "More fields")
                }
            }
            if (moreExpanded) {
                item {
                    Field("Nickname", state.nickname) {
                        viewModel.update { s -> s.copy(nickname = it) }
                    }
                }
                item {
                    Field("Prefix", state.prefix) {
                        viewModel.update { s -> s.copy(prefix = it) }
                    }
                }
                item {
                    Field("Suffix", state.suffix) {
                        viewModel.update { s -> s.copy(suffix = it) }
                    }
                }
                item {
                    Field(
                        "Website", state.website,
                        KeyboardOptions(keyboardType = KeyboardType.Uri)
                    ) {
                        viewModel.update { s -> s.copy(website = it) }
                    }
                }
                item {
                    OutlinedTextField(
                        value = state.birthday,
                        onValueChange = {},
                        label = { Text("Birthday") },
                        readOnly = true,
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { birthdayDialog = true }) {
                                Icon(
                                    Icons.Filled.DateRange,
                                    contentDescription = "Pick birthday"
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                role = Role.Button,
                                onClick = { birthdayDialog = true }
                            )
                    )
                }
                item {
                    Field("Labels", state.labels) {
                        viewModel.update { s -> s.copy(labels = it) }
                    }
                }
            }
            item {
                Button(
                    onClick = { viewModel.save() },
                    enabled = !state.notFound,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("editor_save")
                ) {
                    Text(if (contactId == null) "Save contact" else "Save changes")
                }
            }
        }
    }

    if (showCountries) {
        ModalBottomSheet(onDismissRequest = { showCountries = false }) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = countryQuery,
                    onValueChange = { countryQuery = it },
                    label = { Text("Search countries") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (recents.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(recents, key = { it.code }) { country: Country ->
                            AssistChip(
                                onClick = {
                                    viewModel.setRowCountry(countryRowTarget, country)
                                    showCountries = false
                                },
                                label = { Text("${country.flag} ${country.name}") }
                            )
                        }
                    }
                }
                val filtered = remember(countryQuery) { filterCountries(countryQuery) }
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(filtered, key = { it.code }) { country: Country ->
                        val selected = country.code ==
                            state.numbers.getOrNull(countryRowTarget)?.country?.code
                        ListItem(
                            headlineContent = { Text("${country.flag} ${country.name}") },
                            supportingContent = { Text("+${country.dialCode}") },
                            trailingContent = {
                                if (selected) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = "Selected"
                                    )
                                }
                            },
                            modifier = Modifier.clickable {
                                viewModel.setRowCountry(countryRowTarget, country)
                                showCountries = false
                            }
                        )
                    }
                }
            }
        }
    }

    if (photoChoice) {
        AlertDialog(
            onDismissRequest = { photoChoice = false },
            title = { Text("Contact photo") },
            text = { Text("Take a new picture or choose one from the gallery.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        photoChoice = false
                        photoLauncher.launch("image/*")
                    }
                ) { Text("Gallery") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            photoChoice = false
                            val file = java.io.File(
                                context.cacheDir,
                                "camera_${System.currentTimeMillis()}.jpg"
                            )
                            cameraUri = FileProvider.getUriForFile(
                                context,
                                "com.hcmdz.privnum.fileprovider",
                                file
                            )
                            val cameraIntent = Intent(
                                android.provider.MediaStore.ACTION_IMAGE_CAPTURE
                            )
                            if (cameraIntent.resolveActivity(context.packageManager) != null) {
                                cameraUri?.let { cameraLauncher.launch(it) }
                            } else {
                                viewModel.showMessage("No camera app found")
                            }
                        }
                    ) { Text("Camera") }
                    TextButton(onClick = { photoChoice = false }) { Text("Cancel") }
                }
            }
        )
    }

    if (birthdayDialog) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = birthdayToMillis(state.birthday)
        )
        DatePickerDialog(
            onDismissRequest = { birthdayDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        dateState.selectedDateMillis?.let { millis ->
                            val date = java.time.Instant.ofEpochMilli(millis)
                                .atZone(java.time.ZoneOffset.UTC)
                                .toLocalDate()
                            viewModel.update { s ->
                                s.copy(
                                    birthday = "%04d-%02d-%02d".format(
                                        date.year, date.monthValue, date.dayOfMonth
                                    )
                                )
                            }
                        }
                        birthdayDialog = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { birthdayDialog = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = dateState)
        }
    }

    if (abandonConfirm) {
        AlertDialog(
            onDismissRequest = { abandonConfirm = false },
            title = { Text("Discard changes?") },
            text = { Text("Your unsaved changes will be lost.") },
            confirmButton = {
                TextButton(onClick = { abandonConfirm = false; onBack() }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { abandonConfirm = false }) { Text("Keep editing") }
            }
        )
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}
