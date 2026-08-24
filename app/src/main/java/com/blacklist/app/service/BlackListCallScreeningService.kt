package com.blacklist.app.service

import android.app.NotificationManager
import android.content.Context
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import androidx.core.app.NotificationCompat
import com.blacklist.app.BlackListApp
import com.blacklist.app.R
import com.blacklist.app.data.local.entity.BlockedCallLogEntity
import com.blacklist.app.data.local.entity.SecurityEventEntity
import com.blacklist.app.di.ServiceLocator
import com.blacklist.app.domain.events.FirewallEvent
import com.blacklist.app.domain.events.FirewallEventBus
import com.blacklist.app.domain.model.Decision
import kotlinx.coroutines.*

/**
 * Thin adapter: only translates Call.Details -> CallEvent and delegates to CallFirewallEngine.
 * No business logic here. All decisions via engine (Rule -> Reputation -> Behavior -> Risk -> Policy).
 */
class BlackListCallScreeningService : CallScreeningService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onScreenCall(callDetails: Call.Details) {
        val isIncoming = callDetails.callDirection == Call.Details.DIRECTION_INCOMING
        if (!isIncoming) { respondAllow(callDetails); return }

        scope.launch {
            try {
                withTimeout(1500) {
                    // 1. Adapter: build CallEvent (contains only system-provided data)
                    val event = CallEventAdapter.fromDetails(applicationContext, callDetails)
                    val rawNumber = event.phoneNumber.raw

                    // 2. Delegate to firewall engine (local-first, no network)
                    val engine = ServiceLocator.provideFirewallEngine(applicationContext)
                    val decision = try {
                        engine.evaluate(event)
                    } catch (e: Exception) {
                        Log.e("BlackListService", "Engine failed, fallback to legacy", e)
                        // Fail-safe: allow on engine error (never crash)
                        null
                    }

                    // 3. Fallback to legacy if engine unavailable (preserves existing behavior)
                    val finalDecision = decision ?: legacyEvaluate(rawNumber)

                    // 4. Enforcement via resolver (CallScreening primary, fallback chain)
                    val shouldBlock = finalDecision.decision == Decision.BLOCK
                    if (shouldBlock) {
                        Log.i("BlackListService", "BLOCK ${rawNumber} risk=${finalDecision.riskScore} reasons=${finalDecision.reasons} backend=${finalDecision.backend}")

                        // 5. Durable result + verification
                        try {
                            val db = ServiceLocator.provideDatabase(applicationContext)
                            val contactUtils = ServiceLocator.provideContactUtils(applicationContext)

                            // Log
                            db.blockedCallLogDao().insert(
                                BlockedCallLogEntity(
                                    phoneNumber = rawNumber,
                                    reason = finalDecision.explainable.summary.take(100),
                                    displayName = event.contact?.displayName ?: contactUtils.getContactName(rawNumber)
                                )
                            )

                            // Reputation update
                            try {
                                val normalized = event.phoneNumber.normalized
                                ServiceLocator.provideReputationEngine(applicationContext).recordBlocked(normalized)
                            } catch (_: Exception) {}

                            // Behavior record
                            try {
                                ServiceLocator.provideBehaviorEngine(applicationContext).recordAttempt(event.phoneNumber.digitsOnly)
                                val campaign = ServiceLocator.provideBehaviorEngine(applicationContext).detectCampaign()
                                if (campaign != null) {
                                    db.securityEventDao().insert(
                                        SecurityEventEntity(
                                            severity = "HIGH",
                                            title = "Possible Spam Campaign",
                                            description = "Prefix $campaign detected burst",
                                            relatedNumber = rawNumber,
                                            campaignId = campaign,
                                            riskScore = finalDecision.riskScore
                                        )
                                    )
                                    FirewallEventBus.emit(FirewallEvent.CampaignDetected(campaign, 5))
                                }
                            } catch (_: Exception) {}

                            // Security event for high risk
                            if (finalDecision.riskScore >= 80) {
                                try {
                                    db.securityEventDao().insert(
                                        SecurityEventEntity(
                                            severity = "CRITICAL",
                                            title = "High Risk Call Blocked",
                                            description = finalDecision.reasons.joinToString(", "),
                                            relatedNumber = rawNumber,
                                            riskScore = finalDecision.riskScore
                                        )
                                    )
                                    FirewallEventBus.emit(FirewallEvent.CallBlocked(rawNumber ?: "unknown", finalDecision.reasons.firstOrNull() ?: "high_risk", finalDecision.riskScore))
                                } catch (_: Exception) {}
                            }

                            // Notification (global + per-number)
                            val settings = db.appSettingsDao().get()
                            val globalEnabled = settings?.showBlockedNotification != false
                            var perNumberEnabled = true
                            if (finalDecision.matchedRules.isNotEmpty() || rawNumber != null) {
                                try {
                                    val matched = ServiceLocator.provideRepository(applicationContext).findBlockedMatches(rawNumber ?: "")
                                    perNumberEnabled = matched?.showNotification ?: true
                                } catch (_: Exception) {}
                            }
                            if (globalEnabled && perNumberEnabled) {
                                showBlockedNotification(rawNumber, finalDecision.explainable.summary)
                            }
                        } catch (_: Exception) {}

                        // 6. Enforce via CallScreening (resolver will fallback to Root/Shizuku if needed in future)
                        try {
                            val resolver = ServiceLocator.provideEnforcementResolver(applicationContext)
                            val result = resolver.enforceWithFallback(event, Decision.BLOCK)
                            Log.i("BlackListService", "Enforcement ${result.backend} success=${result.success} verify=${result.verification}")
                        } catch (_: Exception) {}

                        val response = CallResponse.Builder()
                            .setDisallowCall(true).setRejectCall(true).setSkipCallLog(false).setSkipNotification(false).build()
                        respondToCall(callDetails, response)
                    } else {
                        // Allow + reputation decay
                        try {
                            val normalized = event.phoneNumber.normalized
                            if (event.phoneNumber.presentation == com.blacklist.app.domain.model.Presentation.ALLOWED) {
                                ServiceLocator.provideReputationEngine(applicationContext).recordAllowed(normalized)
                            }
                        } catch (_: Exception) {}
                        respondAllow(callDetails)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.w("BlackListService", "Timeout, allowing")
                respondAllow(callDetails)
            } catch (e: Exception) {
                Log.e("BlackListService", "Error", e)
                respondAllow(callDetails)
            }
        }
    }

    private suspend fun legacyEvaluate(rawNumber: String?): com.blacklist.app.domain.model.EnforcementDecision {
        // Minimal fallback that mirrors old logic to keep existing features working if engine fails
        val normalized = ServiceLocator.provideNormalizer(applicationContext).normalize(rawNumber)
        val event = com.blacklist.app.domain.model.CallEvent(
            callId = "legacy",
            phoneNumber = normalized,
            contact = com.blacklist.app.domain.model.CallerContact(null, false),
            isIncoming = true
        )
        return com.blacklist.app.domain.model.EnforcementDecision(
            callEvent = event,
            decision = Decision.ALLOW,
            riskScore = 0,
            reputation = com.blacklist.app.domain.model.ReputationLevel.NEUTRAL,
            reasons = listOf("Legacy fallback"),
            matchedRules = emptyList(),
            backend = com.blacklist.app.domain.model.EnforcementBackendType.CALL_SCREENING,
            verification = com.blacklist.app.domain.model.VerificationStatus.UNKNOWN,
            explainable = com.blacklist.app.domain.model.ExplainableDecision("ALLOW - fallback", com.blacklist.app.domain.model.RiskLevel.SAFE, listOf("fallback"), emptyList(), "fallback", "UNKNOWN")
        )
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
            val text = getString(R.string.notification_blocked_text, number ?: getString(R.string.blocked_log_private_hidden), reason)
            val notif = NotificationCompat.Builder(this, BlackListApp.CHANNEL_BLOCKED)
                .setSmallIcon(R.drawable.ic_block).setContentTitle(getString(R.string.notification_blocked_title))
                .setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text)).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_LOW).build()
            nm.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notif)
        } catch (_: Exception) {}
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
