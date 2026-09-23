package com.hcmdz.privnum.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

fun extForMime(mime: String?): String = when (mime?.lowercase()) {
    "image/png" -> "png"
    "image/gif" -> "gif"
    "image/webp" -> "webp"
    else -> "jpg"
}

/**
 * Contact photos live as files under private storage; [Contact.photo] holds an
 * absolute path, a "data:" URI (VCF imports), or "". Never a remote URL.
 */
@Singleton
class ContactPhotoStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private fun dir(): File = File(context.filesDir, "contact_photos").also { it.mkdirs() }

    fun savePhoto(bytes: ByteArray, mime: String?): String {
        val file = File(dir(), "${UUID.randomUUID()}.${extForMime(mime)}")
        file.writeBytes(bytes)
        return file.absolutePath
    }

    fun readBytes(photo: String): ByteArray? {
        if (photo.isBlank()) return null
        return if (photo.startsWith("data:image/")) {
            runCatching {
                Base64.decode(photo.substringAfter(","), Base64.DEFAULT)
            }.getOrNull()
        } else {
            runCatching {
                val file = File(photo)
                if (file.exists()) file.readBytes() else null
            }.getOrNull()
        }
    }

    fun photoFile(photo: String): File? {
        if (photo.isBlank() || photo.startsWith("data:image/")) return null
        val file = File(photo)
        return if (file.exists()) file else null
    }

    /** Coil model: file when stored locally, buffer for data URIs, null when empty. */
    fun photoModel(photo: String): Any? {
        if (photo.isBlank()) return null
        return photoFile(photo) ?: readBytes(photo)?.let { ByteBuffer.wrap(it) }
    }

    fun photoDataUri(photo: String): String? {
        if (photo.isBlank()) return null
        if (photo.startsWith("data:image/")) return photo
        val bytes = readBytes(photo) ?: return null
        val file = File(photo)
        val mime = when (file.extension.lowercase()) {
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }
        return "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /** Persist a "data:" URI photo to a file; returns the path or null. */
    fun importDataUri(dataUri: String): String? {
        if (!dataUri.startsWith("data:image/")) return null
        val bytes = readBytes(dataUri) ?: return null
        val mime = dataUri.substringAfter("data:").substringBefore(";")
        return savePhoto(bytes, mime)
    }

    fun deletePhoto(photo: String) {
        if (photo.isBlank() || photo.startsWith("data:image/")) return
        runCatching { File(photo).takeIf { it.exists() }?.delete() }
    }

    fun contentTypeOf(uri: Uri): String? =
        runCatching { context.contentResolver.getType(uri) }.getOrNull()
}
