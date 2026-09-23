package com.hcmdz.privnum.settings

import com.hcmdz.privnum.data.AutoLockTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsLabelsTest {

    @Test
    fun `every timeout has a friendly label without raw enum name`() {
        val labels = AutoLockTimeout.entries.associateWith { it.label() }
        assertEquals("Disabled", labels[AutoLockTimeout.DISABLED])
        assertEquals("Immediately", labels[AutoLockTimeout.IMMEDIATELY])
        assertEquals("1 minute", labels[AutoLockTimeout.MIN_1])
        assertEquals("5 minutes", labels[AutoLockTimeout.MIN_5])
        assertEquals("1 hour", labels[AutoLockTimeout.HOUR_1])
        assertEquals("5 hours", labels[AutoLockTimeout.HOUR_5])
        labels.values.forEach { label ->
            assertTrue(label.none { it == '_' })
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
