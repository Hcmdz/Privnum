package com.hcmdz.privnum.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hcmdz.privnum.data.Contact
import com.hcmdz.privnum.data.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: ContactRepository
) : ViewModel() {
    private val query = MutableStateFlow("")

    val results: StateFlow<List<Contact>> =
        query
            .debounce(300)
            .flatMapLatest { q ->
                if (q.isBlank()) flowOf(emptyList()) else flowOf(repository.search(q))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onQueryChange(value: String) {
        query.value = value
    }
}
