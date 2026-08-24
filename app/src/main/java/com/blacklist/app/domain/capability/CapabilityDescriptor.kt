package com.blacklist.app.domain.capability

import com.blacklist.app.domain.permission.PermissionDescriptor
import com.blacklist.app.domain.permission.PermissionState

/**
 * Describes a capability and its requirements.
 */
data class CapabilityDescriptor(
    /** Unique identifier */
    val id: String,
    /** Human-readable name */
    val name: String,
    /** Detailed description */
    val description: String,
    /** Category for grouping */
    val category: CapabilityCategory,
    /** Whether this capability is required for core functionality */
    val required: Boolean,
    /** Required permissions */
    val requiredPermissions: List<String> = emptyList(),
    /** Required roles */
    val requiredRoles: List<String> = emptyList(),
    /** Required special access */
    val requiredSpecialAccess: List<String> = emptyList(),
    /** Minimum Android SDK version */
    val minimumSdk: Int = 1,
    /** Maximum Android SDK version */
    val maximumSdk: Int? = null,
    /** Dependent capabilities */
    val dependencies: List<String> = emptyList(),
    /** Current state */
    val state: CapabilityState = CapabilityState.UNKNOWN,
    /** Human-readable status message */
    val statusMessage: String = "",
    /** Remediation steps if not available */
    val remediation: String = "",
    /** Whether this capability is optional */
    val optional: Boolean = false,
    /** Whether this capability is deprecated */
    val deprecated: Boolean = false,
    /** Replacement if deprecated */
    val replacement: String? = null
)

/**
 * Categories for grouping capabilities
 */
enum class CapabilityCategory(
    val displayName: String
) {
    CALL_FIREWALL("Call Firewall"),
    CALLS("Calls"),
    CONTACTS("Contacts"),
    SPAM_DETECTION("Spam Detection"),
    CALL_FILTERING("Call Filtering"),
    NOTIFICATIONS("Notifications"),
    PRIVILEGED_BACKENDS("Privileged Backends"),
    SYSTEM_ACCESS("System Access"),
    SPECIAL_ACCESS("Special Access"),
    DIAGNOSTICS("Diagnostics"),
    AUTOMATION("Automation"),
    SECURITY("Security"),
    PRIVACY("Privacy")
}