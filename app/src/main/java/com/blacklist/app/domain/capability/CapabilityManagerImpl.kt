package com.blacklist.app.domain.capability

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.blacklist.app.data.local.BlackListDatabase
import com.blacklist.app.domain.permission.PermissionManager
import com.blacklist.app.domain.permission.PermissionState
import com.blacklist.app.domain.settings.SettingsNavigator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Default implementation of CapabilityManager
 */
class CapabilityManagerImpl(
    private val context: Context,
    private val permissionManager: com.blacklist.app.domain.permission.PermissionManager,
    private val database: BlackListDatabase
) : CapabilityManager {

    private val _capabilityStates = MutableStateFlow<Map<String, CapabilityState>>(emptyMap())

    private val listeners = mutableListOf<CapabilityListener>()

    override val descriptors: List<CapabilityDescriptor> = buildDescriptors()

    override val capabilityStates: StateFlow<Map<String, CapabilityState>> = _capabilityStates.asStateFlow()

    private fun buildDescriptors(): List<CapabilityDescriptor> {
        return listOf(
            // ===== CORE FIREWALL =====
            CapabilityDescriptor(
                id = "CALL_FIREWALL",
                name = "Call Firewall",
                description = "Core call blocking and filtering functionality",
                category = CapabilityCategory.CALL_FIREWALL,
                required = true,
                requiredPermissions = listOf(
                    "android.permission.READ_PHONE_STATE",
                    "android.permission.READ_CALL_LOG",
                    "android.permission.ANSWER_PHONE_CALLS"
                ),
                requiredRoles = listOf("role_call_screening"),
                minimumSdk = android.os.Build.VERSION_CODES.Q,
                dependencies = listOf("CALL_SCREENING", "STANDARD_ENFORCEMENT"),
                remediation = "Enable Call Screening role and grant required permissions"
            ),

            // ===== SPAM DETECTION =====
            CapabilityDescriptor(
                id = "SPAM_DETECTION",
                name = "Spam Detection",
                description = "Detect and block spam/scam calls using local intelligence",
                category = CapabilityCategory.SPAM_DETECTION,
                required = true,
                requiredPermissions = listOf(
                    "android.permission.READ_PHONE_STATE",
                    "android.permission.READ_CALL_LOG"
                ),
                dependencies = listOf("CALL_FIREWALL"),
                remediation = "Ensure call screening and call log permissions are granted"
            ),

            // ===== SPAM CAMPAIGN DETECTION =====
            CapabilityDescriptor(
                id = "SPAM_CAMPAIGN_DETECTION",
                name = "Spam Campaign Detection",
                description = "Detect coordinated spam campaigns from similar number patterns",
                category = CapabilityCategory.SPAM_DETECTION,
                required = false,
                optional = true,
                requiredPermissions = listOf("android.permission.READ_CALL_LOG"),
                dependencies = listOf("SPAM_DETECTION"),
                remediation = "Grant call log permission for campaign detection"
            ),

            // ===== CALL FLOOD PROTECTION =====
            CapabilityDescriptor(
                id = "CALL_FLOOD_PROTECTION",
                name = "Call Flood Protection",
                description = "Rate limiting for excessive calls from unknown/hidden numbers",
                category = CapabilityCategory.CALL_FILTERING,
                required = false,
                optional = true,
                requiredPermissions = listOf("android.permission.READ_CALL_LOG", "android.permission.READ_PHONE_STATE"),
                dependencies = listOf("CALL_FIREWALL"),
                remediation = "Grant call log and phone state permissions"
            ),

            // ===== CONTACTS INTEGRATION =====
            CapabilityDescriptor(
                id = "CONTACTS_INTEGRATION",
                name = "Contacts Integration",
                description = "Access contacts to identify known callers and whitelist",
                category = CapabilityCategory.CONTACTS,
                required = false,
                optional = true,
                requiredPermissions = listOf("android.permission.READ_CONTACTS"),
                remediation = "Grant contacts permission to identify known callers"
            ),

            // ===== SMS ANALYSIS =====
            CapabilityDescriptor(
                id = "SMS_ANALYSIS",
                name = "SMS Analysis",
                description = "Analyze SMS messages for spam sender identification",
                category = CapabilityCategory.CALLS,
                required = false,
                optional = true,
                requiredPermissions = listOf("android.permission.READ_SMS"),
                minimumSdk = android.os.Build.VERSION_CODES.P,
                remediation = "Grant SMS permission to identify spam senders from messages"
            ),

            // ===== NOTIFICATIONS =====
            CapabilityDescriptor(
                id = "NOTIFICATIONS",
                name = "Notifications",
                description = "Show notifications for blocked calls and security events",
                category = CapabilityCategory.NOTIFICATIONS,
                required = true,
                requiredPermissions = listOf("android.permission.POST_NOTIFICATIONS"),
                minimumSdk = android.os.Build.VERSION_CODES.TIRAMISU,
                remediation = "Grant notification permission in system settings"
            ),

            // ===== SECURITY NOTIFICATIONS =====
            CapabilityDescriptor(
                id = "SECURITY_NOTIFICATIONS",
                name = "Security Alerts",
                description = "High-priority security notifications for blocked threats",
                category = CapabilityCategory.NOTIFICATIONS,
                required = true,
                requiredPermissions = listOf("android.permission.POST_NOTIFICATIONS"),
                dependencies = listOf("NOTIFICATIONS"),
                remediation = "Enable notification permission for security alerts"
            ),

            // ===== CALL SCREENING =====
            CapabilityDescriptor(
                id = "CALL_SCREENING",
                name = "Call Screening",
                description = "Android Call Screening API to filter calls before ringing",
                category = CapabilityCategory.CALL_FIREWALL,
                required = true,
                requiredRoles = listOf("role_call_screening"),
                minimumSdk = android.os.Build.VERSION_CODES.Q,
                remediation = "Set BlackList as default call screening app in Settings"
            ),

            // ===== STANDARD ENFORCEMENT =====
            CapabilityDescriptor(
                id = "STANDARD_ENFORCEMENT",
                name = "Standard Enforcement",
                description = "Android CallScreeningService-based call blocking",
                category = CapabilityCategory.CALL_FIREWALL,
                required = true,
                requiredRoles = listOf("role_call_screening"),
                minimumSdk = android.os.Build.VERSION_CODES.Q,
                remediation = "Enable Call Screening role in Settings"
            ),

            // ===== ROOT ENFORCEMENT =====
            CapabilityDescriptor(
                id = "ROOT_ENFORCEMENT",
                name = "Root Enforcement",
                description = "Privileged call blocking via root access",
                category = CapabilityCategory.PRIVILEGED_BACKENDS,
                required = false,
                optional = true,
                requiredSpecialAccess = listOf("root"),
                remediation = "Install Magisk and grant root access"
            ),

            // ===== SHIZUKU =====
            CapabilityDescriptor(
                id = "SHIZUKU_ENFORCEMENT",
                name = "Shizuku Enforcement",
                description = "Privileged call blocking via Shizuku",
                category = CapabilityCategory.PRIVILEGED_BACKENDS,
                required = false,
                optional = true,
                requiredSpecialAccess = listOf("shizuku"),
                remediation = "Install Shizuku and grant permission"
            ),

            // ===== BACKGROUND EXECUTION =====
            CapabilityDescriptor(
                id = "BACKGROUND_EXECUTION",
                name = "Background Execution",
                description = "Run call screening service reliably in background",
                category = CapabilityCategory.SYSTEM_ACCESS,
                required = true,
                requiredSpecialAccess = listOf("battery_optimization"),
                remediation = "Disable battery optimization for BlackList"
            ),

            // ===== BATTERY OPTIMIZATION =====
            CapabilityDescriptor(
                id = "BATTERY_OPTIMIZATION",
                name = "Battery Optimization",
                description = "Exempt from battery optimization for reliable screening",
                category = CapabilityCategory.SPECIAL_ACCESS,
                required = false,
                optional = true,
                requiredSpecialAccess = listOf("battery_optimization"),
                remediation = "Disable battery optimization in Settings > Apps > BlackList > Battery"
            ),

            // ===== DATABASE =====
            CapabilityDescriptor(
                id = "DATABASE",
                name = "Database Access",
                description = "Room database for rules, logs, and reputation",
                category = CapabilityCategory.SYSTEM_ACCESS,
                required = true,
                remediation = "Database should always be available"
            ),

            // ===== CONTACTS =====
            CapabilityDescriptor(
                id = "CONTACTS",
                name = "Contacts Access",
                description = "Access contacts for caller identification",
                category = CapabilityCategory.CONTACTS,
                required = false,
                optional = true,
                requiredPermissions = listOf("android.permission.READ_CONTACTS"),
                remediation = "Grant contacts permission in Settings"
            ),

            // ===== CALL LOG =====
            CapabilityDescriptor(
                id = "CALL_LOG",
                name = "Call Log Access",
                description = "Read call log for statistics and reputation",
                category = CapabilityCategory.CALLS,
                required = true,
                requiredPermissions = listOf("android.permission.READ_CALL_LOG"),
                remediation = "Grant call log permission in Settings"
            ),

            // ===== SMS ACCESS =====
            CapabilityDescriptor(
                id = "SMS_ACCESS",
                name = "SMS Access",
                description = "Read SMS for spam sender identification",
                category = CapabilityCategory.CALLS,
                required = false,
                optional = true,
                requiredPermissions = listOf("android.permission.READ_SMS"),
                minimumSdk = android.os.Build.VERSION_CODES.P,
                remediation = "Grant SMS permission for spam sender identification"
            ),

            // ===== ROOT BACKEND =====
            CapabilityDescriptor(
                id = "ROOT_AVAILABLE",
                name = "Root Access",
                description = "Root access available on device",
                category = CapabilityCategory.PRIVILEGED_BACKENDS,
                required = false,
                optional = true,
                requiredSpecialAccess = listOf("root"),
                remediation = "Install Magisk or similar root solution"
            ),

            CapabilityDescriptor(
                id = "ROOT_AUTHORIZED",
                name = "Root Authorization",
                description = "Root access granted to BlackList",
                category = CapabilityCategory.PRIVILEGED_BACKENDS,
                required = false,
                optional = true,
                dependencies = listOf("ROOT_AVAILABLE"),
                requiredSpecialAccess = listOf("root"),
                remediation = "Grant root permission to BlackList when prompted"
            ),

            CapabilityDescriptor(
                id = "ROOT_BACKEND_HEALTHY",
                name = "Root Backend Healthy",
                description = "Root backend can execute commands",
                category = CapabilityCategory.PRIVILEGED_BACKENDS,
                required = false,
                optional = true,
                dependencies = listOf("ROOT_AVAILABLE", "ROOT_AUTHORIZED"),
                requiredSpecialAccess = listOf("root"),
                remediation = "Ensure Magisk is working and grant root to BlackList"
            ),

            CapabilityDescriptor(
                id = "ROOT_ENFORCEMENT_AVAILABLE",
                name = "Root Enforcement Available",
                description = "Root backend can enforce call blocks",
                category = CapabilityCategory.PRIVILEGED_BACKENDS,
                required = false,
                optional = true,
                dependencies = listOf("ROOT_BACKEND_HEALTHY"),
                requiredSpecialAccess = listOf("root"),
                remediation = "Ensure root backend is properly configured"
            ),

            // ===== SHIZUKU =====
            CapabilityDescriptor(
                id = "SHIZUKU_AVAILABLE",
                name = "Shizuku Available",
                description = "Shizuku manager installed and running",
                category = CapabilityCategory.PRIVILEGED_BACKENDS,
                required = false,
                optional = true,
                requiredSpecialAccess = listOf("shizuku"),
                remediation = "Install Shizuku from GitHub/Play Store"
            ),

            CapabilityDescriptor(
                id = "SHIZUKU_AUTHORIZED",
                name = "Shizuku Authorized",
                description = "Shizuku permission granted to BlackList",
                category = CapabilityCategory.PRIVILEGED_BACKENDS,
                required = false,
                optional = true,
                dependencies = listOf("SHIZUKU_AVAILABLE"),
                requiredSpecialAccess = listOf("shizuku"),
                remediation = "Grant Shizuku permission to BlackList"
            ),

            CapabilityDescriptor(
                id = "SHIZUKU_BACKEND_HEALTHY",
                name = "Shizuku Backend Healthy",
                description = "Shizuku backend can execute commands",
                category = CapabilityCategory.PRIVILEGED_BACKENDS,
                required = false,
                optional = true,
                dependencies = listOf("SHIZUKU_AVAILABLE", "SHIZUKU_AUTHORIZED"),
                requiredSpecialAccess = listOf("shizuku"),
                remediation = "Ensure Shizuku is running and BlackList has permission"
            ),

            // ===== DATABASE =====
            CapabilityDescriptor(
                id = "DATABASE",
                name = "Database Access",
                description = "Room database for rules, logs, and reputation",
                category = CapabilityCategory.SYSTEM_ACCESS,
                required = true,
                remediation = "Database should always be available"
            ),

            // ===== NOTIFICATIONS =====
            CapabilityDescriptor(
                id = "NOTIFICATIONS",
                name = "Notifications",
                description = "Show notifications for blocked calls and security events",
                category = CapabilityCategory.NOTIFICATIONS,
                required = true,
                requiredPermissions = listOf("android.permission.POST_NOTIFICATIONS"),
                minimumSdk = android.os.Build.VERSION_CODES.TIRAMISU,
                remediation = "Grant notification permission in system settings"
            ),

            CapabilityDescriptor(
                id = "SECURITY_NOTIFICATIONS",
                name = "Security Alerts",
                description = "High-priority security notifications for blocked threats",
                category = CapabilityCategory.NOTIFICATIONS,
                required = true,
                requiredPermissions = listOf("android.permission.POST_NOTIFICATIONS"),
                dependencies = listOf("NOTIFICATIONS"),
                remediation = "Enable notification permission for security alerts"
            ),

            // ===== SECURITY =====
            CapabilityDescriptor(
                id = "SECURITY_EVENTS",
                name = "Security Events",
                description = "Security event logging and monitoring",
                category = CapabilityCategory.SECURITY,
                required = true,
                dependencies = listOf("DATABASE"),
                remediation = "Database must be available"
            ),

            // ===== BACKGROUND EXECUTION =====
            CapabilityDescriptor(
                id = "BACKGROUND_EXECUTION",
                name = "Background Execution",
                description = "Run call screening service reliably in background",
                category = CapabilityCategory.SYSTEM_ACCESS,
                required = true,
                requiredSpecialAccess = listOf("battery_optimization"),
                remediation = "Disable battery optimization for BlackList"
            ),

            // ===== BATTERY OPTIMIZATION =====
            CapabilityDescriptor(
                id = "BATTERY_OPTIMIZATION",
                name = "Battery Optimization",
                description = "Exempt from battery optimization for reliable screening",
                category = CapabilityCategory.SPECIAL_ACCESS,
                required = false,
                optional = true,
                requiredSpecialAccess = listOf("battery_optimization"),
                remediation = "Disable battery optimization in Settings > Apps > BlackList > Battery"
            )
        )
    }

    override suspend fun refresh(): Map<String, CapabilityState> {
        val states = mutableMapOf<String, CapabilityState>()

        // First check permissions
        permissionManager.refresh()

        for (descriptor in descriptors) {
            if (descriptor.minimumSdk > Build.VERSION.SDK_INT) {
                states[descriptor.id] = CapabilityState.NOT_SUPPORTED
                continue
            }
            if (descriptor.maximumSdk != null && Build.VERSION.SDK_INT > descriptor.maximumSdk!!) {
                states[descriptor.id] = CapabilityState.NOT_SUPPORTED
                continue
            }

            val state = evaluateCapability(descriptor)
            states[descriptor.id] = state

            val oldState = _capabilityStates.value[descriptor.id]
            if (oldState != state) {
                listeners.forEach { it.onCapabilityChanged(descriptor.id, oldState ?: CapabilityState.UNKNOWN, state) }
            }
        }

        _capabilityStates.value = states
        return states
    }

    private fun evaluateCapability(descriptor: CapabilityDescriptor): CapabilityState {
        // Check minimum SDK
        if (descriptor.minimumSdk > Build.VERSION.SDK_INT) {
            return CapabilityState.NOT_SUPPORTED
        }
        if (descriptor.maximumSdk != null && Build.VERSION.SDK_INT > descriptor.maximumSdk!!) {
            return CapabilityState.NOT_SUPPORTED
        }

        // Check required permissions
        for (perm in descriptor.requiredPermissions) {
            val permState = permissionManager.getState(perm)
            when (permState) {
                PermissionState.GRANTED -> {}
                PermissionState.DENIED_CAN_REQUEST -> return CapabilityState.REQUIRES_ACTION
                PermissionState.DENIED_PERMANENT -> return CapabilityState.BLOCKED
                PermissionState.NOT_APPLICABLE -> return CapabilityState.NOT_SUPPORTED
                else -> return CapabilityState.BLOCKED
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
                PermissionState.DENIED_CAN_REQUEST -> return CapabilityState.REQUIRES_ACTION
                PermissionState.DENIED_PERMANENT -> return CapabilityState.BLOCKED
                else -> return CapabilityState.UNAVAILABLE
            }
        }

        // Check special access
        for (access in descriptor.requiredSpecialAccess) {
            val accessState = checkSpecialAccess(access)
            when (accessState) {
                PermissionState.GRANTED -> {}
                PermissionState.DENIED_CAN_REQUEST -> return CapabilityState.REQUIRES_ACTION
                PermissionState.DENIED_PERMANENT -> return CapabilityState.BLOCKED
                else -> return CapabilityState.UNAVAILABLE
            }
        }

        // Check dependencies
        for (dep in descriptor.dependencies) {
            val depState = _capabilityStates.value[dep] ?: CapabilityState.UNKNOWN
            when (depState) {
                CapabilityState.AVAILABLE -> {}
                CapabilityState.DEGRADED -> return CapabilityState.DEGRADED
                CapabilityState.BLOCKED, CapabilityState.UNAVAILABLE -> return CapabilityState.BLOCKED
                else -> return CapabilityState.BLOCKED
            }
        }

        // Check optional capabilities don't block
        if (descriptor.optional) {
            return CapabilityState.AVAILABLE
        }

        return CapabilityState.AVAILABLE
    }

    private fun checkCallScreeningRole(): PermissionState {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return PermissionState.NOT_APPLICABLE
        val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
        return try {
            if (roleManager?.isRoleHeld(android.app.role.RoleManager.ROLE_CALL_SCREENING) == true) {
                PermissionState.GRANTED
            } else {
                // queryIntentRoleHolders is not public API - use reflection (API 30+)
                val available = try {
                    val method = android.content.pm.PackageManager::class.java.getMethod(
                        "queryIntentRoleHolders",
                        android.content.Intent::class.java,
                        Int::class.javaPrimitiveType
                    )
                    val result = method.invoke(
                        context.packageManager,
                        android.content.Intent(android.app.role.RoleManager.ROLE_CALL_SCREENING),
                        0
                    ) as List<*>
                    result.isNotEmpty()
                } catch (_: Exception) {
                    false
                }
                if (available) PermissionState.DENIED_CAN_REQUEST else PermissionState.NOT_APPLICABLE
            }
        } catch (_: Exception) {
            PermissionState.UNKNOWN
        }
    }

    private fun checkSpecialAccess(access: String): PermissionState {
        return when (access) {
            "battery_optimization" -> {
                val pm = context.getSystemService(PowerManager::class.java)
                val ignoring = pm?.isIgnoringBatteryOptimizations(context.packageName) == true
                if (ignoring) PermissionState.GRANTED else PermissionState.DENIED_CAN_REQUEST
            }
            "root" -> checkRootAccess()
            "shizuku" -> checkShizukuAccess()
            else -> PermissionState.UNKNOWN
        }
    }

    private fun checkRootAccess(): PermissionState {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val exit = process.waitFor()
            if (exit == 0) PermissionState.GRANTED else PermissionState.DENIED_CAN_REQUEST
        } catch (_: Exception) {
            PermissionState.UNAVAILABLE
        }
    }

    private fun checkShizukuAccess(): PermissionState {
        return try {
            val clazz = Class.forName("rikka.shizuku.Shizuku")
            val method = clazz.getMethod("pingBinder")
            val result = method.invoke(null) as Boolean
            if (result) PermissionState.GRANTED else PermissionState.DENIED_CAN_REQUEST
        } catch (_: Exception) {
            PermissionState.UNAVAILABLE
        }
    }

    override fun getState(id: String): CapabilityState {
        return _capabilityStates.value[id] ?: CapabilityState.UNKNOWN
    }

    override fun isAvailable(id: String): Boolean = getState(id) == CapabilityState.AVAILABLE

    override fun isDegraded(id: String): Boolean = getState(id) == CapabilityState.DEGRADED

    override fun isBlocked(id: String): Boolean {
        val state = getState(id)
        return state == CapabilityState.BLOCKED || state == CapabilityState.UNAVAILABLE || state == CapabilityState.NOT_SUPPORTED
    }

    override fun getDescriptor(id: String): CapabilityDescriptor? {
        return descriptors.find { it.id == id }
    }

    override fun getCapabilitiesByCategory(category: CapabilityCategory): List<CapabilityDescriptor> {
        return descriptors.filter { it.category == category }
    }

    override fun getHealthPercentage(): Int {
        val required = descriptors.filter { it.required && !it.optional }
        if (required.isEmpty()) return 100
        val available = required.count { isAvailable(it.id) }
        return (available * 100 / required.size)
    }

    override fun getProblematicCapabilities(): List<CapabilityDescriptor> {
        return descriptors.filter {
            val state = getState(it.id)
            it.required && (state == CapabilityState.BLOCKED || state == CapabilityState.UNAVAILABLE || state == CapabilityState.REQUIRES_ACTION)
        }
    }

    override suspend fun refreshCapabilities(): Map<String, CapabilityState> = refresh()

    override fun addListener(listener: CapabilityListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: CapabilityListener) {
        listeners.remove(listener)
    }

    override fun openSettings(capabilityId: String, context: Context): Boolean {
        val navigator = SettingsNavigator(context)
        return navigator.openBestSettingsFor(capabilityId)
    }

    companion object {
        fun create(context: Context, permissionManager: com.blacklist.app.domain.permission.PermissionManager, database: BlackListDatabase): CapabilityManagerImpl {
            return CapabilityManagerImpl(context, permissionManager, database)
        }
    }
}