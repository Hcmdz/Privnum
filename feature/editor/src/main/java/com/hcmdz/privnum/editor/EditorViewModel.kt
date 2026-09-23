package com.hcmdz.privnum.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hcmdz.privnum.data.Contact
import com.hcmdz.privnum.data.ContactRepository
import com.hcmdz.privnum.data.Countries
import com.hcmdz.privnum.data.Country
import com.hcmdz.privnum.data.PhoneNumberUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditorUiState(
    val name: String = "",
    val nationalNumber: String = "",
    val country: Country? = null,
    val appointment: String = "",
    val location: String = "",
    val prefix: String = "",
    val suffix: String = "",
    val email: String = "",
    val notes: String = "",
    val website: String = "",
    val birthday: String = "",
    val nickname: String = "",
    val labels: String = "",
    val nameError: String? = null,
    val numberError: String? = null,
    val saved: Boolean = false,
    val saveError: Boolean = false
)

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val repository: ContactRepository
) : ViewModel() {
    private val _state = MutableStateFlow(EditorUiState(country = Countries.getByCode("DZ")))
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private var originalNumber: String? = null

    fun load(fullPhoneNumber: String) {
        if (originalNumber != null) return
        originalNumber = fullPhoneNumber
        viewModelScope.launch {
            repository.getByFullNumber(fullPhoneNumber)?.let { c ->
                _state.update {
                    it.copy(
                        name = c.name,
                        nationalNumber = c.phoneNumber,
                        country = Countries.getByCode(c.countryCode),
                        appointment = c.appointment,
                        location = c.location,
                        prefix = c.prefix,
                        suffix = c.suffix,
                        email = c.email,
                        notes = c.notes,
                        website = c.website,
                        birthday = c.birthday,
                        nickname = c.nickname,
                        labels = c.labels
                    )
                }
            }
        }
    }

    fun update(mutator: (EditorUiState) -> EditorUiState) {
        _state.update(mutator)
    }

    fun save(onDone: (Boolean) -> Unit = {}) {
        val s = _state.value
        val name = s.name.trim()
        val digits = s.nationalNumber.filter { it.isDigit() }
        var nameError: String? = null
        var numberError: String? = null
        if (name.length < 2) nameError = "Name must be at least 2 characters"
        val country = s.country
        if (country == null) {
            numberError = "Select a country"
        } else if (!PhoneNumberUtils.isValid(digits, country.code)) {
            numberError = "Invalid phone number"
        }
        if (nameError != null || numberError != null) {
            _state.update { it.copy(nameError = nameError, numberError = numberError) }
            return
        }
        val parsed = PhoneNumberUtils.parse(digits, country!!.code)
            ?: run {
                _state.update { it.copy(numberError = "Invalid phone number") }
                return
            }
        val contact = Contact(
            fullPhoneNumber = parsed.fullNumber,
            phoneNumber = parsed.nationalNumber,
            countryCode = parsed.countryIso,
            name = name,
            appointment = s.appointment.trim(),
            location = s.location.trim(),
            prefix = s.prefix.trim(),
            suffix = s.suffix.trim(),
            email = s.email.trim(),
            notes = s.notes.trim(),
            website = s.website.trim(),
            birthday = s.birthday.trim(),
            nickname = s.nickname.trim(),
            labels = s.labels.trim()
        )
        viewModelScope.launch {
            val original = originalNumber
            val ok = if (original == null) {
                repository.add(contact)
            } else {
                repository.update(original, contact)
            }
            if (ok) {
                _state.update { it.copy(saved = true) }
            } else {
                _state.update { it.copy(saveError = true) }
            }
            onDone(ok)
        }
    }
}
