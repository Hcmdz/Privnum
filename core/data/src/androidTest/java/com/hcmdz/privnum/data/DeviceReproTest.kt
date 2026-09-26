package com.hcmdz.privnum.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hcmdz.privnum.data.db.PrivnumDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
    name = name,
    numbers = listOf(PhoneNumberRef(full, national, "FR", primary = true))
)

private fun assertSaved(result: SaveResult) =
    assertTrue(result is SaveResult.Saved)

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
        assertSaved(repository.add(contact()))
        val id = repository.getAll().single().id
        assertSaved(repository.update(id, contact().copy(nickname = "JD")))
        assertEquals("JD", repository.findByNumber("33612345678")?.nickname)
        assertEquals(1, repository.getAll().size)
    }

    @Test
    fun updateWithChangedNumberMovesContact() = runTest {
        assertSaved(repository.add(contact()))
        val id = repository.getAll().single().id
        assertSaved(
            repository.update(id, contact(full = "33698765432", national = "698765432"))
        )
        assertNull(repository.findByNumber("33612345678"))
        assertEquals("Test", repository.findByNumber("33698765432")?.name)
    }

    @Test
    fun updateMissingContactReturnsNotFound() = runTest {
        assertTrue(
            repository.update(
                999999,
                contact(full = "33000000000", national = "000000000")
            ) is SaveResult.NotFound
        )
    }

    @Test
    fun duplicateNumberRefusedNamingOwner() = runTest {
        assertSaved(repository.add(contact(name = "Owner")))
        val other = repository.add(contact(name = "Clone"))
        assertTrue(other is SaveResult.DuplicateNumber)
        assertEquals("Owner", (other as SaveResult.DuplicateNumber).ownerName)
        val ownerId = repository.getAll().single().id
        assertSaved(repository.add(contact(full = "33698765432", national = "698765432", name = "Second")))
        val secondId = repository.getAll().single { it.name == "Second" }.id
        val moved = repository.update(secondId, contact(name = "Second"))
        assertTrue(moved is SaveResult.DuplicateNumber)
        assertEquals("Owner", (moved as SaveResult.DuplicateNumber).ownerName)
        assertEquals("Owner", repository.getById(ownerId)?.name)
    }

    @Test
    fun secondNumberSearchableByDigits() = runTest {
        val twoNumbers = contact().copy(
            numbers = listOf(
                PhoneNumberRef("33612345678", "612345678", "FR", primary = true),
                PhoneNumberRef("1555" + "1234567", "555" + "1234567", "US", primary = false)
            )
        )
        assertSaved(repository.add(twoNumbers))
        assertEquals(1, repository.search("1555").size)
        assertEquals("Test", repository.search("1555").single().name)
        assertEquals(1, repository.search("Test 1555").size)
    }

    @Test
    fun clearAllEmptiesTableAndFts() = runTest {
        assertSaved(repository.add(contact()))
        assertEquals(1, repository.search("Test").size)
        repository.clearAll()
        assertEquals(0, repository.getAll().size)
        assertEquals(0, repository.search("Test").size)
    }

    @Test
    fun deleteRemovesPhotoFile() = runTest {
        val path = photos.savePhoto(byteArrayOf(1, 2, 3), "image/jpeg")
        assertSaved(repository.add(contact().copy(photo = path)))
        val id = repository.getAll().single().id
        assertTrue(repository.delete(id))
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

    @Test
    fun biometricDecryptCipherNullWhenNeverEnrolled() {
        assertFalse(store.biometricEnrolled())
        assertNull(store.biometricCipherForDecrypt())
    }

    @Test
    fun biometricUseWithoutAuthFailsClosed() {
        org.junit.Assume.assumeTrue(
            "Needs API 30+ auth-bound keys",
            android.os.Build.VERSION.SDK_INT >= 30
        )
        // The keystore refuses to create an auth-bound key when no strong
        // biometric is enrolled, so this only runs on a device that has one.
        org.junit.Assume.assumeTrue(
            "Needs an enrolled strong biometric",
            androidx.biometric.BiometricManager
                .from(ApplicationProvider.getApplicationContext())
                .canAuthenticate(
                    androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
                ) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
        )
        // An enroll cipher created but never authed through the prompt must
        // not yield a usable token: doFinal throws UserNotAuthenticatedException.
        val cipher = store.biometricCipherForEnroll()
        assertNotNull(cipher)
        var threw = false
        try {
            cipher?.doFinal(byteArrayOf(7, 7, 7))
        } catch (e: Exception) {
            threw = true
        }
        assertTrue(threw)
        assertFalse(store.verifyBiometricToken(cipher!!))
    }

    @After
    fun teardown() {
        store.clear()
    }
}
