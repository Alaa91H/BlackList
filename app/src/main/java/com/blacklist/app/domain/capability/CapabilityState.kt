package com.blacklist.app.domain.capability

/**
 * Represents the availability state of a capability.
 */
enum class CapabilityState {
    /** Fully available and functional */
    AVAILABLE,
    /** Partially available - some features may not work */
    DEGRADED,
    /** Blocked due to missing required capability */
    BLOCKED,
    /** Not available on this device/version */
    UNAVAILABLE,
    /** Not supported on this device/version */
    NOT_SUPPORTED,
    /** Requires user action to enable */
    REQUIRES_ACTION,
    /** State could not be determined */
    UNKNOWN
}