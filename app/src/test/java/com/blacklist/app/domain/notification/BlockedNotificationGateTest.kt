package com.blacklist.app.domain.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockedNotificationGateTest {
    @Test
    fun `notification is allowed only when both global and per-number controls are enabled`() {
        assertTrue(BlockedNotificationGate.isAllowed(globalEnabled = true, perNumberEnabled = true))
        assertFalse(BlockedNotificationGate.isAllowed(globalEnabled = false, perNumberEnabled = true))
        assertFalse(BlockedNotificationGate.isAllowed(globalEnabled = true, perNumberEnabled = false))
        assertFalse(BlockedNotificationGate.isAllowed(globalEnabled = false, perNumberEnabled = false))
    }
}
