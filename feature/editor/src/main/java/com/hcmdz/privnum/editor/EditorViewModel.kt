package com.hcmdz.privnum.editor

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hcmdz.privnum.data.Contact
import com.hcmdz.privnum.data.ContactPhotoStore
import com.hcmdz.privnum.data.ContactRepository
import com.hcmdz.privnum.data.Countries
import com.hcmdz.privnum.data.Country
import com.hcmdz.privnum.data.ParsedNumber
import com.hcmdz.privnum.data.PhoneNumberUtils
import com.hcmdz.privnum.data.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class EditorUiState(
    val name: String = "",
    val nationalNumber: String = "",
    val country: Country? = null,
    val photo: String = "",
    val pendingPhotoUri: Uri? = null,
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
        photo == other.photo &&
        pendingPhotoUri == other.pendingPhotoUri &&
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
    private val photos: ContactPhotoStore,
    @ApplicationContext private val context: Context
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

    val recentCountries: StateFlow<List<Country>> =
        settings.recentCountries
            .map { codes -> codes.mapNotNull { Countries.getByCode(it) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
     * originalNumber is always reset first: load() refuses to run twice, which
     * would otherwise show the previous contact when editing another one.
     */
    fun enter(fullPhoneNumber: String?) {
        originalNumber = null
        // Synchronous: LaunchedEffect(saved) fires on first composition, before
        // the async load() below completes. A stale true would pop instantly.
        _state.update { it.copy(saved = false, message = null) }
        if (fullPhoneNumber == null) {
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
                    photo = c.photo,
                    appointment = c.appointment,
                    location = c.location,
                    prefix = c.prefix,
                    suffix = c.suffix,
                    email = c.email,
                    notes = c.notes,
                    website = c.website,
                    birthday = c.birthday,
                    nickname = c.nickname,
                    labels = c.labels,
                    saved = false,
                    message = null
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
        viewModelScope.launch { settings.pushRecentCountry(country.code) }
    }

    fun setPhotoUri(uri: Uri?) {
        _state.update { it.copy(pendingPhotoUri = uri) }
    }

    fun removePhoto() {
        val current = _state.value.photo
        if (current.isNotBlank()) {
            viewModelScope.launch(Dispatchers.IO) { photos.deletePhoto(current) }
        }
        _state.update { it.copy(photo = "", pendingPhotoUri = null) }
    }

    fun photoModel(photo: String): Any? = photos.photoModel(photo)

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
        viewModelScope.launch {
            var finalPhoto = s.photo
            s.pendingPhotoUri?.let { uri ->
                val (bytes, mime) = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }.getOrNull() to photos.contentTypeOf(uri)
                }
                if (bytes != null) {
                    if (finalPhoto.isNotBlank()) {
                        withContext(Dispatchers.IO) { photos.deletePhoto(finalPhoto) }
                    }
                    finalPhoto = withContext(Dispatchers.IO) {
                        photos.savePhoto(bytes, mime)
                    }
                }
            }
            val contact = buildContact(name, parsed!!, s, finalPhoto)
            val original = originalNumber
            val ok = if (original == null) {
                repository.add(contact)
            } else {
                repository.update(original, contact)
            }
            if (ok) {
                _state.update { it.copy(saved = true, photo = finalPhoto, pendingPhotoUri = null) }
                pristine = _state.value
            } else {
                _state.update { it.copy(message = "Contact already exists") }
            }
            onDone(ok)
        }
    }

    private fun buildContact(
        name: String,
        parsed: ParsedNumber,
        s: EditorUiState,
        photo: String
    ): Contact {
        return Contact(
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
            labels = s.labels.trim(),
            photo = photo
        )
    }
}
