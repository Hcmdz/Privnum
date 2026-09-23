package com.hcmdz.privnum.editor

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hcmdz.privnum.data.Contact
import com.hcmdz.privnum.data.ContactRepository
import com.hcmdz.privnum.data.Countries
import com.hcmdz.privnum.data.Country
import com.hcmdz.privnum.data.PhoneNumberUtils
import com.hcmdz.privnum.data.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    val message: String? = null,
    val notFound: Boolean = false,
    val saved: Boolean = false
)

/** User setting wins over SIM; "DZ" preserves the historical default. */
fun resolveDefaultCountry(setting: String?, simIso: String): Country =
    setting?.let { Countries.getByCode(it) }
        ?: Countries.getByCode(simIso)
        ?: Countries.getByCode("DZ")!!

/** Input-field equality, ignoring transient UI flags. */
fun EditorUiState.inputsEqual(other: EditorUiState): Boolean =
    name == other.name &&
        nationalNumber == other.nationalNumber &&
        country == other.country &&
        appointment == other.appointment &&
        location == other.location &&
        prefix == other.prefix &&
        suffix == other.suffix &&
        email == other.email &&
        notes == other.notes &&
        website == other.website &&
        birthday == other.birthday &&
        nickname == other.nickname &&
        labels == other.labels

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val repository: ContactRepository,
    private val settings: SettingsStore,
    @ApplicationContext context: Context
) : ViewModel() {
    private val _state = MutableStateFlow(
        EditorUiState(
            country = resolveDefaultCountry(null, Countries.simRegion(context))
        )
    )
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private var originalNumber: String? = null
    private var countryTouched = false
    private var pristine = _state.value
    private val initialCountry = _state.value.country

    init {
        viewModelScope.launch {
            val setting = settings.defaultRegion.first()
            if (!countryTouched && setting != null) {
                val country = resolveDefaultCountry(setting, "")
                _state.update { it.copy(country = country) }
                pristine = _state.value
            }
        }
    }

    /**
     * Called on screen entry: a null number means a new contact and must reset
     * any draft leaked by a shared ViewModelStoreOwner across backstack entries.
     */
    fun enter(fullPhoneNumber: String?) {
        if (fullPhoneNumber == null) {
            originalNumber = null
            countryTouched = false
            val fresh = EditorUiState(country = initialCountry)
            _state.value = fresh
            pristine = fresh
        } else {
            load(fullPhoneNumber)
        }
    }

    fun load(fullPhoneNumber: String) {
        if (originalNumber != null) return
        originalNumber = fullPhoneNumber
        viewModelScope.launch {
            val c = repository.getByFullNumber(fullPhoneNumber)
            if (c == null) {
                _state.update { it.copy(notFound = true) }
                return@launch
            }
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
            pristine = _state.value
        }
    }

    fun update(mutator: (EditorUiState) -> EditorUiState) {
        _state.update(mutator)
    }

    fun setCountry(country: Country) {
        countryTouched = true
        _state.update { it.copy(country = country, numberError = null) }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    fun isDirty(): Boolean = !_state.value.inputsEqual(pristine)

    fun save(onDone: (Boolean) -> Unit = {}) {
        val s = _state.value
        if (s.notFound) {
            onDone(false)
            return
        }
        val name = s.name.trim()
        var nameError: String? = null
        var numberError: String? = null
        if (name.length < 2) nameError = "Name must be at least 2 characters"
        val country = s.country
        val parsed = country?.let { PhoneNumberUtils.parseForSave(s.nationalNumber, it.code) }
        if (country == null) {
            numberError = "Select a country"
        } else if (parsed == null ||
            !PhoneNumberUtils.isValid("+" + parsed.fullNumber, parsed.countryIso)
        ) {
            numberError = "Invalid phone number"
        }
        if (nameError != null || numberError != null) {
            _state.update { it.copy(nameError = nameError, numberError = numberError) }
            return
        }
        val contact = Contact(
            fullPhoneNumber = parsed!!.fullNumber,
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
                pristine = _state.value
            } else {
                _state.update { it.copy(message = "Contact already exists") }
            }
            onDone(ok)
        }
    }
}
