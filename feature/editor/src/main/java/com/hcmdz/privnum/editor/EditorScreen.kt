package com.hcmdz.privnum.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hcmdz.privnum.data.Countries
import com.hcmdz.privnum.data.Country

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    fullPhoneNumber: String?,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showCountries by remember { mutableStateOf(false) }
    var countryQuery by remember { mutableStateOf("") }

    LaunchedEffect(fullPhoneNumber) {
        if (fullPhoneNumber != null) viewModel.load(fullPhoneNumber)
    }
    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = { value ->
                        viewModel.update { s -> s.copy(name = value, nameError = null) }
                    },
                    label = { Text("Name *") },
                    isError = state.nameError != null,
                    supportingText = { state.nameError?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showCountries = true }) {
                        Text(state.country?.let { "${it.flag} +${it.dialCode}" } ?: "Country")
                    }
                    OutlinedTextField(
                        value = state.nationalNumber,
                        onValueChange = {
                            viewModel.update { s -> s.copy(nationalNumber = it, numberError = null) }
                        },
                        label = { Text("Phone number *") },
                        isError = state.numberError != null,
                        supportingText = { state.numberError?.let { Text(it) } },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item {
                Field("Appointment", state.appointment) {
                    viewModel.update { s -> s.copy(appointment = it) }
                }
            }
            item {
                Field("Location", state.location) {
                    viewModel.update { s -> s.copy(location = it) }
                }
            }
            item {
                Field("Email", state.email) {
                    viewModel.update { s -> s.copy(email = it) }
                }
            }
            item {
                Field("Notes", state.notes) {
                    viewModel.update { s -> s.copy(notes = it) }
                }
            }
            item {
                Button(
                    onClick = { viewModel.save() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (fullPhoneNumber == null) "Save contact" else "Save changes")
                }
            }
        }
    }

    if (showCountries) {
        ModalBottomSheet(onDismissRequest = { showCountries = false }) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = countryQuery,
                    onValueChange = { countryQuery = it },
                    label = { Text("Search countries") },
                    modifier = Modifier.fillMaxWidth()
                )
                val filtered = Countries.all.filter {
                    it.name.contains(countryQuery, ignoreCase = true) ||
                        it.dialCode.contains(countryQuery)
                }
                LazyColumn {
                    items(filtered, key = { it.code }) { country: Country ->
                        ListItem(
                            headlineContent = { Text("${country.flag} ${country.name}") },
                            supportingContent = { Text("+${country.dialCode}") },
                            modifier = Modifier.clickable {
                                viewModel.update { s -> s.copy(country = country) }
                                showCountries = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth()
    )
}
