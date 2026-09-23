package com.hcmdz.privnum.preview

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hcmdz.privnum.data.Contact
import com.hcmdz.privnum.data.ContactPhotoStore
import com.hcmdz.privnum.data.ContactRepository
import com.hcmdz.privnum.data.VcfMapper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.MonthDay
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class PreviewUiState(
    val contact: Contact? = null,
    val notFound: Boolean = false,
    val message: String? = null,
    val deleted: Boolean = false
)

/** "--MM-DD" -> "May 4"; full ISO date -> "May 4, 2026"; anything else untouched. */
fun formatContactDate(raw: String): String {
    val trimmed = raw.trim()
    return runCatching {
        if (trimmed.startsWith("--")) {
            MonthDay.parse(trimmed).format(DateTimeFormatter.ofPattern("MMMM d", Locale.US))
        } else {
            LocalDate.parse(trimmed).format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US))
        }
    }.getOrDefault(trimmed)
}

fun whatsappUri(number: String, installed: Boolean): String =
    if (installed) "whatsapp://send?phone=$number"
    else "https://wa.me/$number"

fun telegramUri(number: String, profile: Boolean, installed: Boolean): String =
    if (installed) "tg://resolve?phone=$number${if (profile) "&profile" else ""}"
    else "https://t.me/+$number${if (profile) "?profile" else ""}"

/** Stable 0..2 index mapping a name initial to a Material color role pair. */
fun avatarRoleIndex(letter: Char): Int =
    (letter.uppercaseChar().code % 3 + 3) % 3

@HiltViewModel
class PreviewViewModel @Inject constructor(
    private val repository: ContactRepository,
    private val photos: ContactPhotoStore,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _state = MutableStateFlow(PreviewUiState())
    val state: StateFlow<PreviewUiState> = _state.asStateFlow()

    private var loadedNumber: String? = null

    fun load(fullPhoneNumber: String) {
        if (loadedNumber == fullPhoneNumber) return
        loadedNumber = fullPhoneNumber
        viewModelScope.launch {
            val contact = repository.getByFullNumber(fullPhoneNumber)
            _state.update {
                if (contact == null) it.copy(contact = null, notFound = true)
                else it.copy(contact = contact, notFound = false)
            }
        }
    }

    fun delete() {
        val contact = _state.value.contact ?: return
        viewModelScope.launch {
            if (repository.delete(contact.fullPhoneNumber)) {
                _state.update { it.copy(deleted = true) }
            } else {
                _state.update { it.copy(message = "Delete failed") }
            }
        }
    }

    suspend fun makeShareUri(): Uri? = withContext(Dispatchers.IO) {
        val contact = _state.value.contact ?: return@withContext null
        runCatching {
            val file = File(context.cacheDir, "shared_contact.vcf")
            file.writeText(VcfMapper.contactsToVcf(listOf(contact)))
            FileProvider.getUriForFile(context, "com.hcmdz.privnum.fileprovider", file)
        }.getOrNull()
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    fun photoModel(photo: String): Any? = photos.photoModel(photo)
}
