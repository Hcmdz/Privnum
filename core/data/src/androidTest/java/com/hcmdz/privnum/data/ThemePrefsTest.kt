package com.hcmdz.privnum.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemePrefsTest {
    private val store = SettingsStore(
        ApplicationProvider.getApplicationContext()
    )

    @Test
    fun themeDefaults() = runTest {
        store.setDynamicColor(true)
        store.setAmoledBlack(false)
        assertEquals(true, store.dynamicColor.first())
        assertEquals(false, store.amoledBlack.first())
    }

    @Test
    fun themePrefsPersist() = runTest {
        store.setDynamicColor(false)
        store.setAmoledBlack(true)
        assertEquals(false, store.dynamicColor.first())
        assertEquals(true, store.amoledBlack.first())
        store.setDynamicColor(true)
        store.setAmoledBlack(false)
    }
}
