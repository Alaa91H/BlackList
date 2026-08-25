package com.blacklist.app.domain.notification

/**
 * Pure privacy gate for blocked-call notifications. It is intentionally
 * independent of enforcement: muting notifications can never alter blocking.
 */
object BlockedNotificationGate {
    fun isAllowed(globalEnabled: Boolean, perNumberEnabled: Boolean): Boolean =
        globalEnabled && perNumberEnabled
}
