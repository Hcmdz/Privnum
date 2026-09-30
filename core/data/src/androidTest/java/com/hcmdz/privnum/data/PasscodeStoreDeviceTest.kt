package com.hcmdz.privnum.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The store keeps no migration path by design: a missing or wiped store reads
 * as unset, and the user re-enrolls. These tests lock that semantics in place
 * so a later change of a default cannot silently flip it.
 */
@RunWith(AndroidJUnit4::class)
class PasscodeStoreDeviceTest {
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        for (name in listOf("passcode_settings", "passcode_store")) {
            context.getSharedPreferences(name, Context.MODE_PRIVATE)
                .edit().clear().commit()
        }
    }

    @Test
    fun freshStoreReadsAsUnset() {
        val store = PasscodeStore(context)
        assertFalse(store.passcodeEnabled)
        assertFalse(store.biometricEnabled)
        assertFalse(store.biometricEnrolled())
    }

    @Test
    fun wipedStoreReadsAsUnset() {
        val store = PasscodeStore(context)
        store.passcodeEnabled = true
        assertTrue(store.passcodeEnabled)
        context.getSharedPreferences("passcode_settings", Context.MODE_PRIVATE)
            .edit().clear().commit()
        assertFalse(PasscodeStore(context).passcodeEnabled)
    }
}
