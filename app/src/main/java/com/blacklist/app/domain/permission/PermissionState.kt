package com.blacklist.app.domain.permission

/**
 * Represents the state of a permission or special access.
 */
enum class PermissionState {
    /** Permission/capability is fully available and granted */
    AVAILABLE,
    /** Permission is granted and active */
    GRANTED,
    /** Permission is denied but can be requested */
    DENIED_CAN_REQUEST,
    /** Permission is denied and user selected "Don't ask again" */
    DENIED_PERMANENT,
    /** Permission is not applicable (e.g., not available on this Android version) */
    NOT_APPLICABLE,
    /** Permission is restricted by system policy (e.g., device owner) */
    RESTRICTED,
    /** Capability is degraded but partially available */
    DEGRADED,
    /** Capability is blocked/unavailable */
    BLOCKED,
    /** Capability is not available on this device/version */
    UNAVAILABLE,
    /** Capability requires user action to enable */
    REQUIRES_ACTION,
    /** State could not be determined */
    UNKNOWN
}