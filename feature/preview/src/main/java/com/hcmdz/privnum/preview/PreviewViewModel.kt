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
import com.hcmdz.privnum.data.shareFile
import com.hcmdz.privnum.ui.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.MonthDay
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class PreviewUiState(
    val contact: Contact? = null,
    val notFound: Boolean = false,
    val message: UiText? = null,
    val deleted: Boolean = false
)

fun formatContactDate(raw: String, locale: Locale): String {
    val trimmed = raw.trim()
    return runCatching {
        if (trimmed.startsWith("--")) {
            MonthDay.parse(trimmed).format(DateTimeFormatter.ofPattern("MMMM d", locale))
        } else {
            LocalDate.parse(trimmed).format(DateTimeFormatter.ofPattern("MMMM d, yyyy", locale))
        }
    }.getOrDefault(trimmed)
}

fun whatsappUri(number: String, installed: Boolean): String =
    if (installed) "whatsapp://send?phone=$number"
    else "https://wa.me/$number"

fun telegramUri(number: String, profile: Boolean, installed: Boolean): String =
    if (installed) "tg://resolve?phone=$number${if (profile) "&profile" else ""}"
    else "https://t.me/+$number${if (profile) "?profile" else ""}"

/** Explicit package for an installed external app, so the launch Intent is explicit. */
fun whatsappPackage(installed: Boolean): String? =
    if (installed) "com.whatsapp" else null

/** Explicit package for an installed external app, so the launch Intent is explicit. */
fun telegramPackage(installed: Boolean): String? =
    if (installed) "org.telegram.messenger" else null

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

    private var loadedId: Long? = null

    fun load(contactId: Long) {
        // One-shot flags must never survive across entries sharing this ViewModel,
        // otherwise the screen auto-pops right after opening.
        _state.update { it.copy(deleted = false, message = null) }
        if (loadedId == contactId) return
        loadedId = contactId
        viewModelScope.launch {
            val contact = repository.getById(contactId)
            _state.update {
                if (contact == null) it.copy(contact = null, notFound = true)
                else it.copy(contact = contact, notFound = false)
            }
        }
    }

    fun delete() {
        val contact = _state.value.contact ?: return
        viewModelScope.launch {
            if (repository.delete(contact.id)) {
                _state.update { it.copy(deleted = true) }
            } else {
                _state.update {
                    it.copy(message = UiText.Resource(R.string.preview_delete_failed))
                }
            }
        }
    }

    suspend fun makeShareUri(): Uri? = withContext(Dispatchers.IO) {
        val contact = _state.value.contact ?: return@withContext null
        runCatching {
            val file = shareFile(context, "shared_contact.vcf")
            file.writeText(VcfMapper.contactsToVcf(listOf(contact)))
            FileProvider.getUriForFile(context, "com.hcmdz.privnum.fileprovider", file)
        }.getOrNull()
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    fun photoModel(photo: String): Any? = photos.photoModel(photo)
}
