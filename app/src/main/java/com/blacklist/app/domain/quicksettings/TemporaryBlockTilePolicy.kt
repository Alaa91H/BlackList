package com.blacklist.app.domain.quicksettings

/**
 * Keeps the lock-screen decision for the user-controlled temporary block tile
 * explicit and independently testable. Changing an active call-block override
 * requires an unlock only when the device is both secured and currently locked.
 */
object TemporaryBlockTilePolicy {
    fun requiresUnlock(isLocked: Boolean, isSecure: Boolean): Boolean = isLocked && isSecure
}
