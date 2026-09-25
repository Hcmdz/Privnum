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
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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
import com.hcmdz.privnum.data.displayName
import com.hcmdz.privnum.data.PhoneNumberUtils
import com.hcmdz.privnum.data.filterCountries
import com.hcmdz.privnum.data.suggestCountryFor
import com.hcmdz.privnum.ui.ContactAvatar
import com.hcmdz.privnum.ui.UiText
import com.hcmdz.privnum.ui.resolveText

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
    val locale = LocalLocale.current.platformLocale
    val recents by viewModel.recentCountries.collectAsStateWithLifecycle()
    var showCountries by remember { mutableStateOf(false) }
    var countryRowTarget by remember { mutableStateOf(0) }
    var countryQuery by rememberSaveable { mutableStateOf("") }
    var moreExpanded by rememberSaveable { mutableStateOf(false) }
    var abandonConfirm by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val focus = LocalFocusManager.current
    val dirty = remember(state) { viewModel.isDirty() }
    val message = state.message?.resolveText()
    val nameError = state.nameError?.resolveText()
    val numberError = state.numberError?.resolveText()

    BackHandler(enabled = dirty && !state.saved) { abandonConfirm = true }

    LaunchedEffect(entryNonce, contactId) {
        viewModel.enter(contactId)
    }
    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
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
                title = {
                    Text(
                        if (contactId == null) {
                            stringResource(R.string.editor_new_contact)
                        } else {
                            stringResource(R.string.editor_edit_contact)
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (dirty && !state.saved) abandonConfirm = true else onBack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.editor_back)
                        )
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
                        stringResource(R.string.editor_contact_not_found),
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
                            Text(
                                if (photoModel == null) {
                                    stringResource(R.string.editor_add_photo)
                                } else {
                                    stringResource(R.string.editor_change_photo)
                                }
                            )
                        }
                        if (photoModel != null) {
                            TextButton(onClick = { viewModel.removePhoto() }) {
                                Text(stringResource(R.string.editor_remove))
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
                    label = { Text(stringResource(R.string.editor_name)) },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = next,
                    isError = state.nameError != null,
                    supportingText = { nameError?.let { Text(it) } },
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
                        val countryDescription = stringResource(
                            R.string.editor_selected_country_description,
                             country?.displayName(locale) ?: stringResource(R.string.editor_no_country)
                        )
                        // Transparent overlay first in tap dispatch: the text field
                        // consumes taps even when read-only and unfocusable.
                        Box(modifier = Modifier.weight(0.55f)) {
                            OutlinedTextField(
                                value = country?.let { "${it.flag} +${it.dialCode}" }
                                    ?: "",
                                onValueChange = {},
                                label = { Text(stringResource(R.string.editor_country)) },
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
                                        contentDescription = countryDescription
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
                            label = { Text(stringResource(R.string.editor_phone_number)) },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Phone,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = next,
                            isError = state.numberErrorRow == index,
                            supportingText = {
                                if (state.numberErrorRow == index) {
                                    numberError?.let { Text(it) }
                                } else if (index == 0) {
                                    preview?.let {
                                        Text(stringResource(R.string.editor_will_be_saved_as, it))
                                    }
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
                            stringResource(R.string.editor_primary),
                            modifier = Modifier.clickable { viewModel.setPrimaryRow(index) }
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (state.numbers.size > 1) {
                            TextButton(onClick = { viewModel.removeNumberRow(index) }) {
                                Text(stringResource(R.string.editor_remove))
                            }
                        }
                    }
                }
            }
            item {
                TextButton(onClick = { viewModel.addNumberRow() }) {
                    Text(stringResource(R.string.editor_add_another_number))
                }
            }
            if (suggestion != null) {
                item {
                    AssistChip(
                        onClick = { viewModel.setRowCountry(0, suggestion) },
                        label = {
                            Text(
                                stringResource(
                                    R.string.editor_switch_country,
                                    suggestion.dialCode,
                                    suggestion.displayName(locale)
                                )
                            )
                        }
                    )
                }
            }
            item {
                Field(
                    stringResource(R.string.editor_appointment), state.appointment,
                    KeyboardOptions(imeAction = ImeAction.Next), next
                ) {
                    viewModel.update { s -> s.copy(appointment = it) }
                }
            }
            item {
                Field(
                    stringResource(R.string.editor_location), state.location,
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
                    stringResource(R.string.editor_email), state.email,
                    KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    next
                ) {
                    viewModel.update { s -> s.copy(email = it) }
                }
            }
            item {
                Field(
                    stringResource(R.string.editor_notes), state.notes,
                    KeyboardOptions(imeAction = ImeAction.Done),
                    KeyboardActions(onDone = { focus.clearFocus() })
                ) {
                    viewModel.update { s -> s.copy(notes = it) }
                }
            }
            item {
                TextButton(onClick = { moreExpanded = !moreExpanded }) {
                    Text(
                        if (moreExpanded) {
                            stringResource(R.string.editor_fewer_fields)
                        } else {
                            stringResource(R.string.editor_more_fields)
                        }
                    )
                }
            }
            if (moreExpanded) {
                item {
                    Field(stringResource(R.string.editor_nickname), state.nickname) {
                        viewModel.update { s -> s.copy(nickname = it) }
                    }
                }
                item {
                    Field(stringResource(R.string.editor_prefix), state.prefix) {
                        viewModel.update { s -> s.copy(prefix = it) }
                    }
                }
                item {
                    Field(stringResource(R.string.editor_suffix), state.suffix) {
                        viewModel.update { s -> s.copy(suffix = it) }
                    }
                }
                item {
                    Field(
                        stringResource(R.string.editor_website), state.website,
                        KeyboardOptions(keyboardType = KeyboardType.Uri)
                    ) {
                        viewModel.update { s -> s.copy(website = it) }
                    }
                }
                item {
                    OutlinedTextField(
                        value = state.birthday,
                        onValueChange = {},
                        label = { Text(stringResource(R.string.editor_birthday)) },
                        readOnly = true,
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { birthdayDialog = true }) {
                                Icon(
                                    Icons.Filled.DateRange,
                                    contentDescription = stringResource(R.string.editor_pick_birthday)
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
                    Field(stringResource(R.string.editor_labels), state.labels) {
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
                    Text(
                        if (contactId == null) {
                            stringResource(R.string.editor_save_contact)
                        } else {
                            stringResource(R.string.editor_save_changes)
                        }
                    )
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
                    label = { Text(stringResource(R.string.editor_search_countries)) },
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
                                label = { Text("${country.flag} ${country.displayName(locale)}") }
                            )
                        }
                    }
                }
                val filtered = remember(countryQuery, locale) { filterCountries(countryQuery, locale) }
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(filtered, key = { it.code }) { country: Country ->
                        val selected = country.code ==
                            state.numbers.getOrNull(countryRowTarget)?.country?.code
                        ListItem(
                            headlineContent = { Text("${country.flag} ${country.displayName(locale)}") },
                            supportingContent = { Text("+${country.dialCode}") },
                            trailingContent = {
                                if (selected) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = stringResource(R.string.editor_selected)
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
            title = { Text(stringResource(R.string.editor_contact_photo)) },
            text = { Text(stringResource(R.string.editor_photo_choice_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        photoChoice = false
                        photoLauncher.launch("image/*")
                    }
                ) { Text(stringResource(R.string.editor_gallery)) }
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
                                viewModel.showMessage(
                                    UiText.Resource(R.string.editor_no_camera_app)
                                )
                            }
                        }
                    ) { Text(stringResource(R.string.editor_camera)) }
                    TextButton(onClick = { photoChoice = false }) {
                        Text(stringResource(R.string.editor_cancel))
                    }
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
                ) { Text(stringResource(R.string.editor_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { birthdayDialog = false }) {
                    Text(stringResource(R.string.editor_cancel))
                }
            }
        ) {
            DatePicker(state = dateState)
        }
    }

    if (abandonConfirm) {
        AlertDialog(
            onDismissRequest = { abandonConfirm = false },
            title = { Text(stringResource(R.string.editor_discard_changes_title)) },
            text = { Text(stringResource(R.string.editor_discard_changes_message)) },
            confirmButton = {
                TextButton(onClick = { abandonConfirm = false; onBack() }) {
                    Text(stringResource(R.string.editor_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { abandonConfirm = false }) {
                    Text(stringResource(R.string.editor_keep_editing))
                }
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
