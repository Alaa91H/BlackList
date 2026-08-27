package com.blacklist.app.domain.enforcement

import com.blacklist.app.data.local.entity.AppSettingsEntity
import com.blacklist.app.domain.model.Decision
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockedCallLogPrivacyPolicyTest {

    @Test
    fun `private history hides blocked calls from Android call log`() {
        val result = BlockedCallLogPrivacyPolicy.skipSystemCallLog(
            Decision.BLOCK,
            AppSettingsEntity(hideBlockedCallsFromSystemLog = true)
        )

        assertTrue(result)
    }

    @Test
    fun `private history does not hide allowed or silenced calls`() {
        val settings = AppSettingsEntity(hideBlockedCallsFromSystemLog = true)

        assertFalse(BlockedCallLogPrivacyPolicy.skipSystemCallLog(Decision.ALLOW, settings))
        assertFalse(BlockedCallLogPrivacyPolicy.skipSystemCallLog(Decision.SILENCE, settings))
    }

    @Test
    fun `default setting preserves Android call log visibility`() {
        assertFalse(
            BlockedCallLogPrivacyPolicy.skipSystemCallLog(
                Decision.BLOCK,
                AppSettingsEntity()
            )
        )
    }
}
