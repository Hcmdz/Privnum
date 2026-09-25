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
    private val selected = MutableStateFlow<Set<Long>>(emptySet())

    val uiState: StateFlow<ContactsUiState> =
        combine(repository.observeContacts(), selected) { contacts, selection ->
            ContactsUiState(
                items = contacts.map {
                    ContactListItem(it, it.id in selection)
                },
                selectionMode = selection.isNotEmpty(),
                selectedCount = selection.size
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ContactsUiState())

    fun toggleSelection(id: Long) {
        selected.value = selected.value.toMutableSet().also { set ->
            if (!set.add(id)) set.remove(id)
        }
    }

    fun clearSelection() {
        selected.value = emptySet()
    }

    fun deleteSelected(onDone: (Boolean) -> Unit = {}) {
        val ids = selected.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val ok = repository.deleteMultiple(ids)
            if (ok) clearSelection()
            onDone(ok)
        }
    }

    fun delete(contact: Contact, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            onDone(repository.delete(contact.id))
        }
    }

    fun photoModel(photo: String): Any? = photos.photoModel(photo)

    fun isPasscodeSet(): Boolean = passcode.passcodeEnabled && passcode.hasPin()
}

data class ContactClipboardLabels(
    val name: String,
    val phone: String,
    val additionalPhone: String,
    val email: String,
    val appointment: String,
    val location: String,
    val notes: String,
    val nickname: String,
    val website: String,
    val birthday: String,
    val labels: String,
    val prefix: String,
    val suffix: String
)

/** "Label - value" lines for non-empty fields, contacts separated by a blank line. */
fun contactClipboardLines(
    contacts: List<Contact>,
    labels: ContactClipboardLabels
): String =
    contacts.joinToString("\n\n") { contact ->
        listOf(
            labels.name to contact.displayName(),
        ).plus(
            contact.numbers.mapIndexed { index, number ->
                (if (index == 0) labels.phone else labels.additionalPhone.format(index + 1)) to "+${number.full}"
            }
        ).plus(
            listOf(
                labels.email to contact.email,
                labels.appointment to contact.appointment,
                labels.location to contact.location,
                labels.notes to contact.notes,
                labels.nickname to contact.nickname,
                labels.website to contact.website,
                labels.birthday to contact.birthday,
                labels.labels to contact.labels,
                labels.prefix to contact.prefix,
                labels.suffix to contact.suffix
            )
        ).filter { it.second.isNotBlank() }
            .joinToString("\n") { (label, value) -> "$label - $value" }
    }
