package com.hcmdz.privnum.settings

import com.hcmdz.privnum.data.AutoLockTimeout
import com.hcmdz.privnum.ui.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsLabelsTest {

    @Test
    fun `every timeout maps to a resource label`() {
        val labels = AutoLockTimeout.entries.associateWith { it.label() }
        assertEquals(UiText.Resource(R.string.settings_auto_lock_disabled), labels[AutoLockTimeout.DISABLED])
        assertEquals(UiText.Resource(R.string.settings_auto_lock_immediately), labels[AutoLockTimeout.IMMEDIATELY])
        assertEquals(UiText.Resource(R.string.settings_auto_lock_minute), labels[AutoLockTimeout.MIN_1])
        assertEquals(UiText.Resource(R.string.settings_auto_lock_minutes), labels[AutoLockTimeout.MIN_5])
        assertEquals(UiText.Resource(R.string.settings_auto_lock_hour), labels[AutoLockTimeout.HOUR_1])
        assertEquals(UiText.Resource(R.string.settings_auto_lock_hours), labels[AutoLockTimeout.HOUR_5])
        labels.values.forEach { label ->
            assertTrue(label is UiText.Resource)
            assertTrue((label as UiText.Resource).id != 0)
        }
    }

    @Test
    fun `region options are valid ISO codes`() {
        assertTrue(DEFAULT_REGION_OPTIONS.isNotEmpty())
        DEFAULT_REGION_OPTIONS.forEach { region ->
            assertEquals(2, region.length)
            assertEquals(region, region.uppercase())
        }
    }
}
