package com.hcmdz.privnum.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hcmdz.privnum.data.Contact

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onPreview: (Contact) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    var text by remember { mutableStateOf("") }
    val results by viewModel.results.collectAsStateWithLifecycle()
    val searching by viewModel.isSearching.collectAsStateWithLifecycle()
    val locale = LocalLocale.current.platformLocale
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { focus.requestFocus() }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    viewModel.onQueryChange(it)
                },
                label = { Text(stringResource(R.string.search_field_label)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searching) CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .focusRequester(focus)
            )
            if (!searching && results.isEmpty()) {
                Text(
                    if (text.isBlank()) {
                        stringResource(R.string.search_empty_prompt)
                    } else {
                        stringResource(R.string.search_no_results)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            var lastLetter = ""
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                results.forEach { contact ->
                    val letter = contact.displayName().firstOrNull()?.uppercase(locale) ?: "#"
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
                    item(key = contact.id) {
                        ListItem(
                            headlineContent = { Text(contact.displayName()) },
                            supportingContent = {
                                Text(
                                    contact.primaryNumber()?.let { "+${it.full}" }.orEmpty()
                                )
                            },
                            modifier = Modifier.clickable { onPreview(contact) }
                        )
                    }
                }
            }
        }
    }
}
