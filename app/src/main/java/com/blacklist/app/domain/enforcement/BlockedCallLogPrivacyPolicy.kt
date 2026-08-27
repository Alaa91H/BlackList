package com.blacklist.app.domain.enforcement

import com.blacklist.app.data.local.entity.AppSettingsEntity
import com.blacklist.app.domain.model.Decision

/**
 * Defines the boundary between Android's shared call history and BlackList's
 * private local history. The preference affects only calls BlackList blocks;
 * allowed and silenced calls retain their normal system history.
 */
object BlockedCallLogPrivacyPolicy {
    fun skipSystemCallLog(
        decision: Decision,
        settings: AppSettingsEntity?
    ): Boolean = decision == Decision.BLOCK && settings?.hideBlockedCallsFromSystemLog == true
}
