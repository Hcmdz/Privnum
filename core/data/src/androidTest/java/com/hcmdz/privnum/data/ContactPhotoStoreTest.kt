package com.hcmdz.privnum.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer

@RunWith(AndroidJUnit4::class)
class ContactPhotoStoreTest {
    private val store = ContactPhotoStore(
        ApplicationProvider.getApplicationContext()
    )

    @Test
    fun saveReadDeleteRoundTrip() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val path = store.savePhoto(bytes, "image/png")
        assertTrue(path.endsWith(".png"))
        assertArrayEquals(bytes, store.readBytes(path))
        store.deletePhoto(path)
        assertNull(store.readBytes(path))
    }

    @Test
    fun importDataUriPersistsToFile() {
        val bytes = byteArrayOf(9, 8, 7, 6)
        val uri = "data:image/jpeg;base64," +
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        val path = store.importDataUri(uri)
        assertNotNull(path)
        assertArrayEquals(bytes, store.readBytes(path!!))
        store.deletePhoto(path)
    }

    @Test
    fun photoModelShapes() {
        assertNull(store.photoModel(""))
        val bytes = byteArrayOf(5, 5, 5)
        val uri = "data:image/png;base64," +
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        assertTrue(store.photoModel(uri) is ByteBuffer)
        val path = store.savePhoto(bytes, "image/jpeg")
        assertTrue(store.photoModel(path) is java.io.File)
        store.deletePhoto(path)
    }

    @Test
    fun extForMimeDefaultsJpeg() {
        assertEquals("png", extForMime("image/png"))
        assertEquals("jpg", extForMime("image/bmp"))
        assertEquals("jpg", extForMime(null))
    }
}
