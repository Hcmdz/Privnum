package com.hcmdz.privnum.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
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

    /** Gallery/camera pick → oriented square JPEG capped at 512px. */
    fun savePickedPhoto(bytes: ByteArray): String =
        savePhoto(processPicked(bytes), "image/jpeg")

    fun processPicked(bytes: ByteArray, maxSize: Int = 512): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return bytes
        var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
        bitmap = bitmap.oriented(bytes)
        val side = minOf(bitmap.width, bitmap.height)
        val cropped = Bitmap.createBitmap(
            bitmap,
            (bitmap.width - side) / 2,
            (bitmap.height - side) / 2,
            side,
            side
        )
        val scaled = if (side > maxSize) {
            Bitmap.createScaledBitmap(cropped, maxSize, maxSize, true)
        } else {
            cropped
        }
        val out = ByteArrayOutputStream()
        return if (scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)) {
            out.toByteArray()
        } else {
            bytes
        }
    }

    private fun Bitmap.oriented(bytes: ByteArray): Bitmap {
        val orientation = runCatching {
            ExifInterface(bytes.inputStream()).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return this
        }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
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

    fun deleteAllPhotos() {
        runCatching { dir().listFiles()?.forEach { it.delete() } }
    }

    fun contentTypeOf(uri: Uri): String? =
        runCatching { context.contentResolver.getType(uri) }.getOrNull()
}
