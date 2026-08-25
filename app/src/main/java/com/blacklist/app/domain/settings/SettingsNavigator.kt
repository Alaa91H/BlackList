package com.blacklist.app.domain.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Centralized system settings navigation.
 * All system settings intents should go through here for consistency and OEM compatibility.
 */
class SettingsNavigator(private val context: Context) {

    /**
     * Open app-specific settings
     */
    fun openAppSettings(): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Open call screening role settings
     */
    fun openCallScreeningRoleSettings(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return try {
            val rm = context.getSystemService(android.app.role.RoleManager::class.java)
            val intent = rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_CALL_SCREENING)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            openAppSettings()
        }
    }

    /**
     * Open notification settings
     */
    fun openNotificationSettings(): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            openAppSettings()
        }
    }

    /**
     * Open notification channel settings
     */
    fun openNotificationChannelSettings(channelId: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return try {
            val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            openNotificationSettings()
        }
    }

    /**
     * Open battery optimization settings
     */
    fun openBatteryOptimizationSettings(): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            openAppSettings()
        }
    }

    /**
     * Open manage all files access (Android 11+)
     */
    fun openManageAllFilesAccess(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        return try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            openAppSettings()
        }
    }

    /**
     * Open default dialer settings
     */
    fun openDefaultDialerSettings(): Boolean {
        return try {
            val intent = Intent(android.telecom.TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                .putExtra(android.telecom.TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            openAppSettings()
        }
    }

    /**
     * Open special access settings (fallback)
     */
    fun openSpecialAccessSettings(): Boolean {
        return openAppSettings()
    }

    /**
     * Open default apps settings
     */
    fun openDefaultAppsSettings(): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            openAppSettings()
        }
    }

    /**
     * Open general app settings with fallback chain
     */
    fun openBestSettingsFor(capability: String): Boolean {
        return when (capability) {
            "CALL_SCREENING", "CALL_FIREWALL", "role_call_screening" -> openCallScreeningRoleSettings()
            "NOTIFICATIONS", "android.permission.POST_NOTIFICATIONS" -> openNotificationSettings()
            "BATTERY_OPTIMIZATION", "battery_optimization" -> openBatteryOptimizationSettings()
            "CONTACTS_INTEGRATION", "android.permission.READ_CONTACTS" -> openAppSettings()
            else -> openAppSettings()
        }
    }
}