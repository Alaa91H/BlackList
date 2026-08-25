package com.blacklist.app.domain.notification

import android.app.NotificationChannel
import android.app.NotificationManager as SystemNotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.blacklist.app.BlackListApp
import com.blacklist.app.R
import com.blacklist.app.domain.model.EnforcementDecision
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Privacy-preserving notification delivery.
 *
 * Call blocking never depends on this component. Notifications intentionally
 * use a small channel set, private lock-screen visibility, low/default
 * importance, grouping and in-memory rate limits.
 */
class NotificationManagerImpl(
    private val context: Context
) : NotificationManager {

    private val notificationManager = context.getSystemService(SystemNotificationManager::class.java)

    private val _policy = MutableStateFlow(NotificationPolicy())
    val policy: StateFlow<NotificationPolicy> = _policy.asStateFlow()

    private val historyList = mutableListOf<NotificationRecord>()
    private val _history = MutableStateFlow<List<NotificationRecord>>(emptyList())
    val history: StateFlow<List<NotificationRecord>> = _history.asStateFlow()

    private val rateLock = Any()
    private val notificationTimes = mutableMapOf<String, ArrayDeque<Long>>()
    private val deduplicationCache = mutableMapOf<String, Long>()

    init {
        initializeChannels()
    }

    override fun initializeChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        // The blocked-calls channel itself is created by BlackListApp during
        // application startup because the service has a small legacy fallback.
        val channels = listOf(
            NotificationChannel(
                CHANNEL_SECURITY_ALERTS,
                context.getString(R.string.channel_security_alerts),
                SystemNotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.channel_security_alerts_desc)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
            },
            NotificationChannel(
                CHANNEL_PERMISSIONS,
                context.getString(R.string.channel_permissions),
                SystemNotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.channel_permissions_desc)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
            },
            NotificationChannel(
                CHANNEL_DIAGNOSTICS,
                context.getString(R.string.channel_diagnostics),
                SystemNotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.channel_diagnostics_desc)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
            }
        )
        channels.forEach(notificationManager::createNotificationChannel)
    }

    override fun getPolicy(): NotificationPolicy = _policy.value

    override fun updatePolicy(policy: NotificationPolicy) {
        _policy.value = policy
    }

    override fun areNotificationsEnabled(): Boolean = notificationManager.areNotificationsEnabled()

    override fun isChannelEnabled(channelId: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return areNotificationsEnabled()
        return notificationManager.getNotificationChannel(channelId)?.importance != SystemNotificationManager.IMPORTANCE_NONE
    }

    override suspend fun notifySecurity(
        title: String,
        body: String,
        riskScore: Int,
        number: String?,
        actionLabel: String?,
        actionIntent: Intent?
    ) {
        if (!_policy.value.enabled || !checkRateLimit("security", HOUR_MS, _policy.value.maxSecurityAlertsPerHour)) return
        val builder = privateBuilder(CHANNEL_SECURITY_ALERTS)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setGroup(GROUP_SECURITY)

        actionLabel?.let { label -> actionIntent?.let { builder.addAction(R.drawable.ic_block, label, activityPendingIntent(it)) } }
        notificationManager.notify(nextNotificationId(), builder.build())
        recordHistory(NotificationRecord(category = NotificationCategory.SECURITY, title = title, body = body, channelId = CHANNEL_SECURITY_ALERTS, severity = NotificationSeverity.WARNING))
    }

    override suspend fun notifySpam(
        title: String,
        body: String,
        number: String?,
        count: Int,
        campaignId: String?
    ) {
        if (!_policy.value.enabled || !checkRateLimit("spam", MINUTE_MS, _policy.value.maxNotificationsPerMinute)) return
        val key = "spam_${number ?: "unknown"}"
        if (_policy.value.deduplicationEnabled && isDuplicate(key)) return
        val displayBody = if (count > 1) "$body ($count calls)" else body
        val builder = privateBuilder(BlackListApp.CHANNEL_BLOCKED)
            .setContentTitle(title)
            .setContentText(displayBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(displayBody))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setGroup(GROUP_BLOCKED)
            .setNumber(count.coerceAtLeast(1))
        notificationManager.notify(nextNotificationId(), builder.build())
        recordHistory(NotificationRecord(category = NotificationCategory.SPAM, title = title, body = displayBody, channelId = BlackListApp.CHANNEL_BLOCKED, severity = NotificationSeverity.WARNING))
    }

    override suspend fun notifyCampaign(prefix: String, count: Int, attempts: Int, durationMinutes: Int) {
        if (!_policy.value.enabled || !checkRateLimit("campaign", HOUR_MS, 3)) return
        val body = "$count numbers, $attempts attempts in ${durationMinutes} min"
        val builder = privateBuilder(CHANNEL_SECURITY_ALERTS)
            .setContentTitle("Local call campaign detected")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setGroup(GROUP_SECURITY)
        notificationManager.notify(nextNotificationId(), builder.build())
        recordHistory(NotificationRecord(category = NotificationCategory.CAMPAIGNS, title = "Local call campaign detected", body = body, channelId = CHANNEL_SECURITY_ALERTS, severity = NotificationSeverity.WARNING))
    }

    override suspend fun notifyCallFlood(count: Int, durationMinutes: Int, reason: String) {
        if (!_policy.value.enabled || !checkRateLimit("flood", HOUR_MS, 3)) return
        val body = "$count calls in ${durationMinutes} min. Local temporary protection is active."
        val builder = privateBuilder(CHANNEL_SECURITY_ALERTS)
            .setContentTitle("Call flood detected")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setGroup(GROUP_SECURITY)
        notificationManager.notify(nextNotificationId(), builder.build())
        recordHistory(NotificationRecord(category = NotificationCategory.SECURITY, title = "Call flood detected", body = body, channelId = CHANNEL_SECURITY_ALERTS, severity = NotificationSeverity.WARNING))
    }

    override suspend fun notifyPermissionWarning(
        capability: String,
        reason: String,
        actionLabel: String,
        action: () -> Unit
    ) {
        if (!_policy.value.enabled || !checkRateLimit("permission_$capability", HOUR_MS, 2)) return
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
        }
        val builder = privateBuilder(CHANNEL_PERMISSIONS)
            .setContentTitle("Action needed for call protection")
            .setContentText(reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .addAction(R.drawable.ic_block, actionLabel, activityPendingIntent(intent))
            .setGroup(GROUP_HEALTH)
        notificationManager.notify(nextNotificationId(), builder.build())
        recordHistory(NotificationRecord(category = NotificationCategory.PERMISSIONS, title = "Action needed for call protection", body = reason, channelId = CHANNEL_PERMISSIONS, severity = NotificationSeverity.WARNING))
    }

    override suspend fun notifyDiagnostic(title: String, message: String, severity: NotificationSeverity) {
        if (!_policy.value.enabled || !checkRateLimit("diagnostic_${severity.name}", HOUR_MS, 5)) return
        val priority = if (severity == NotificationSeverity.CRITICAL || severity == NotificationSeverity.ERROR) {
            NotificationCompat.PRIORITY_DEFAULT
        } else {
            NotificationCompat.PRIORITY_LOW
        }
        val builder = privateBuilder(CHANNEL_DIAGNOSTICS)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(priority)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setGroup(GROUP_HEALTH)
        notificationManager.notify(nextNotificationId(), builder.build())
        recordHistory(NotificationRecord(category = NotificationCategory.DIAGNOSTICS, title = title, body = message, channelId = CHANNEL_DIAGNOSTICS, severity = severity))
    }

    override suspend fun notifyCallBlocked(
        number: String?,
        reason: String,
        riskScore: Int,
        decision: EnforcementDecision
    ) {
        if (!_policy.value.enabled || !checkRateLimit("blocked", MINUTE_MS, _policy.value.maxNotificationsPerMinute)) return
        val key = "blocked_${number ?: "private"}"
        if (_policy.value.deduplicationEnabled && isDuplicate(key)) return

        // Do not expose a caller number in heads-up, group, or lock-screen text.
        val title = context.getString(R.string.notification_blocked_title)
        val body = reason.take(MAX_NOTIFICATION_REASON)
        val builder = privateBuilder(BlackListApp.CHANNEL_BLOCKED)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setGroup(GROUP_BLOCKED)
        notificationManager.notify(nextNotificationId(), builder.build())
        recordHistory(NotificationRecord(category = NotificationCategory.CALLS, title = title, body = "Blocked ${number ?: "Private"} — $body", channelId = BlackListApp.CHANNEL_BLOCKED, severity = NotificationSeverity.INFO))
    }

    override suspend fun sendTestNotification(channelId: String) {
        val builder = privateBuilder(channelId)
            .setContentTitle("BlackList notification test")
            .setContentText("Notifications are working for this channel.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
        notificationManager.notify(nextNotificationId(), builder.build())
    }

    override fun getHistory(limit: Int): List<NotificationRecord> = synchronized(historyList) { historyList.take(limit) }

    override fun clearHistory() {
        synchronized(historyList) { historyList.clear() }
        _history.value = emptyList()
    }

    private fun privateBuilder(channelId: String): NotificationCompat.Builder = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_block)
        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        .setAutoCancel(true)
        .setOnlyAlertOnce(true)

    private fun activityPendingIntent(intent: Intent): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun checkRateLimit(key: String, windowMs: Long, maxCount: Int): Boolean = synchronized(rateLock) {
        val now = System.currentTimeMillis()
        val timestamps = notificationTimes.getOrPut(key) { ArrayDeque() }
        while (timestamps.isNotEmpty() && timestamps.first() <= now - windowMs) timestamps.removeFirst()
        if (timestamps.size >= maxCount.coerceAtLeast(1)) return@synchronized false
        timestamps.addLast(now)
        true
    }

    private fun isDuplicate(key: String): Boolean = synchronized(rateLock) {
        val now = System.currentTimeMillis()
        val windowMs = _policy.value.deduplicationWindowMinutes.coerceAtLeast(1) * MINUTE_MS
        deduplicationCache.entries.removeIf { (_, timestamp) -> timestamp <= now - windowMs }
        val previous = deduplicationCache[key]
        if (previous != null && now - previous < windowMs) true else {
            deduplicationCache[key] = now
            false
        }
    }

    private fun recordHistory(record: NotificationRecord) {
        synchronized(historyList) {
            historyList.add(0, record)
            if (historyList.size > MAX_HISTORY) historyList.removeLast()
            _history.value = historyList.toList()
        }
    }

    private fun nextNotificationId(): Int = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()

    companion object {
        const val CHANNEL_SECURITY_ALERTS = "security_alerts"
        const val CHANNEL_PERMISSIONS = "permission_warnings"
        const val CHANNEL_DIAGNOSTICS = "diagnostics"
        private const val GROUP_BLOCKED = "blocked_calls"
        private const val GROUP_SECURITY = "call_security"
        private const val GROUP_HEALTH = "protection_health"
        private const val MINUTE_MS = 60_000L
        private const val HOUR_MS = 60 * MINUTE_MS
        private const val MAX_HISTORY = 500
        private const val MAX_NOTIFICATION_REASON = 120
    }
}
