package com.hcmdz.privnum.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hcmdz.privnum.data.Contact
import com.hcmdz.privnum.data.ContactPhotoStore
import com.hcmdz.privnum.data.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ContactListItem(
    val contact: Contact,
    val selected: Boolean = false
)

data class ContactsUiState(
    val items: List<ContactListItem> = emptyList(),
    val selectionMode: Boolean = false,
    val selectedCount: Int = 0
)

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val repository: ContactRepository,
    private val photos: ContactPhotoStore
) : ViewModel() {
    private val selected = MutableStateFlow<Set<String>>(emptySet())

    val uiState: StateFlow<ContactsUiState> =
        combine(repository.observeContacts(), selected) { contacts, selection ->
            ContactsUiState(
                items = contacts.map {
                    ContactListItem(it, it.fullPhoneNumber in selection)
                },
                selectionMode = selection.isNotEmpty(),
                selectedCount = selection.size
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ContactsUiState())

    fun toggleSelection(fullPhoneNumber: String) {
        selected.value = selected.value.toMutableSet().also { set ->
            if (!set.add(fullPhoneNumber)) set.remove(fullPhoneNumber)
        }
    }

    fun clearSelection() {
        selected.value = emptySet()
    }

    fun deleteSelected(onDone: (Boolean) -> Unit = {}) {
        val numbers = selected.value.toList()
        if (numbers.isEmpty()) return
        viewModelScope.launch {
            val ok = repository.deleteMultiple(numbers)
            if (ok) clearSelection()
            onDone(ok)
        }
    }

    fun delete(contact: Contact, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            onDone(repository.delete(contact.fullPhoneNumber))
        }
    }

    fun photoModel(photo: String): Any? = photos.photoModel(photo)
}
