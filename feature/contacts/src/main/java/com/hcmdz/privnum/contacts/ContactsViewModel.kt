package com.hcmdz.privnum.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hcmdz.privnum.data.Contact
import com.hcmdz.privnum.data.ContactPhotoStore
import com.hcmdz.privnum.data.ContactRepository
import com.hcmdz.privnum.data.PasscodeStore
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
    private val photos: ContactPhotoStore,
    private val passcode: PasscodeStore
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

    fun isPasscodeSet(): Boolean = passcode.passcodeEnabled && passcode.hasPin()
}

/** "Label - value" lines for non-empty fields, contacts separated by a blank line. */
fun contactClipboardLines(contacts: List<Contact>): String =
    contacts.joinToString("\n\n") { contact ->
        listOf(
            "Name" to contact.displayName(),
            "Phone" to "+${contact.fullPhoneNumber}",
            "Email" to contact.email,
            "Appointment" to contact.appointment,
            "Location" to contact.location,
            "Notes" to contact.notes,
            "Nickname" to contact.nickname,
            "Website" to contact.website,
            "Birthday" to contact.birthday,
            "Labels" to contact.labels,
            "Prefix" to contact.prefix,
            "Suffix" to contact.suffix
        ).filter { it.second.isNotBlank() }
            .joinToString("\n") { (label, value) -> "$label - $value" }
    }
