package com.blacklist.app.domain.permission

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.app.role.RoleManager
import androidx.core.content.ContextCompat
import com.blacklist.app.domain.permission.PermissionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Central permission management - single source of truth for all permission states.
 * All components should use this instead of checking permissions directly.
 */
interface PermissionManager {
    /** Current state of all tracked permissions */
    val permissionStates: StateFlow<Map<String, PermissionState>>

    /** Descriptors for all tracked permissions */
    val descriptors: List<PermissionDescriptor>

    /** Refresh all permission states */
    suspend fun refresh(): Map<String, PermissionState>

    /** Get state of a specific permission */
    fun getState(id: String): PermissionState

    /** Check if a permission is granted */
    fun isGranted(id: String): Boolean

    /** Check if a permission can be requested */
    fun canRequest(id: String): Boolean

    /** Get descriptor for a permission */
    fun getDescriptor(id: String): PermissionDescriptor?

    /** Request a permission (returns true if granted) */
    suspend fun request(id: String, context: Context): Boolean

    /** Open settings for a permission */
    fun openSettings(id: String, context: Context): Boolean

    /** Get permissions that need to be requested */
    fun getMissingRequiredPermissions(): List<PermissionDescriptor>

    /** Get permissions that need to be requested (recommended) */
    fun getMissingRecommendedPermissions(): List<PermissionDescriptor>

    /** Refresh and return capabilities */
    suspend fun refreshCapabilities(): Map<String, PermissionState>
}

/**
 * Default implementation of PermissionManager
 */
class PermissionManagerImpl(
    private val context: Context
) : PermissionManager {

    private val _permissionStates = MutableStateFlow<Map<String, PermissionState>>(emptyMap())
    override val permissionStates: StateFlow<Map<String, PermissionState>> = _permissionStates.asStateFlow()

    override val descriptors: List<PermissionDescriptor> = buildDescriptors()

    private fun buildDescriptors(): List<PermissionDescriptor> {
        return listOf(
            // --- CALLS ---
            PermissionDescriptor(
                id = "android.permission.READ_PHONE_STATE",
                name = "Phone State",
                description = "Read phone state and identity for call identification",
                category = PermissionCategory.CALLS,
                required = true,
                runtime = true,
                specialAccess = false,
                role = false,
                rationale = "Required to identify incoming callers and their state",
                remediation = "Grant in Settings > Apps > BlackList > Permissions"
            ),
            PermissionDescriptor(
                id = "android.permission.READ_CALL_LOG",
                name = "Call Log",
                description = "Read call history for blocking statistics",
                category = PermissionCategory.CALLS,
                required = true,
                runtime = true,
                specialAccess = false,
                role = false,
                rationale = "Needed to read call history for blocked call statistics",
                remediation = "Grant in Settings > Apps > BlackList > Permissions"
            ),
            PermissionDescriptor(
                id = "android.permission.CALL_PHONE",
                name = "Call Phone",
                description = "Initiate phone calls",
                category = PermissionCategory.CALLS,
                required = false,
                runtime = true,
                specialAccess = false,
                role = false,
                rationale = "Used for emergency callback functionality",
                optional = true
            ),
            PermissionDescriptor(
                id = "android.permission.ANSWER_PHONE_CALLS",
                name = "Answer Calls",
                description = "Programmatically answer incoming calls",
                category = PermissionCategory.CALLS,
                required = true,
                runtime = true,
                specialAccess = false,
                role = false,
                minimumSdk = Build.VERSION_CODES.Q,
                rationale = "Required for call screening to reject calls",
                remediation = "Grant in Settings > Apps > BlackList > Permissions"
            ),
            PermissionDescriptor(
                id = "android.permission.READ_CONTACTS",
                name = "Contacts",
                description = "Access contacts to identify known callers",
                category = PermissionCategory.CONTACTS,
                required = false,
                runtime = true,
                specialAccess = false,
                role = false,
                rationale = "Identify known callers for whitelist/unknown detection",
                remediation = "Grant in Settings > Apps > BlackList > Permissions",
                optional = true
            ),
            PermissionDescriptor(
                id = "android.permission.READ_SMS",
                name = "SMS Messages",
                description = "Read SMS messages for spam sender identification",
                category = PermissionCategory.CALLS,
                required = false,
                runtime = true,
                specialAccess = false,
                role = false,
                minimumSdk = Build.VERSION_CODES.P,
                rationale = "Identify spam senders from SMS messages",
                optional = true
            ),

            // --- ROLES ---
            PermissionDescriptor(
                id = "role_call_screening",
                name = "Call Screening",
                description = "Act as default call screening app",
                category = PermissionCategory.ROLES,
                required = true,
                runtime = false,
                specialAccess = false,
                role = true,
                minimumSdk = Build.VERSION_CODES.Q,
                rationale = "Required to screen calls before they ring",
                remediation = "Set BlackList as default call screening app in Settings"
            ),

            // --- NOTIFICATIONS ---
            PermissionDescriptor(
                id = "android.permission.POST_NOTIFICATIONS",
                name = "Notifications",
                description = "Show notifications for blocked calls and security events",
                category = PermissionCategory.NOTIFICATIONS,
                required = true,
                runtime = true,
                specialAccess = false,
                role = false,
                minimumSdk = Build.VERSION_CODES.TIRAMISU,
                rationale = "Show notifications when calls are blocked",
                remediation = "Grant notification permission in system settings"
            ),

            // --- SPECIAL ACCESS ---
            PermissionDescriptor(
                id = "battery_optimization",
                name = "Battery Optimization",
                description = "Exempt from battery optimization for reliable call screening",
                category = PermissionCategory.SPECIAL_ACCESS,
                required = false,
                runtime = false,
                specialAccess = true,
                role = false,
                rationale = "Prevent system from killing call screening service",
                remediation = "Disable in Settings > Apps > BlackList > Battery > Unrestricted",
                optional = true
            ),
            PermissionDescriptor(
                id = "android.permission.MANAGE_EXTERNAL_STORAGE",
                name = "All Files Access",
                description = "Full external storage access for backups",
                category = PermissionCategory.SPECIAL_ACCESS,
                required = false,
                runtime = false,
                specialAccess = true,
                role = false,
                minimumSdk = Build.VERSION_CODES.R,
                rationale = "Full access for backup/restore operations",
                optional = true
            )
        )
    }

    override suspend fun refresh(): Map<String, PermissionState> {
        val states = mutableMapOf<String, PermissionState>()

        for (descriptor in descriptors) {
            if (descriptor.minimumSdk > Build.VERSION.SDK_INT) {
                states[descriptor.id] = PermissionState.NOT_APPLICABLE
                continue
            }
            if (descriptor.maximumSdk != null && Build.VERSION.SDK_INT > descriptor.maximumSdk!!) {
                states[descriptor.id] = PermissionState.NOT_APPLICABLE
                continue
            }

            val state = evaluatePermission(descriptor)
            states[descriptor.id] = state
        }

        _permissionStates.value = states
        return states
    }

    private fun evaluatePermission(descriptor: PermissionDescriptor): PermissionState {
        // Check minimum SDK
        if (descriptor.minimumSdk > Build.VERSION.SDK_INT) {
            return PermissionState.NOT_APPLICABLE
        }
        if (descriptor.maximumSdk != null && Build.VERSION.SDK_INT > descriptor.maximumSdk!!) {
            return PermissionState.NOT_APPLICABLE
        }

        // Check required permissions
        for (perm in descriptor.requiredPermissions) {
            val permState = getState(perm)
            when (permState) {
                PermissionState.GRANTED -> {}
                PermissionState.DENIED_CAN_REQUEST -> return PermissionState.REQUIRES_ACTION
                PermissionState.DENIED_PERMANENT -> return PermissionState.BLOCKED
                PermissionState.NOT_APPLICABLE -> return PermissionState.NOT_APPLICABLE
                else -> return PermissionState.BLOCKED
            }
        }

        // Check required roles
        for (role in descriptor.requiredRoles) {
            val roleState = when {
                role == "role_call_screening" -> checkCallScreeningRole()
                else -> PermissionState.UNKNOWN
            }
            when (roleState) {
                PermissionState.GRANTED -> {}
                PermissionState.DENIED_CAN_REQUEST -> return PermissionState.REQUIRES_ACTION
                PermissionState.DENIED_PERMANENT -> return PermissionState.BLOCKED
                else -> return PermissionState.UNAVAILABLE
            }
        }

        // Check special access
        for (access in descriptor.requiredSpecialAccess) {
            val accessState = checkSpecialAccess(access)
            when (accessState) {
                PermissionState.GRANTED -> {}
                PermissionState.DENIED_CAN_REQUEST -> return PermissionState.REQUIRES_ACTION
                PermissionState.DENIED_PERMANENT -> return PermissionState.BLOCKED
                else -> return PermissionState.UNAVAILABLE
            }
        }

        // Check dependencies
        for (dep in descriptor.dependencies) {
            val depState = _permissionStates.value[dep] ?: PermissionState.UNKNOWN
            when (depState) {
                PermissionState.AVAILABLE -> {}
                PermissionState.DEGRADED -> return PermissionState.DEGRADED
                PermissionState.BLOCKED, PermissionState.UNAVAILABLE -> return PermissionState.BLOCKED
                else -> return PermissionState.BLOCKED
            }
        }

        // Check optional capabilities don't block
        if (descriptor.optional) {
            return PermissionState.AVAILABLE
        }

        return PermissionState.AVAILABLE
    }

    private fun checkCallScreeningRole(): PermissionState {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return PermissionState.NOT_APPLICABLE
        val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
        return try {
            if (roleManager?.isRoleHeld(android.app.role.RoleManager.ROLE_CALL_SCREENING) == true) {
                PermissionState.GRANTED
            } else {
                // Check if role is available (API 30+)
                val available = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        val method = PackageManager::class.java.getMethod("queryIntentRoleHolders", Intent::class.java, Int::class.java)
                        val result = method.invoke(context.packageManager, Intent(android.app.role.RoleManager.ROLE_CALL_SCREENING), 0) as List<*>
                        result.isNotEmpty()
                    } catch (_: Exception) {
                        false
                    }
                } else {
                    // On Android 10 (API 29), queryIntentRoleHolders is not available
                    // Assume role is available if we're on Android 10
                    true
                }
                if (available) PermissionState.DENIED_CAN_REQUEST else PermissionState.NOT_APPLICABLE
            }
        } catch (_: Exception) {
            PermissionState.UNKNOWN
        }
    }

    private fun checkSpecialAccess(access: String): PermissionState {
        return when (access) {
            // Battery optimization
            "battery_optimization" -> {
                val pm = context.getSystemService(PowerManager::class.java)
                val ignoring = pm?.isIgnoringBatteryOptimizations(context.packageName) == true
                if (ignoring) PermissionState.GRANTED else PermissionState.DENIED_CAN_REQUEST
            }
            // Manage external storage (Android 11+)
            "android.permission.MANAGE_EXTERNAL_STORAGE" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val granted = android.os.Environment.isExternalStorageManager()
                    if (granted) PermissionState.GRANTED else PermissionState.DENIED_CAN_REQUEST
                } else PermissionState.NOT_APPLICABLE
            }
            else -> PermissionState.UNKNOWN
        }
    }

    override fun getState(id: String): PermissionState {
        return _permissionStates.value[id] ?: PermissionState.UNKNOWN
    }

    override fun isGranted(id: String): Boolean {
        return getState(id) == PermissionState.GRANTED
    }

    override fun canRequest(id: String): Boolean {
        val state = getState(id)
        return state == PermissionState.DENIED_CAN_REQUEST
    }

    override fun getDescriptor(id: String): PermissionDescriptor? {
        return descriptors.find { it.id == id }
    }

    override suspend fun request(id: String, context: Context): Boolean {
        val descriptor = getDescriptor(id) ?: return false
        // This would need to be called from an Activity/Fragment
        // For now, return current state
        return isGranted(id)
    }

    override fun openSettings(id: String, context: Context): Boolean {
        val descriptor = getDescriptor(id) ?: return false
        return try {
            val intent: Intent = when {
                descriptor.role -> Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                descriptor.specialAccess -> {
                    val action = when {
                        id == "battery_optimization" -> Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                        id == "android.permission.MANAGE_EXTERNAL_STORAGE" -> Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                        else -> Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    }
                    Intent(action)
                }
                else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun getMissingRequiredPermissions(): List<PermissionDescriptor> {
        return descriptors.filter { desc -> desc.required && !isGranted(desc.id) }
    }

    override fun getMissingRecommendedPermissions(): List<PermissionDescriptor> {
        return descriptors.filter { desc -> !desc.required && !desc.optional && !isGranted(desc.id) }
    }

    override suspend fun refreshCapabilities(): Map<String, PermissionState> = refresh()

    companion object {
        fun createDefault(context: Context): PermissionManagerImpl {
            return PermissionManagerImpl(context)
        }
    }
}