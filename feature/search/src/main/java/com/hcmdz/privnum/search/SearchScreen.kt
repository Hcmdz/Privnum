package com.hcmdz.privnum.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hcmdz.privnum.data.Contact

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel = hiltViewModel()
) {
    var text by remember { mutableStateOf("") }
    val results by viewModel.results.collectAsStateWithLifecycle()

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
                label = { Text("Search contacts") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(results, key = { it.fullPhoneNumber }) { contact: Contact ->
                    ListItem(
                        headlineContent = { Text(contact.displayName()) },
                        supportingContent = { Text("+${contact.fullPhoneNumber}") }
                    )
                }
            }
        }
    }
}
