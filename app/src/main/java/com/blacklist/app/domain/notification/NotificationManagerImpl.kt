package com.blacklist.app.domain.notification

import android.app.NotificationChannel
import android.app.NotificationManager as SystemNotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.blacklist.app.BlackListApp
import com.blacklist.app.R
import com.blacklist.app.domain.model.Decision
import com.blacklist.app.domain.model.EnforcementDecision
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Default implementation of NotificationManager
 */
class NotificationManagerImpl(
    private val context: Context
) : NotificationManager {

    private val notificationManager = context.getSystemService(SystemNotificationManager::class.java)
    private val scope = CoroutineScope(Dispatchers.IO)

    // Notification channels
    private val CHANNEL_SECURITY_ALERTS = "security_alerts"
    private val CHANNEL_HIGH_RISK = "high_risk_calls"
    private val CHANNEL_SPAM = "spam_detection"
    private val CHANNEL_CALLS = "call_activity"
    private val CHANNEL_PERMISSIONS = "permission_warnings"
    private val CHANNEL_DIAGNOSTICS = "diagnostics"
    private val CHANNEL_SERVICE = "service_status"
    private val CHANNEL_AUTOMATION = "automation"

    private val _policy = MutableStateFlow(NotificationPolicy())
    val policy: StateFlow<NotificationPolicy> = _policy.asStateFlow()

    // Notification history
    private val historyList = mutableListOf<NotificationRecord>()
    private val _history = MutableStateFlow<List<NotificationRecord>>(emptyList())
    val history: StateFlow<List<NotificationRecord>> = _history.asStateFlow()

    // Rate limiting
    private val recentNotifications = mutableMapOf<String, Long>()
    private val securityAlertsThisHour = mutableListOf<Long>()
    private val deduplicationCache = mutableMapOf<String, Long>()

    init {
        initializeChannels()
    }

    override fun initializeChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channels = listOf(
            NotificationChannel(
                CHANNEL_SECURITY_ALERTS,
                context.getString(R.string.channel_security_alerts),
                SystemNotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_security_alerts_desc)
                enableVibration(true)
                enableLights(true)
                lightColor = 0xFFFF0000.toInt()
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            },
            NotificationChannel(
                CHANNEL_HIGH_RISK,
                context.getString(R.string.channel_high_risk),
                SystemNotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_high_risk_desc)
                enableVibration(true)
                enableLights(true)
                lightColor = 0xFFFF4444.toInt()
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            },
            NotificationChannel(
                CHANNEL_SPAM,
                context.getString(R.string.channel_spam),
                SystemNotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.channel_spam_desc)
                enableVibration(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
            },
            NotificationChannel(
                CHANNEL_CALLS,
                context.getString(R.string.channel_calls),
                SystemNotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.channel_calls_desc)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
            },
            NotificationChannel(
                CHANNEL_PERMISSIONS,
                context.getString(R.string.channel_permissions),
                SystemNotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.channel_permissions_desc)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
            },
            NotificationChannel(
                CHANNEL_DIAGNOSTICS,
                context.getString(R.string.channel_diagnostics),
                SystemNotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.channel_diagnostics_desc)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
            },
            NotificationChannel(
                CHANNEL_SERVICE,
                context.getString(R.string.channel_service),
                SystemNotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.channel_service_desc)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
            },
            NotificationChannel(
                CHANNEL_AUTOMATION,
                context.getString(R.string.channel_automation),
                SystemNotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.channel_automation_desc)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
            }
        )

        for (channel in channels) {
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun getPolicy(): NotificationPolicy = _policy.value

    override fun updatePolicy(policy: NotificationPolicy) {
        _policy.value = policy
    }

    override fun areNotificationsEnabled(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return notificationManager.areNotificationsEnabled()
        }
        return true
    }

    override fun isChannelEnabled(channelId: String): Boolean {
        val channel = notificationManager.getNotificationChannel(channelId)
        return channel != null && channel.importance != SystemNotificationManager.IMPORTANCE_NONE
    }

    override suspend fun notifySecurity(
        title: String,
        body: String,
        riskScore: Int,
        number: String?,
        actionLabel: String?,
        actionIntent: Intent?
    ) {
        if (!_policy.value.enabled) return
        if (!checkRateLimit("security", _policy.value.maxSecurityAlertsPerHour * 60L * 1000L, _policy.value.maxSecurityAlertsPerHour)) return

        val channelId = if (riskScore >= 80) CHANNEL_HIGH_RISK else CHANNEL_SECURITY_ALERTS
        val importance = if (riskScore >= 80) SystemNotificationManager.IMPORTANCE_HIGH else SystemNotificationManager.IMPORTANCE_HIGH

        val builder = NotificationCompat.Builder(context, if (riskScore >= 80) CHANNEL_HIGH_RISK else CHANNEL_SECURITY_ALERTS)
            .setSmallIcon(R.drawable.ic_block)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(importance)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setGroup("CALL_SECURITY")
            .setGroupSummary(false)

        number?.let { builder.setSubText(it) }

        actionLabel?.let { label ->
            actionIntent?.let { intent ->
                val pending = PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                builder.addAction(R.drawable.ic_block, label, pending)
            }
        }

        builder.setAutoCancel(true)
        builder.setOnlyAlertOnce(true)

        val notification = builder.build()
        val id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        notificationManager.notify(id, notification)

        recordHistory(NotificationRecord(
            category = NotificationCategory.SECURITY,
            title = title,
            body = body,
            channelId = if (riskScore >= 80) CHANNEL_HIGH_RISK else CHANNEL_SECURITY_ALERTS,
            severity = NotificationSeverity.CRITICAL
        ))
    }

    override suspend fun notifySpam(
        title: String,
        body: String,
        number: String?,
        count: Int,
        campaignId: String?
    ) {
        if (!_policy.value.enabled) return
        if (!checkRateLimit("spam", 60000, _policy.value.maxNotificationsPerMinute)) return

        val key = "spam_${number ?: "unknown"}"
        if (_policy.value.deduplicationEnabled && isDuplicate(key)) return

        val builder = NotificationCompat.Builder(context, CHANNEL_SPAM)
            .setSmallIcon(R.drawable.ic_block)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(SystemNotificationManager.IMPORTANCE_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
            .setGroup("SPAM")

        if (count > 1) {
            builder.setNumber(count)
            builder.setContentText("$body ($count calls)")
        }

        number?.let { builder.setSubText(it) }

        val notification = builder.build()
        val id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        notificationManager.notify(id, notification)

        recordHistory(NotificationRecord(
            category = NotificationCategory.SPAM,
            title = title,
            body = body,
            channelId = CHANNEL_SPAM,
            severity = NotificationSeverity.WARNING
        ))
    }

    override suspend fun notifyCampaign(
        prefix: String,
        count: Int,
        attempts: Int,
        durationMinutes: Int
    ) {
        if (!_policy.value.enabled) return

        val builder = NotificationCompat.Builder(context, CHANNEL_SPAM)
            .setSmallIcon(R.drawable.ic_block)
            .setContentTitle("SPAM CAMPAIGN DETECTED")
            .setContentText("$count numbers, $attempts attempts in ${durationMinutes}min")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "Prefix: $prefix\n" +
                "Numbers: $count\n" +
                "Attempts: $attempts\n" +
                "Duration: ${durationMinutes}min\n" +
                "Risk: HIGH"
            ))
            .setPriority(SystemNotificationManager.IMPORTANCE_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setGroup("CAMPAIGNS")
            .setGroupSummary(true)

        val notification = builder.build()
        val id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        notificationManager.notify(id, notification)

        recordHistory(NotificationRecord(
            category = NotificationCategory.CAMPAIGNS,
            title = "SPAM CAMPAIGN DETECTED",
            body = "$count numbers, $attempts attempts in ${durationMinutes}min",
            channelId = CHANNEL_SPAM,
            severity = NotificationSeverity.CRITICAL
        ))
    }

    override suspend fun notifyCallFlood(
        count: Int,
        durationMinutes: Int,
        reason: String
    ) {
        if (!_policy.value.enabled) return

        val builder = NotificationCompat.Builder(context, CHANNEL_SECURITY_ALERTS)
            .setSmallIcon(R.drawable.ic_block)
            .setContentTitle("CALL FLOOD DETECTED")
            .setContentText("$count calls in ${durationMinutes}min")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "$count calls in ${durationMinutes}min\nReason: $reason\nTemporary protection enabled."
            ))
            .setPriority(SystemNotificationManager.IMPORTANCE_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setGroup("CALL_SECURITY")

        val notification = builder.build()
        val id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        notificationManager.notify(id, notification)

        recordHistory(NotificationRecord(
            category = NotificationCategory.SECURITY,
            title = "Call Flood Detected",
            body = "$count calls in ${durationMinutes}min",
            channelId = CHANNEL_SECURITY_ALERTS,
            severity = NotificationSeverity.CRITICAL
        ))
    }

    override suspend fun notifyPermissionWarning(
        capability: String,
        reason: String,
        actionLabel: String,
        action: () -> Unit
    ) {
        if (!_policy.value.enabled) return

        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_PERMISSIONS)
            .setSmallIcon(R.drawable.ic_block)
            .setContentTitle("Permission Required")
            .setContentText(reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
            .setPriority(SystemNotificationManager.IMPORTANCE_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_block, actionLabel, PendingIntent.getActivity(
                context, 0,
                android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            ))

        val notification = builder.build()
        val id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        notificationManager.notify(id, notification)

        recordHistory(NotificationRecord(
            category = NotificationCategory.PERMISSIONS,
            title = "Permission Required",
            body = reason,
            channelId = CHANNEL_PERMISSIONS,
            severity = NotificationSeverity.WARNING
        ))
    }

    override suspend fun notifyDiagnostic(
        title: String,
        message: String,
        severity: NotificationSeverity
    ) {
        if (!_policy.value.enabled) return

        val channel = when (severity) {
            NotificationSeverity.CRITICAL -> CHANNEL_DIAGNOSTICS
            NotificationSeverity.ERROR -> CHANNEL_DIAGNOSTICS
            NotificationSeverity.WARNING -> CHANNEL_DIAGNOSTICS
            else -> CHANNEL_DIAGNOSTICS
        }

        val importance = when (severity) {
            NotificationSeverity.CRITICAL -> SystemNotificationManager.IMPORTANCE_HIGH
            NotificationSeverity.ERROR -> SystemNotificationManager.IMPORTANCE_HIGH
            NotificationSeverity.WARNING -> SystemNotificationManager.IMPORTANCE_DEFAULT
            else -> SystemNotificationManager.IMPORTANCE_LOW
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_DIAGNOSTICS)
            .setSmallIcon(R.drawable.ic_block)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(importance)
            .setCategory(NotificationCompat.CATEGORY_SYSTEM)
            .setAutoCancel(true)

        val notification = builder.build()
        val id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        notificationManager.notify(id, notification)

        recordHistory(NotificationRecord(
            category = NotificationCategory.DIAGNOSTICS,
            title = title,
            body = message,
            channelId = CHANNEL_DIAGNOSTICS,
            severity = severity
        ))
    }

    override suspend fun notifyCallBlocked(
        number: String?,
        reason: String,
        riskScore: Int,
        decision: EnforcementDecision
    ) {
        if (!_policy.value.enabled) return

        val isHighRisk = riskScore >= 80
        val channel = if (isHighRisk) CHANNEL_HIGH_RISK else CHANNEL_SECURITY_ALERTS

        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_block)
            .setContentTitle(if (isHighRisk) "HIGH RISK CALL BLOCKED" else "Call Blocked")
            .setContentText("Blocked $number — $reason")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "Blocked ${number ?: "Private"} — $reason\nRisk: $riskScore/100"
            ))
            .setPriority(if (isHighRisk) SystemNotificationManager.IMPORTANCE_HIGH else SystemNotificationManager.IMPORTANCE_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setGroup("CALL_SECURITY")

        val notification = builder.build()
        val id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        notificationManager.notify(id, notification)

        recordHistory(NotificationRecord(
            category = NotificationCategory.CALLS,
            title = "Call Blocked",
            body = "Blocked ${number ?: "Private"} — $reason",
            channelId = channel,
            severity = if (isHighRisk) NotificationSeverity.CRITICAL else NotificationSeverity.WARNING
        ))
    }

    override suspend fun sendTestNotification(channelId: String) {
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_block)
            .setContentTitle("Test Notification")
            .setContentText("This is a test notification for channel: $channelId")
            .setStyle(NotificationCompat.BigTextStyle().bigText("If you see this, notifications are working correctly for channel: $channelId"))
            .setPriority(SystemNotificationManager.IMPORTANCE_DEFAULT)
            .setAutoCancel(true)

        val notification = builder.build()
        val id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        notificationManager.notify(id, notification)
    }

    override fun getHistory(limit: Int): List<NotificationRecord> {
        return historyList.take(limit)
    }

    override fun clearHistory() {
        historyList.clear()
        _history.value = emptyList()
    }

    private fun checkRateLimit(key: String, windowMs: Long, maxCount: Int): Boolean {
        val now = System.currentTimeMillis()
        val windowStart = now - windowMs

        // Clean old entries
        recentNotifications.values.removeIf { it < windowStart }
        securityAlertsThisHour.removeIf { it < windowStart }

        if (key == "security") {
            return securityAlertsThisHour.size < 20
        }

        return recentNotifications[key]?.let { it < maxCount } ?: true
    }

    private fun isDuplicate(key: String): Boolean {
        val now = System.currentTimeMillis()
        val window = 5 * 60 * 1000 // 5 minutes

        val lastTime = deduplicationCache[key]
        if (lastTime != null && now - lastTime < 5 * 60 * 1000) {
            return true
        }
        deduplicationCache[key] = System.currentTimeMillis()
        return false
    }

    private fun recordHistory(record: NotificationRecord) {
        historyList.add(0, record)
        if (historyList.size > 500) historyList.removeLast()
        _history.value = historyList.toList()
    }

    // Constants for channels
    companion object {
        const val CHANNEL_SECURITY_ALERTS = "security_alerts"
        const val CHANNEL_HIGH_RISK = "high_risk_calls"
        const val CHANNEL_SPAM = "spam_detection"
        const val CHANNEL_CALLS = "call_activity"
        const val CHANNEL_PERMISSIONS = "permission_warnings"
        const val CHANNEL_DIAGNOSTICS = "diagnostics"
        const val CHANNEL_SERVICE = "service_status"
        const val CHANNEL_AUTOMATION = "automation"
    }
}