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
import com.hcmdz.privnum.data.PhoneNumberRef
import com.hcmdz.privnum.data.PhoneNumberUtils
import com.hcmdz.privnum.data.SaveResult
import com.hcmdz.privnum.data.SettingsStore
import com.hcmdz.privnum.ui.UiText
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

data class NumberRow(
    val nationalNumber: String = "",
    val country: Country? = null,
    val primary: Boolean = false
)

data class EditorUiState(
    val name: String = "",
    val numbers: List<NumberRow> = emptyList(),
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
    val nameError: UiText? = null,
    val numberError: UiText? = null,
    val numberErrorRow: Int = -1,
    val message: UiText? = null,
    val notFound: Boolean = false,
    val saved: Boolean = false
)

/** User setting wins over SIM; "DZ" preserves the historical default. */
fun resolveDefaultCountry(setting: String?, simIso: String): Country =
    setting?.let { Countries.getByCode(it) }
        ?: Countries.getByCode(simIso)
        ?: Countries.getByCode("DZ")!!

/** Initial DatePicker millis for stored birthdays, null when unparseable. */
fun birthdayToMillis(raw: String): Long? {
    val trimmed = raw.trim()
    return runCatching {
        if (trimmed.startsWith("--")) {
            val parts = trimmed.removePrefix("--").split("-")
            java.time.LocalDate.of(
                java.time.LocalDate.now().year, parts[0].toInt(), parts[1].toInt()
            )
        } else {
            java.time.LocalDate.parse(trimmed)
        }.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
    }.getOrNull()
}

/** Input-field equality, ignoring transient UI flags. */
fun EditorUiState.inputsEqual(other: EditorUiState): Boolean =
    name == other.name &&
        numbers == other.numbers &&
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
    private val initialCountry: Country = resolveDefaultCountry(null, Countries.simRegion(context))

    private fun freshState() = EditorUiState(
        numbers = listOf(NumberRow(country = initialCountry))
    )

    private val _state = MutableStateFlow(freshState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private var originalId: Long? = null
    private var countryTouched = false
    private var pristine = _state.value

    val recentCountries: StateFlow<List<Country>> =
        settings.recentCountries
            .map { codes -> codes.mapNotNull { Countries.getByCode(it) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            val setting = settings.defaultRegion.first()
            if (!countryTouched && setting != null) {
                val country = resolveDefaultCountry(setting, "")
                _state.update { s ->
                    s.copy(
                        numbers = s.numbers.mapIndexed { index, row ->
                            if (index == 0) row.copy(country = country) else row
                        }
                    )
                }
                pristine = _state.value
            }
        }
    }

    /**
     * Called on screen entry: a null id means a new contact and must reset
     * any draft leaked by a shared ViewModelStoreOwner across backstack entries.
     * originalId is always reset first: load() refuses to run twice, which
     * would otherwise show the previous contact when editing another one.
     */
    fun enter(contactId: Long?) {
        originalId = null
        // Synchronous: LaunchedEffect(saved) fires on first composition, before
        // the async load() below completes. A stale true would pop instantly.
        _state.update { it.copy(saved = false, message = null) }
        if (contactId == null) {
            countryTouched = false
            val fresh = freshState()
            _state.value = fresh
            pristine = fresh
        } else {
            load(contactId)
        }
    }

    fun load(contactId: Long) {
        if (originalId != null) return
        originalId = contactId
        viewModelScope.launch {
            val c = repository.getById(contactId)
            if (c == null) {
                _state.update { it.copy(notFound = true) }
                return@launch
            }
            _state.update {
                it.copy(
                    name = c.name,
                    numbers = c.numbers.map { n ->
                        NumberRow(
                            nationalNumber = n.national,
                            country = Countries.getByCode(n.country),
                            primary = n.primary
                        )
                    }.ifEmpty { listOf(NumberRow(country = initialCountry)) },
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

    fun updateRow(index: Int, mutator: (NumberRow) -> NumberRow) {
        _state.update { s ->
            s.copy(
                numbers = s.numbers.mapIndexed { i, row ->
                    if (i == index) mutator(row) else row
                },
                numberError = if (s.numberErrorRow == index) null else s.numberError,
                numberErrorRow = if (s.numberErrorRow == index) -1 else s.numberErrorRow
            )
        }
    }

    fun setCountry(country: Country) = setRowCountry(0, country)

    fun setRowCountry(index: Int, country: Country) {
        if (index == 0) countryTouched = true
        updateRow(index) { it.copy(country = country) }
        viewModelScope.launch { settings.pushRecentCountry(country.code) }
    }

    fun addNumberRow() {
        _state.update { s ->
            val fallback = s.numbers.lastOrNull()?.country ?: initialCountry
            s.copy(numbers = s.numbers + NumberRow(country = fallback))
        }
    }

    fun removeNumberRow(index: Int) {
        _state.update { s ->
            if (s.numbers.size <= 1) return@update s
            val remaining = s.numbers.filterIndexed { i, _ -> i != index }
            val fixed = if (remaining.none { it.primary }) {
                remaining.mapIndexed { i, row -> row.copy(primary = i == 0) }
            } else remaining
            s.copy(numbers = fixed, numberError = null, numberErrorRow = -1)
        }
    }

    fun setPrimaryRow(index: Int) {
        _state.update { s ->
            s.copy(numbers = s.numbers.mapIndexed { i, row -> row.copy(primary = i == index) })
        }
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

    fun showMessage(text: UiText) {
        _state.update { it.copy(message = text) }
    }

    fun isDirty(): Boolean = !_state.value.inputsEqual(pristine)

    fun save(onDone: (Boolean) -> Unit = {}) {
        val s = _state.value
        if (s.notFound) {
            onDone(false)
            return
        }
        val name = s.name.trim()
        var nameError: UiText? = null
        var numberError: UiText? = null
        var numberErrorRow = -1
        if (name.length < 2) nameError = UiText.Resource(R.string.editor_error_name_too_short)
        val parsedRows = mutableListOf<PhoneNumberRef>()
        s.numbers.forEachIndexed { index, row ->
            val error = parseRow(row)
            if (error != null) {
                if (numberError == null) {
                    numberError = error
                    numberErrorRow = index
                }
            } else {
                parseRowValue(row)?.let { parsedRows.add(it) }
            }
        }
        if (nameError != null || numberError != null) {
            _state.update {
                it.copy(
                    nameError = nameError,
                    numberError = numberError,
                    numberErrorRow = numberErrorRow
                )
            }
            return
        }
        viewModelScope.launch {
            var finalPhoto = s.photo
            s.pendingPhotoUri?.let { uri ->
                val bytes = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }.getOrNull()
                }
                if (bytes != null) {
                    if (finalPhoto.isNotBlank()) {
                        withContext(Dispatchers.IO) { photos.deletePhoto(finalPhoto) }
                    }
                    finalPhoto = withContext(Dispatchers.IO) {
                        photos.savePickedPhoto(bytes)
                    }
                }
            }
            val contact = buildContact(name, parsedRows, s, finalPhoto)
            val original = originalId
            val result = if (original == null) {
                repository.add(contact)
            } else {
                repository.update(original, contact)
            }
            when (result) {
                is SaveResult.Saved -> {
                    _state.update {
                        it.copy(saved = true, photo = finalPhoto, pendingPhotoUri = null)
                    }
                    pristine = _state.value
                    onDone(true)
                }
                is SaveResult.DuplicateNumber -> {
                    val message = if (result.ownerName.isBlank()) {
                        UiText.Resource(R.string.editor_error_duplicate_number_unknown)
                    } else {
                        UiText.Resource(
                            R.string.editor_error_duplicate_number,
                            listOf(result.ownerName)
                        )
                    }
                    _state.update { it.copy(message = message) }
                    onDone(false)
                }
                is SaveResult.NotFound -> {
                    _state.update { it.copy(notFound = true) }
                    onDone(false)
                }
            }
        }
    }

    /** Error message for an invalid row, null when the row parses. */
    private fun parseRow(row: NumberRow): UiText? {
        val country = row.country
        val digits = row.nationalNumber.filter { it.isDigit() }
        val parsed = country?.let { PhoneNumberUtils.parseForSave(row.nationalNumber, it.code) }
        return when {
            country == null -> UiText.Resource(R.string.editor_error_select_country)
            digits.length < PhoneNumberUtils.MIN_PHONE_DIGITS ->
                UiText.Resource(R.string.editor_error_too_short)
            parsed == null ||
                !PhoneNumberUtils.isValid("+" + parsed.fullNumber, parsed.countryIso) ->
                UiText.Resource(R.string.editor_error_invalid_phone_number)
            else -> null
        }
    }

    private fun parseRowValue(row: NumberRow): PhoneNumberRef? {
        val parsed = row.country?.let { PhoneNumberUtils.parseForSave(row.nationalNumber, it.code) }
            ?: return null
        return PhoneNumberRef(
            full = parsed.fullNumber,
            national = parsed.nationalNumber,
            country = parsed.countryIso,
            primary = row.primary
        )
    }

    private fun buildContact(
        name: String,
        numbers: List<PhoneNumberRef>,
        s: EditorUiState,
        photo: String
    ): Contact {
        return Contact(
            name = name,
            numbers = numbers,
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
