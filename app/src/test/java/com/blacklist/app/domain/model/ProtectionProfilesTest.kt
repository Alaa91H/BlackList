package com.blacklist.app.domain.model

import com.blacklist.app.data.local.entity.AppSettingsEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectionProfilesTest {
    @Test
    fun `normal profile preserves a conservative policy`() {
        val settings = ProtectionProfiles.byId(ProtectionProfiles.NORMAL)!!.applyTo(AppSettingsEntity())

        assertEquals(ProtectionProfiles.NORMAL, settings.activeProfileId)
        assertFalse(settings.blockUnknown)
        assertTrue(settings.blockPrivate)
        assertFalse(settings.blockAllExceptWhitelist)
    }

    @Test
    fun `focus profile blocks unknown and private callers without enabling whitelist only mode`() {
        val settings = ProtectionProfiles.byId(ProtectionProfiles.FOCUS)!!.applyTo(AppSettingsEntity())

        assertEquals(ProtectionProfiles.FOCUS, settings.activeProfileId)
        assertTrue(settings.blockUnknown)
        assertTrue(settings.blockPrivate)
        assertFalse(settings.blockAllExceptWhitelist)
    }

    @Test
    fun `whitelist only profile enables the strict local policy`() {
        val settings = ProtectionProfiles.byId(ProtectionProfiles.WHITELIST_ONLY)!!.applyTo(AppSettingsEntity())

        assertEquals(ProtectionProfiles.WHITELIST_ONLY, settings.activeProfileId)
        assertTrue(settings.blockAllExceptWhitelist)
    }

    @Test
    fun `preset resets optional quiet screening preferences`() {
        val customQuietSettings = AppSettingsEntity(
            blockUnknown = true,
            silenceUnknown = true,
            blockPrivate = true,
            silencePrivate = true
        )

        val applied = ProtectionProfiles.byId(ProtectionProfiles.FOCUS)!!.applyTo(customQuietSettings)

        assertTrue(applied.blockUnknown)
        assertTrue(applied.blockPrivate)
        assertFalse(applied.silenceUnknown)
        assertFalse(applied.silencePrivate)
        assertEquals(ProtectionProfiles.FOCUS, applied.activeProfileId)
    }
}
