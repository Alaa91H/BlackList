package com.blacklist.app.service

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import androidx.core.app.NotificationCompat
import com.blacklist.app.BlackListApp
import com.blacklist.app.R
import com.blacklist.app.di.ServiceLocator
import com.blacklist.app.util.PhoneNumberUtils
import com.blacklist.app.util.ScheduleEvaluator
import kotlinx.coroutines.*

class BlackListCallScreeningService : CallScreeningService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onScreenCall(callDetails: Call.Details) {
        val rawNumber = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            callDetails.handle?.schemeSpecificPart
        } else {
            @Suppress("DEPRECATION") callDetails.handle?.schemeSpecificPart
        }
        val isIncoming = callDetails.callDirection == Call.Details.DIRECTION_INCOMING
        if (!isIncoming) { respondAllow(callDetails); return }

        scope.launch {
            try {
                withTimeout(1500) {
                    val shouldBlock = evaluateShouldBlock(rawNumber)
                    if (shouldBlock.first) {
                        Log.i("BlackListService", "Blocking $rawNumber reason=${shouldBlock.second}")
                        try {
                            val db = ServiceLocator.provideDatabase(applicationContext)
                            val contactUtils = ServiceLocator.provideContactUtils(applicationContext)
                            db.blockedCallLogDao().insert(
                                com.blacklist.app.data.local.entity.BlockedCallLogEntity(
                                    phoneNumber = rawNumber,
                                    reason = shouldBlock.second,
                                    displayName = contactUtils.getContactName(rawNumber)
                                )
                            )
                            val settings = db.appSettingsDao().get()
                            if (settings?.showBlockedNotification != false) {
                                showBlockedNotification(rawNumber, shouldBlock.second)
                            }
                        } catch (_: Exception) {}
                        val response = CallResponse.Builder()
                            .setDisallowCall(true).setRejectCall(true).setSkipCallLog(false).setSkipNotification(false).build()
                        respondToCall(callDetails, response)
                    } else {
                        respondAllow(callDetails)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.w("BlackListService", "Timeout evaluating, allowing call")
                respondAllow(callDetails)
            } catch (e: Exception) {
                Log.e("BlackListService", "Error", e)
                respondAllow(callDetails)
            }
        }
    }

    private suspend fun evaluateShouldBlock(rawNumber: String?): Pair<Boolean, String> {
        val db = ServiceLocator.provideDatabase(applicationContext)
        val contactUtils = ServiceLocator.provideContactUtils(applicationContext)

        if (!rawNumber.isNullOrBlank() && isWhitelisted(rawNumber)) return false to "WHITELIST"
        if (PhoneNumberUtils.isPrivateOrHidden(rawNumber)) {
            val schedule = activeScheduleMode()
            if (schedule != null) return evaluateScheduleBlock(rawNumber, schedule)
            val settings = db.appSettingsDao().get() ?: return false to "NO_SETTINGS"
            return if (settings.blockPrivate) true to "PRIVATE" else false to "PRIVATE_ALLOWED"
        }
        val settings = db.appSettingsDao().get() ?: return false to "NO_SETTINGS"
        val activeRule = activeScheduleMode()
        if (activeRule != null) return evaluateScheduleBlock(rawNumber, activeRule)
        if (settings.blockAllExceptWhitelist) return true to "ALL_EXCEPT_WHITELIST"
        if (!rawNumber.isNullOrBlank() && isBlacklisted(rawNumber)) return true to "BLACKLIST"
        if (settings.blockUnknown) {
            if (!rawNumber.isNullOrBlank()) {
                val inContacts = try { contactUtils.isInContacts(rawNumber) } catch (_: Exception) { true }
                if (!inContacts) return true to "UNKNOWN"
            }
        }
        return false to "ALLOWED"
    }

    private suspend fun activeScheduleMode(): com.blacklist.app.data.local.entity.ScheduleRuleEntity? {
        val db = ServiceLocator.provideDatabase(applicationContext)
        val rules = try { db.scheduleRuleDao().getEnabled() } catch (_: Exception) { emptyList() }
        return ScheduleEvaluator.matchingRule(rules)
    }

    private suspend fun evaluateScheduleBlock(rawNumber: String?, rule: com.blacklist.app.data.local.entity.ScheduleRuleEntity): Pair<Boolean, String> {
        return when (rule.mode) {
            com.blacklist.app.data.local.entity.ScheduleRuleEntity.MODE_ALL -> true to "SCHEDULE"
            com.blacklist.app.data.local.entity.ScheduleRuleEntity.MODE_ALL_EXCEPT_WHITELIST -> {
                if (!rawNumber.isNullOrBlank() && isWhitelisted(rawNumber)) false to "WHITELIST_SCHEDULE" else true to "SCHEDULE"
            }
            com.blacklist.app.data.local.entity.ScheduleRuleEntity.MODE_UNKNOWN_PRIVATE -> {
                if (PhoneNumberUtils.isPrivateOrHidden(rawNumber)) true to "SCHEDULE"
                else {
                    val inContacts = if (!rawNumber.isNullOrBlank()) try { ServiceLocator.provideContactUtils(applicationContext).isInContacts(rawNumber) } catch (_: Exception) { true } else false
                    if (!inContacts) true to "SCHEDULE" else false to "ALLOWED_SCHEDULE"
                }
            }
            com.blacklist.app.data.local.entity.ScheduleRuleEntity.MODE_BLACKLIST -> {
                if (!rawNumber.isNullOrBlank() && isBlacklisted(rawNumber)) true to "SCHEDULE" else false to "ALLOWED_SCHEDULE"
            }
            else -> false to "ALLOWED"
        }
    }

    private suspend fun isWhitelisted(number: String): Boolean {
        val db = ServiceLocator.provideDatabase(applicationContext)
        val all = try { db.whitelistedNumberDao().getAll() } catch (_: Exception) { emptyList() }
        return all.any { PhoneNumberUtils.matches(it.rawNumber, number) || PhoneNumberUtils.matches(it.normalizedNumber, number) }
    }

    private suspend fun isBlacklisted(number: String): Boolean {
        val db = ServiceLocator.provideDatabase(applicationContext)
        val all = try { db.blockedNumberDao().getAll() } catch (_: Exception) { emptyList() }
        return all.any { PhoneNumberUtils.matches(it.rawNumber, number) || PhoneNumberUtils.matches(it.normalizedNumber, number) }
    }

    private fun respondAllow(details: Call.Details) {
        try {
            val response = CallResponse.Builder().setDisallowCall(false).setRejectCall(false).setSkipCallLog(false).setSkipNotification(false).build()
            respondToCall(details, response)
        } catch (e: Exception) { Log.e("BlackListService", "respondAllow failed", e) }
    }

    private fun showBlockedNotification(number: String?, reason: String) {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val text = getString(R.string.notification_blocked_text, number ?: "Private", reason)
            val notif = NotificationCompat.Builder(this, BlackListApp.CHANNEL_BLOCKED)
                .setSmallIcon(R.drawable.ic_block).setContentTitle(getString(R.string.notification_blocked_title))
                .setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text)).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_LOW).build()
            nm.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notif)
        } catch (_: Exception) {}
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
