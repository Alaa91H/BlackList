package com.blacklist.app.domain.quicksettings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemporaryBlockTilePolicyTest {

    @Test
    fun `secured locked device requires unlock before temporary override changes`() {
        assertTrue(TemporaryBlockTilePolicy.requiresUnlock(isLocked = true, isSecure = true))
    }

    @Test
    fun `unlocked device does not require another unlock`() {
        assertFalse(TemporaryBlockTilePolicy.requiresUnlock(isLocked = false, isSecure = true))
    }

    @Test
    fun `unsecured locked device does not claim an unavailable secure unlock boundary`() {
        assertFalse(TemporaryBlockTilePolicy.requiresUnlock(isLocked = true, isSecure = false))
    }

    @Test
    fun `unsecured unlocked device remains immediately operable`() {
        assertFalse(TemporaryBlockTilePolicy.requiresUnlock(isLocked = false, isSecure = false))
    }
}
