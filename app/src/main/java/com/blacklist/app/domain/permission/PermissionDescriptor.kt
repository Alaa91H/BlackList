package com.blacklist.app.domain.permission

/**
 * Describes a permission, special access, or role requirement.
 */
data class PermissionDescriptor(
    /** Unique identifier */
    val id: String,
    /** Human-readable name */
    val name: String,
    /** Detailed description */
    val description: String,
    /** Category for grouping */
    val category: PermissionCategory,
    /** Whether this is required for core functionality */
    val required: Boolean,
    /** Whether this is a runtime permission */
    val runtime: Boolean,
    /** Whether this is special access (not a standard permission) */
    val specialAccess: Boolean,
    /** Whether this is a role */
    val role: Boolean,
    /** Minimum Android SDK version required */
    val minimumSdk: Int = 1,
    /** Maximum Android SDK version (if deprecated) */
    val maximumSdk: Int? = null,
    /** Dependencies on other permissions */
    val dependencies: List<String> = emptyList(),
    /** Current state */
    val state: PermissionState = PermissionState.UNKNOWN,
    /** User-facing rationale for why this is needed */
    val rationale: String = "",
    /** Remediation steps if not granted */
    val remediation: String = "",
    /** Whether this is optional (won't block core functionality) */
    val optional: Boolean = false,
    /** Minimum SDK where this is available */
    val availableSinceSdk: Int = 1,
    /** Whether this is deprecated */
    val deprecated: Boolean = false,
    /** Replacement if deprecated */
    val replacement: String? = null,
    /** Required permissions for this capability */
    val requiredPermissions: List<String> = emptyList(),
    /** Required roles for this capability */
    val requiredRoles: List<String> = emptyList(),
    /** Required special access for this capability */
    val requiredSpecialAccess: List<String> = emptyList()
)

/**
 * Categories for grouping permissions
 */
enum class PermissionCategory {
    CALLS("Calls"),
    CONTACTS("Contacts"),
    NOTIFICATIONS("Notifications"),
    BACKGROUND("Background Execution"),
    SPECIAL_ACCESS("Special Access"),
    ROLES("Roles"),
    PRIVILEGED("Privileged"),
    SECURITY("Security"),
    OPTIONAL("Optional"),
    MEDIA("Media"),
    LOCATION("Location"),
    STORAGE("Storage");

    private val displayName: String

    constructor(displayName: String) {
        this.displayName = displayName
    }

    override fun toString(): String = displayName
}