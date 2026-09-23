package com.hcmdz.privnum.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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
    fullPhoneNumber: String?,
    entryNonce: Int,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recents by viewModel.recentCountries.collectAsStateWithLifecycle()
    var showCountries by remember { mutableStateOf(false) }
    var countryQuery by rememberSaveable { mutableStateOf("") }
    var moreExpanded by rememberSaveable { mutableStateOf(false) }
    var abandonConfirm by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val focus = LocalFocusManager.current
    val dirty = remember(state) { viewModel.isDirty() }

    BackHandler(enabled = dirty && !state.saved) { abandonConfirm = true }

    LaunchedEffect(entryNonce, fullPhoneNumber) {
        viewModel.enter(fullPhoneNumber)
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

    val preview = remember(state.nationalNumber, state.country) {
        state.country?.let { PhoneNumberUtils.previewNumber(state.nationalNumber, it.code) }
    }
    val suggestion = remember(state.nationalNumber, state.country) {
        val current = state.country
        suggestCountryFor(state.nationalNumber, current?.code ?: "")
            ?.takeIf { it.code != current?.code }
    }
    val next = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) })
    val photoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) viewModel.setPhotoUri(uri) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (fullPhoneNumber == null) "New contact" else "Edit contact") },
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
                        TextButton(onClick = { photoLauncher.launch("image/*") }) {
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
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val country = state.country
                    // Transparent overlay first in tap dispatch: the text field
                    // consumes taps even when read-only and unfocusable.
                    Box(modifier = Modifier.weight(0.42f)) {
                        OutlinedTextField(
                            value = country?.flag ?: "",
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
                                .testTag("editor_country")
                                .semantics {
                                    contentDescription =
                                        "Selected country: ${country?.name ?: "none"}"
                                }
                                .clickable(
                                    role = Role.DropdownList,
                                    onClick = { showCountries = true }
                                )
                        )
                    }
                    OutlinedTextField(
                        value = state.nationalNumber,
                        onValueChange = {
                            viewModel.update { s ->
                                s.copy(
                                    nationalNumber = PhoneNumberUtils.trimPhoneInput(it),
                                    numberError = null
                                )
                            }
                        },
                        label = { Text("Phone number *") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Phone,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = next,
                        isError = state.numberError != null,
                        supportingText = {
                            state.numberError?.let { Text(it) }
                                ?: preview?.let { Text("Will be saved as $it") }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("editor_number")
                    )
                }
            }
            if (suggestion != null) {
                item {
                    AssistChip(
                        onClick = { viewModel.setCountry(suggestion) },
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
                    Field("Birthday", state.birthday) {
                        viewModel.update { s -> s.copy(birthday = it) }
                    }
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
                    Text(if (fullPhoneNumber == null) "Save contact" else "Save changes")
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
                                    viewModel.setCountry(country)
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
                        val selected = country.code == state.country?.code
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
                                viewModel.setCountry(country)
                                showCountries = false
                            }
                        )
                    }
                }
            }
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
