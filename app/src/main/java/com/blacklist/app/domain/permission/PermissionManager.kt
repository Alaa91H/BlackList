package com.blacklist.app.domain.permission

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Central source of truth for permissions and the call-screening role. */
interface PermissionManager {
    val permissionStates: StateFlow<Map<String, PermissionState>>
    val descriptors: List<PermissionDescriptor>
    suspend fun refresh(): Map<String, PermissionState>
    fun getState(id: String): PermissionState
    fun isGranted(id: String): Boolean
    fun canRequest(id: String): Boolean
    fun getDescriptor(id: String): PermissionDescriptor?
    suspend fun request(id: String, context: Context): Boolean
    fun openSettings(id: String, context: Context): Boolean
    fun getMissingRequiredPermissions(): List<PermissionDescriptor>
    fun getMissingRecommendedPermissions(): List<PermissionDescriptor>
    suspend fun refreshCapabilities(): Map<String, PermissionState>
}

/**
 * Permissions are intentionally conservative. Core incoming-call protection
 * requires only the user-granted ROLE_CALL_SCREENING. Contacts and
 * notifications remain optional enhancements, and rejection never disables
 * local block rules.
 */
class PermissionManagerImpl(
    private val context: Context
) : PermissionManager {

    private val _permissionStates = MutableStateFlow<Map<String, PermissionState>>(emptyMap())
    override val permissionStates: StateFlow<Map<String, PermissionState>> = _permissionStates.asStateFlow()

    override val descriptors: List<PermissionDescriptor> = listOf(
        PermissionDescriptor(
            id = ROLE_CALL_SCREENING,
            name = "Call screening",
            description = "Lets BlackList decide on incoming calls before they ring.",
            category = PermissionCategory.ROLES,
            required = true,
            runtime = false,
            specialAccess = false,
            role = true,
            minimumSdk = Build.VERSION_CODES.Q,
            rationale = "This system role is required for call screening.",
            remediation = "Choose BlackList as the call screening app in Android settings."
        ),
        PermissionDescriptor(
            id = android.Manifest.permission.READ_CONTACTS,
            name = "Contacts",
            description = "Optionally identifies saved callers for the unsaved-number rule.",
            category = PermissionCategory.CONTACTS,
            required = false,
            runtime = true,
            specialAccess = false,
            role = false,
            rationale = "Only needed when you want to block numbers that are not saved in contacts.",
            remediation = "Grant Contacts access in Android settings to enable this optional rule.",
            optional = true
        ),
        PermissionDescriptor(
            id = android.Manifest.permission.READ_CALL_LOG,
            name = "Call log",
            description = "Optionally selects numbers from the local call history.",
            category = PermissionCategory.CALLS,
            required = false,
            runtime = true,
            specialAccess = false,
            role = false,
            rationale = "Only needed when you choose the Call log source in the number picker.",
            remediation = "Grant Call log access from the in-app permission manager to use this optional picker source.",
            optional = true
        ),
        PermissionDescriptor(
            id = android.Manifest.permission.READ_SMS,
            name = "Messages",
            description = "Optionally selects sender numbers from local SMS history.",
            category = PermissionCategory.OPTIONAL,
            required = false,
            runtime = true,
            specialAccess = false,
            role = false,
            rationale = "Only needed when you choose the Messages source in the number picker.",
            remediation = "Grant Messages access from the in-app permission manager to use this optional picker source.",
            optional = true
        ),
        PermissionDescriptor(
            id = android.Manifest.permission.POST_NOTIFICATIONS,
            name = "Notifications",
            description = "Optionally shows private, low-priority summaries of blocked calls.",
            category = PermissionCategory.NOTIFICATIONS,
            required = false,
            runtime = true,
            specialAccess = false,
            role = false,
            minimumSdk = Build.VERSION_CODES.TIRAMISU,
            rationale = "Blocking continues when this permission is denied.",
            remediation = "Grant Notifications access in Android settings if you want summaries.",
            optional = true
        ),
        PermissionDescriptor(
            id = BATTERY_OPTIMIZATION,
            name = "Battery optimization",
            description = "Optional reliability setting for devices that aggressively stop background work.",
            category = PermissionCategory.SPECIAL_ACCESS,
            required = false,
            runtime = false,
            specialAccess = true,
            role = false,
            rationale = "Most devices work without changing battery settings.",
            remediation = "Only change this setting if Android reports a service reliability issue.",
            optional = true
        )
    )

    override suspend fun refresh(): Map<String, PermissionState> {
        val states = descriptors.associate { descriptor -> descriptor.id to evaluatePermission(descriptor) }
        _permissionStates.value = states
        return states
    }

    private fun evaluatePermission(descriptor: PermissionDescriptor): PermissionState {
        if (descriptor.minimumSdk > Build.VERSION.SDK_INT ||
            (descriptor.maximumSdk != null && Build.VERSION.SDK_INT > descriptor.maximumSdk)
        ) return PermissionState.NOT_APPLICABLE

        return when {
            descriptor.role -> checkCallScreeningRole()
            descriptor.specialAccess -> checkSpecialAccess(descriptor.id)
            descriptor.runtime -> {
                if (ContextCompat.checkSelfPermission(context, descriptor.id) == PackageManager.PERMISSION_GRANTED) {
                    PermissionState.GRANTED
                } else {
                    PermissionState.DENIED_CAN_REQUEST
                }
            }
            else -> PermissionState.AVAILABLE
        }
    }

    private fun checkCallScreeningRole(): PermissionState {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return PermissionState.NOT_APPLICABLE
        val roleManager = context.getSystemService(RoleManager::class.java) ?: return PermissionState.UNAVAILABLE
        return try {
            when {
                roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) -> PermissionState.GRANTED
                roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) -> PermissionState.DENIED_CAN_REQUEST
                else -> PermissionState.UNAVAILABLE
            }
        } catch (_: SecurityException) {
            PermissionState.UNAVAILABLE
        }
    }

    private fun checkSpecialAccess(access: String): PermissionState = when (access) {
        BATTERY_OPTIMIZATION -> {
            val powerManager = context.getSystemService(PowerManager::class.java)
            if (powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true) {
                PermissionState.GRANTED
            } else {
                PermissionState.DENIED_CAN_REQUEST
            }
        }
        else -> PermissionState.UNKNOWN
    }

    override fun getState(id: String): PermissionState = _permissionStates.value[id] ?: PermissionState.UNKNOWN

    override fun isGranted(id: String): Boolean = getState(id) == PermissionState.GRANTED

    override fun canRequest(id: String): Boolean = getState(id) == PermissionState.DENIED_CAN_REQUEST

    override fun getDescriptor(id: String): PermissionDescriptor? = descriptors.find { it.id == id }

    override suspend fun request(id: String, context: Context): Boolean = isGranted(id)

    override fun openSettings(id: String, context: Context): Boolean = try {
        val intent = when (id) {
            ROLE_CALL_SCREENING -> Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            BATTERY_OPTIMIZATION -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
            }
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }

    override fun getMissingRequiredPermissions(): List<PermissionDescriptor> =
        descriptors.filter { it.required && !isGranted(it.id) }

    override fun getMissingRecommendedPermissions(): List<PermissionDescriptor> =
        descriptors.filter { !it.required && !it.optional && !isGranted(it.id) }

    override suspend fun refreshCapabilities(): Map<String, PermissionState> = refresh()

    companion object {
        const val ROLE_CALL_SCREENING = "role_call_screening"
        const val BATTERY_OPTIMIZATION = "battery_optimization"
    }
}
