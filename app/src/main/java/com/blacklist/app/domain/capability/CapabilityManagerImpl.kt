package com.blacklist.app.domain.capability

import android.content.Context
import android.os.Build
import com.blacklist.app.data.local.BlackListDatabase
import com.blacklist.app.domain.permission.PermissionManager
import com.blacklist.app.domain.permission.PermissionState
import com.blacklist.app.domain.settings.SettingsNavigator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Health model for the local-only protection stack.
 *
 * The sole system prerequisite for screening is ROLE_CALL_SCREENING. Contact
 * matching, notifications and battery exemptions are reported as optional
 * enhancements and never lower core protection health to a blocked state.
 */
class CapabilityManagerImpl(
    private val context: Context,
    private val permissionManager: PermissionManager,
    @Suppress("unused") private val database: BlackListDatabase
) : CapabilityManager {

    private val _capabilityStates = MutableStateFlow<Map<String, CapabilityState>>(emptyMap())
    private val listeners = mutableListOf<CapabilityListener>()

    override val descriptors: List<CapabilityDescriptor> = listOf(
        CapabilityDescriptor(
            id = CALL_SCREENING,
            name = "Call screening role",
            description = "Android system role that permits filtering incoming calls before they ring.",
            category = CapabilityCategory.CALL_FIREWALL,
            required = true,
            requiredRoles = listOf(ROLE_CALL_SCREENING),
            minimumSdk = Build.VERSION_CODES.Q,
            remediation = "Choose BlackList as the call screening app in Android settings."
        ),
        CapabilityDescriptor(
            id = CALL_FIREWALL,
            name = "Local call firewall",
            description = "Evaluates local rules, lists, schedules and safeguards before the phone rings.",
            category = CapabilityCategory.CALL_FIREWALL,
            required = true,
            dependencies = listOf(CALL_SCREENING),
            remediation = "Restore the call screening role, then recheck protection health."
        ),
        CapabilityDescriptor(
            id = LOCAL_SPAM_SIGNALS,
            name = "Local spam signals",
            description = "Uses only device-local rules, reputation and in-memory behavior signals.",
            category = CapabilityCategory.SPAM_DETECTION,
            required = false,
            optional = true,
            dependencies = listOf(CALL_FIREWALL),
            remediation = "Enable the call screening role to use local spam signals."
        ),
        CapabilityDescriptor(
            id = CONTACTS_INTEGRATION,
            name = "Contacts matching",
            description = "Optionally identifies saved callers for the unsaved-number rule.",
            category = CapabilityCategory.CONTACTS,
            required = false,
            optional = true,
            requiredPermissions = listOf(android.Manifest.permission.READ_CONTACTS),
            remediation = "Grant Contacts permission only if you use the unsaved-number rule."
        ),
        CapabilityDescriptor(
            id = NOTIFICATIONS,
            name = "Private notification summaries",
            description = "Optionally shows private, rate-limited blocked-call summaries.",
            category = CapabilityCategory.NOTIFICATIONS,
            required = false,
            optional = true,
            requiredPermissions = listOf(android.Manifest.permission.POST_NOTIFICATIONS),
            minimumSdk = Build.VERSION_CODES.TIRAMISU,
            remediation = "Grant Notifications permission if you want summaries; blocking already works without it."
        ),
        CapabilityDescriptor(
            id = BATTERY_OPTIMIZATION,
            name = "Battery optimization exemption",
            description = "Optional device-specific reliability setting.",
            category = CapabilityCategory.SPECIAL_ACCESS,
            required = false,
            optional = true,
            requiredSpecialAccess = listOf(BATTERY_OPTIMIZATION_ACCESS),
            remediation = "Only change this setting if Android reports a reliability problem."
        ),
        CapabilityDescriptor(
            id = DATABASE,
            name = "Local policy database",
            description = "Stores rules and local call history exclusively on this device.",
            category = CapabilityCategory.SYSTEM_ACCESS,
            required = true,
            remediation = "Restart the app or restore a manual encrypted backup if local storage is unavailable."
        ),
        CapabilityDescriptor(
            id = SECURITY_EVENTS,
            name = "Local protection health",
            description = "Records health events locally after a fallback or a policy failure.",
            category = CapabilityCategory.SECURITY,
            required = false,
            optional = true,
            dependencies = listOf(DATABASE),
            remediation = "Check device storage and restart the app."
        )
    )

    override val capabilityStates: StateFlow<Map<String, CapabilityState>> = _capabilityStates.asStateFlow()

    override suspend fun refresh(): Map<String, CapabilityState> {
        permissionManager.refresh()
        val states = mutableMapOf<String, CapabilityState>()
        descriptors.forEach { descriptor ->
            val state = evaluateCapability(descriptor, states)
            val oldState = _capabilityStates.value[descriptor.id]
            states[descriptor.id] = state
            if (oldState != state) listeners.forEach { it.onCapabilityChanged(descriptor.id, oldState ?: CapabilityState.UNKNOWN, state) }
        }
        _capabilityStates.value = states
        return states
    }

    private fun evaluateCapability(
        descriptor: CapabilityDescriptor,
        currentStates: Map<String, CapabilityState>
    ): CapabilityState {
        if (descriptor.minimumSdk > Build.VERSION.SDK_INT ||
            (descriptor.maximumSdk != null && Build.VERSION.SDK_INT > descriptor.maximumSdk)
        ) return CapabilityState.NOT_SUPPORTED

        fun unavailable(state: PermissionState): CapabilityState = when (state) {
            PermissionState.DENIED_CAN_REQUEST -> CapabilityState.REQUIRES_ACTION
            PermissionState.NOT_APPLICABLE -> CapabilityState.NOT_SUPPORTED
            PermissionState.DENIED_PERMANENT, PermissionState.BLOCKED -> CapabilityState.BLOCKED
            else -> CapabilityState.UNAVAILABLE
        }

        descriptor.requiredPermissions.forEach { permission ->
            val state = permissionManager.getState(permission)
            if (state != PermissionState.GRANTED) return if (descriptor.optional) CapabilityState.DEGRADED else unavailable(state)
        }
        descriptor.requiredRoles.forEach { role ->
            val state = permissionManager.getState(role)
            if (state != PermissionState.GRANTED) return if (descriptor.optional) CapabilityState.DEGRADED else unavailable(state)
        }
        descriptor.requiredSpecialAccess.forEach { access ->
            val state = permissionManager.getState(access)
            if (state != PermissionState.GRANTED) return if (descriptor.optional) CapabilityState.DEGRADED else unavailable(state)
        }
        descriptor.dependencies.forEach { dependency ->
            when (currentStates[dependency]) {
                CapabilityState.AVAILABLE -> Unit
                CapabilityState.DEGRADED -> return if (descriptor.optional) CapabilityState.DEGRADED else CapabilityState.BLOCKED
                CapabilityState.REQUIRES_ACTION -> return if (descriptor.optional) CapabilityState.DEGRADED else CapabilityState.REQUIRES_ACTION
                CapabilityState.NOT_SUPPORTED -> return CapabilityState.NOT_SUPPORTED
                else -> return if (descriptor.optional) CapabilityState.DEGRADED else CapabilityState.BLOCKED
            }
        }
        return CapabilityState.AVAILABLE
    }

    override fun getState(id: String): CapabilityState = _capabilityStates.value[id] ?: CapabilityState.UNKNOWN
    override fun isAvailable(id: String): Boolean = getState(id) == CapabilityState.AVAILABLE
    override fun isDegraded(id: String): Boolean = getState(id) == CapabilityState.DEGRADED
    override fun isBlocked(id: String): Boolean = getState(id) in setOf(CapabilityState.BLOCKED, CapabilityState.UNAVAILABLE, CapabilityState.NOT_SUPPORTED)
    override fun getDescriptor(id: String): CapabilityDescriptor? = descriptors.find { it.id == id }
    override fun getCapabilitiesByCategory(category: CapabilityCategory): List<CapabilityDescriptor> = descriptors.filter { it.category == category }

    override fun getHealthPercentage(): Int {
        val required = descriptors.filter { it.required && !it.optional }
        return if (required.isEmpty()) 100 else required.count { isAvailable(it.id) } * 100 / required.size
    }

    override fun getProblematicCapabilities(): List<CapabilityDescriptor> = descriptors.filter {
        it.required && getState(it.id) in setOf(CapabilityState.BLOCKED, CapabilityState.UNAVAILABLE, CapabilityState.REQUIRES_ACTION)
    }

    override suspend fun refreshCapabilities(): Map<String, CapabilityState> = refresh()
    override fun addListener(listener: CapabilityListener) { listeners.add(listener) }
    override fun removeListener(listener: CapabilityListener) { listeners.remove(listener) }

    override fun openSettings(capabilityId: String, context: Context): Boolean =
        SettingsNavigator(context).openBestSettingsFor(capabilityId)

    companion object {
        const val ROLE_CALL_SCREENING = "role_call_screening"
        const val CALL_SCREENING = "CALL_SCREENING"
        const val CALL_FIREWALL = "CALL_FIREWALL"
        const val LOCAL_SPAM_SIGNALS = "LOCAL_SPAM_SIGNALS"
        const val CONTACTS_INTEGRATION = "CONTACTS_INTEGRATION"
        const val NOTIFICATIONS = "NOTIFICATIONS"
        const val BATTERY_OPTIMIZATION = "BATTERY_OPTIMIZATION"
        const val BATTERY_OPTIMIZATION_ACCESS = "battery_optimization"
        const val DATABASE = "DATABASE"
        const val SECURITY_EVENTS = "SECURITY_EVENTS"
    }
}
