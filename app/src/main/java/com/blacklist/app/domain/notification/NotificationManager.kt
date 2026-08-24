package com.blacklist.app.domain.notification

import android.app.NotificationChannel
import android.app.NotificationManager as SystemNotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.blacklist.app.domain.model.Decision
import com.blacklist.app.domain.model.EnforcementDecision

/**
 * Central notification management - all notifications go through this.
 */
interface NotificationManager {
    /** Initialize notification channels */
    fun initializeChannels()

    /** Send a security notification */
    suspend fun notifySecurity(
        title: String,
        body: String,
        riskScore: Int,
        number: String?,
        actionLabel: String? = null,
        actionIntent: android.content.Intent? = null
    )

    /** Send a spam notification */
    suspend fun notifySpam(
        title: String,
        body: String,
        number: String?,
        count: Int = 1,
        campaignId: String? = null
    )

    /** Send a campaign notification */
    suspend fun notifyCampaign(
        prefix: String,
        count: Int,
        attempts: Int,
        durationMinutes: Int
    )

    /** Send a call flood notification */
    suspend fun notifyCallFlood(
        count: Int,
        durationMinutes: Int,
        reason: String
    )

    /** Send a permission warning notification */
    suspend fun notifyPermissionWarning(
        capability: String,
        reason: String,
        actionLabel: String,
        action: () -> Unit
    )

    /** Send a diagnostic notification */
    suspend fun notifyDiagnostic(
        title: String,
        message: String,
        severity: NotificationSeverity
    )

    /** Send a call blocked notification */
    suspend fun notifyCallBlocked(
        number: String?,
        reason: String,
        riskScore: Int,
        decision: EnforcementDecision
    )

    /** Send a test notification */
    suspend fun sendTestNotification(channelId: String)

    /** Check if notifications are enabled */
    fun areNotificationsEnabled(): Boolean

    /** Check if a specific channel is enabled */
    fun isChannelEnabled(channelId: String): Boolean

    /** Get notification policy */
    fun getPolicy(): NotificationPolicy

    /** Update notification policy */
    fun updatePolicy(policy: NotificationPolicy)

    /** Get notification history */
    fun getHistory(limit: Int): List<NotificationRecord>

    /** Clear notification history */
    fun clearHistory()
}

/**
 * Notification severity levels
 */
enum class NotificationSeverity {
    INFO,
    WARNING,
    ERROR,
    CRITICAL
}

/**
 * Notification policy
 */
data class NotificationPolicy(
    val enabled: Boolean = true,
    val importance: Int = SystemNotificationManager.IMPORTANCE_DEFAULT,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val badgeEnabled: Boolean = true,
    val lockScreenVisibility: Int = NotificationCompat.VISIBILITY_PRIVATE,
    val group: String = "default",
    val quietHoursEnabled: Boolean = false,
    val quietHoursStart: Int = 22, // 22:00
    val quietHoursEnd: Int = 7, // 07:00
    val quietHoursCriticalOverride: Boolean = true,
    val deduplicationEnabled: Boolean = true,
    val deduplicationWindowMinutes: Int = 5,
    val maxNotificationsPerMinute: Int = 10,
    val maxSecurityAlertsPerHour: Int = 20,
    val groupEnabled: Boolean = true,
    val profiles: Map<String, ProfileNotificationPolicy> = emptyMap()
)

data class ProfileNotificationPolicy(
    val enabled: Boolean = true,
    val importance: Int = SystemNotificationManager.IMPORTANCE_DEFAULT,
    val quietHoursOverride: Boolean = false
)

/**
 * Notification record for history
 */
data class NotificationRecord(
    val id: Long = System.currentTimeMillis(),
    val timestamp: Long = System.currentTimeMillis(),
    val category: NotificationCategory,
    val title: String,
    val body: String,
    val channelId: String,
    val severity: NotificationSeverity,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Notification categories
 */
enum class NotificationCategory(
    val displayName: String
) {
    SECURITY("Security"),
    CALLS("Calls"),
    SPAM("Spam"),
    CAMPAIGNS("Campaigns"),
    DIAGNOSTICS("Diagnostics"),
    PERMISSIONS("Permissions"),
    SYSTEM("System"),
    SERVICE("Service"),
    AUTOMATION("Automation");

    override fun toString(): String = name
}