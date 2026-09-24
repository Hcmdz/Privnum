package com.hcmdz.privnum.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hcmdz.privnum.data.db.PrivnumDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private fun contact(
    full: String = "33612345678",
    national: String = "612345678",
    name: String = "Test"
) = Contact(
    fullPhoneNumber = full,
    phoneNumber = national,
    countryCode = "FR",
    name = name
)

@RunWith(AndroidJUnit4::class)
class ContactRepositoryDeviceTest {
    private lateinit var repository: ContactRepository
    private lateinit var photos: ContactPhotoStore

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.deleteDatabase("privnum.db")
        photos = ContactPhotoStore(context)
        repository = ContactRepository(context, photos)
    }

    @Test
    fun updateWithUnchangedNumberKeepsSingleRow() = runTest {
        assertTrue(repository.add(contact()))
        assertTrue(repository.update("33612345678", contact().copy(nickname = "JD")))
        assertEquals("JD", repository.getByFullNumber("33612345678")?.nickname)
        assertEquals(1, repository.getAll().size)
    }

    @Test
    fun updateWithChangedNumberMovesContact() = runTest {
        assertTrue(repository.add(contact()))
        assertTrue(repository.update("33612345678", contact(full = "33698765432", national = "698765432")))
        assertNull(repository.getByFullNumber("33612345678"))
        assertEquals("Test", repository.getByFullNumber("33698765432")?.name)
    }

    @Test
    fun updateMissingContactReturnsFalse() = runTest {
        assertFalse(repository.update("33000000000", contact(full = "33000000000", national = "000000000")))
    }

    @Test
    fun clearAllEmptiesTableAndFts() = runTest {
        assertTrue(repository.add(contact()))
        assertEquals(1, repository.search("Test").size)
        repository.clearAll()
        assertEquals(0, repository.getAll().size)
        assertEquals(0, repository.search("Test").size)
    }

    @Test
    fun deleteRemovesPhotoFile() = runTest {
        val path = photos.savePhoto(byteArrayOf(1, 2, 3), "image/jpeg")
        assertTrue(repository.add(contact().copy(photo = path)))
        assertTrue(repository.delete("33612345678"))
        assertNull(photos.readBytes(path))
    }

    @After
    fun teardown() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.deleteDatabase("privnum.db")
    }
}

@RunWith(AndroidJUnit4::class)
class PasscodeLockoutDeviceTest {
    private lateinit var store: PasscodeStore

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        store = PasscodeStore(context)
        store.clear()
    }

    @Test
    fun correctPinAcceptedAfterFourFailures() {
        store.setPin("1111")
        repeat(4) { assertFalse(store.verifyPin("0000")) }
        assertTrue(store.verifyPin("1111"))
    }

    @Test
    fun correctPinRejectedDuringLockoutAfterFiveFailures() {
        store.setPin("1111")
        repeat(5) { assertFalse(store.verifyPin("0000")) }
        assertTrue(store.isLockedOut())
        assertFalse(store.verifyPin("1111"))
    }

    @Test
    fun fifthFailureLocksOut() {
        store.setPin("1111")
        repeat(4) {
            assertFalse(store.verifyPin("0000"))
            assertFalse(store.isLockedOut())
        }
        assertFalse(store.verifyPin("0000"))
        assertTrue(store.isLockedOut())
        assertFalse(store.verifyPin("1111"))
    }

    @Test
    fun setPinResetsBiometric() {
        store.biometricEnabled = true
        store.setPin("1111")
        assertFalse(store.biometricEnabled)
    }

    @Test
    fun forceLockedTriggersLockUntilNextUnlock() {
        store.setPin("1111")
        store.passcodeEnabled = true
        store.autoLockTimeout = AutoLockTimeout.DISABLED
        assertFalse(store.shouldLock())
        store.lockNow()
        assertTrue(store.shouldLock())
        assertTrue(store.verifyPin("1111"))
        assertFalse(store.shouldLock())
    }

    @After
    fun teardown() {
        store.clear()
    }
}
