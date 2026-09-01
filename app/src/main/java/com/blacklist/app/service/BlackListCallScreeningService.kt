package com.blacklist.app.service

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import com.blacklist.app.data.local.entity.AppSettingsEntity
import com.blacklist.app.data.local.entity.BlockedCallLogEntity
import com.blacklist.app.data.local.entity.SecurityEventEntity
import com.blacklist.app.di.ServiceLocator
import com.blacklist.app.domain.engine.EmergencyCallbackGrace
import com.blacklist.app.domain.engine.OutboundCallbackGrace
import com.blacklist.app.domain.enforcement.BlockedCallLogPrivacyPolicy
import com.blacklist.app.domain.events.FirewallEvent
import com.blacklist.app.domain.events.FirewallEventBus
import com.blacklist.app.domain.model.Decision
import com.blacklist.app.domain.notification.BlockedNotificationGate
import com.blacklist.app.domain.retention.BlockedCallLogRetentionPolicy
import com.blacklist.app.widget.BlockedCallStatsWidgetProvider
import com.blacklist.app.domain.model.EnforcementDecision
import com.blacklist.app.domain.model.Presentation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Android adapter for the local firewall.
 *
 * The critical path is deliberately limited to: framework details -> immutable
 * policy snapshot -> respondToCall. Database writes, contact-name lookup,
 * reputation updates, diagnostics and notifications run only after Telecom has
 * received the answer.
 */
class BlackListCallScreeningService : CallScreeningService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onScreenCall(callDetails: Call.Details) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            callDetails.callDirection != Call.Details.DIRECTION_INCOMING
        ) {
            // Unknown directions deliberately fail open, but only a definite
            // outgoing call may create a callback allowance.
            respondAllow(callDetails)
            if (callDetails.callDirection == Call.Details.DIRECTION_OUTGOING) {
                scope.launch(Dispatchers.IO) { recordOutgoingCallProtections(callDetails) }
            }
            return
        }

        val event = try {
            CallEventAdapter.fromDetails(applicationContext, callDetails)
        } catch (error: Exception) {
            Log.w(TAG, "Could not adapt call details; allowing safely", error)
            respondAllow(callDetails)
            return
        }

        scope.launch {
            val decision = try {
                // Android allows up to five seconds. The local engine has a much
                // smaller budget so a delayed process never delays the ringer.
                withTimeout(HOT_PATH_TIMEOUT_MS) {
                    ServiceLocator.provideFirewallEngine(applicationContext).evaluate(event)
                }
            } catch (error: TimeoutCancellationException) {
                Log.w(TAG, "Policy evaluation exceeded local budget; allowing safely")
                null
            } catch (error: Exception) {
                Log.e(TAG, "Policy evaluation failed; allowing safely", error)
                null
            }

            // This is the only enforcement point. Do not call Room, Contacts,
            // notifications, Root, Shizuku, or a secondary backend before it.
            if (decision == null) {
                respondAllow(callDetails)
                recordScreeningHealthFailure("Policy evaluation fallback")
                return@launch
            }

            respond(callDetails, decision)
            scope.launch(Dispatchers.IO) { persistPostDecisionEffects(decision) }
        }
    }

    /**
     * Runs only after Telecom has received an allow response for a definite
     * outgoing call. It creates either the existing emergency-wide protection
     * or an exact-number callback allowance; both remain entirely local.
     */
    private suspend fun recordOutgoingCallProtections(callDetails: Call.Details) {
        val event = runCatching {
            CallEventAdapter.fromDetails(applicationContext, callDetails)
        }.getOrNull() ?: return
        val normalizer = ServiceLocator.provideNormalizer(applicationContext)
        if (normalizer.isEmergencyNumber(event.phoneNumber)) {
            val expiry = EmergencyCallbackGrace.activate()
            runCatching {
                val settingsDao = ServiceLocator.provideDatabase(applicationContext).appSettingsDao()
                val current = settingsDao.get()
                if (current == null) {
                    settingsDao.upsert(AppSettingsEntity(emergencyCallbackGraceUntil = expiry))
                } else if (current.emergencyCallbackGraceUntil < expiry) {
                    settingsDao.setEmergencyCallbackGraceUntil(expiry)
                }
            }.onFailure { error ->
                // The in-memory grace remains active for this process; a write
                // failure must never delay or change the outgoing call response.
                Log.w(TAG, "Could not persist emergency callback grace", error)
            }
            return
        }

        val digits = event.phoneNumber.digitsOnly
        if (!OutboundCallbackGrace.isValidDigits(digits) ||
            ServiceLocator.providePolicySnapshotStore(applicationContext).snapshot().settings?.allowOutboundCallbackGrace != true
        ) return

        OutboundCallbackGrace.activate(digits) ?: return
        runCatching {
            ServiceLocator.provideRepository(applicationContext).recordOutboundCallbackGrace(digits).getOrThrow()
        }.onFailure { error ->
            // The process-local bridge still covers the snapshot refresh race;
            // persistence failures never alter the already answered call.
            Log.w(TAG, "Could not persist outgoing callback grace", error)
        }
    }

    private fun respond(callDetails: Call.Details, decision: EnforcementDecision) {
        try {
            val settings = ServiceLocator.providePolicySnapshotStore(applicationContext).snapshot().settings
            val skipSystemCallLog = BlockedCallLogPrivacyPolicy.skipSystemCallLog(decision.decision, settings)
            val response = when (decision.decision) {
                Decision.BLOCK -> CallResponse.Builder()
                    .setDisallowCall(true)
                    .setRejectCall(true)
                    .setSkipCallLog(skipSystemCallLog)
                    // BlackList owns blocked-call notifications; suppress the
                    // system fallback so a muted configuration stays muted.
                    .setSkipNotification(true)
                    .build()
                Decision.SILENCE -> CallResponse.Builder().apply {
                    setDisallowCall(false)
                    setRejectCall(false)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        setSilenceCall(true)
                    }
                    setSkipCallLog(false)
                    // Suppress an extra Telecom notification. OEM call UI and
                    // missed-call history remain system-controlled.
                    setSkipNotification(true)
                }.build()
                Decision.ALLOW -> CallResponse.Builder()
                    .setDisallowCall(false)
                    .setRejectCall(false)
                    .setSkipCallLog(false)
                    .setSkipNotification(false)
                    .build()
            }
            respondToCall(callDetails, response)
        } catch (error: Exception) {
            Log.e(TAG, "respondToCall failed", error)
        }
    }

    private suspend fun persistPostDecisionEffects(decision: EnforcementDecision) {
        val event = decision.callEvent
        val number = event.phoneNumber.raw
        val database = ServiceLocator.provideDatabase(applicationContext)

        try {
            if (decision.decision == Decision.BLOCK) {
                val displayName = if (event.phoneNumber.presentation == Presentation.ALLOWED) {
                    ServiceLocator.provideContactUtils(applicationContext).getContactName(number)
                } else {
                    null
                }
                database.blockedCallLogDao().insert(
                    BlockedCallLogEntity(
                        phoneNumber = number,
                        reason = decision.explainable.summary.take(MAX_REASON_LENGTH),
                        displayName = displayName
                    )
                )

                // This cached setting is consulted only after Telecom has received
                // the response and the new private log entry is persisted. An
                // invalid stored value fails conservatively by retaining history.
                val retentionDays = ServiceLocator.providePolicySnapshotStore(applicationContext)
                    .snapshot()
                    .settings
                    ?.blockedLogRetentionDays
                    ?.takeIf(BlockedCallLogRetentionPolicy::isSupported)
                    ?: BlockedCallLogRetentionPolicy.NEVER
                BlockedCallLogRetentionPolicy.deletionCutoffMillis(retentionDays, System.currentTimeMillis())
                    ?.let { cutoffMillis -> database.blockedCallLogDao().deleteOlderThan(cutoffMillis) }

                runCatching {
                    ServiceLocator.provideReputationEngine(applicationContext)
                        .recordBlocked(event.phoneNumber.normalized)
                }
                runCatching {
                    val behavior = ServiceLocator.provideBehaviorEngine(applicationContext)
                    val campaign = behavior.detectCampaign()
                    if (campaign != null) {
                        database.securityEventDao().insert(
                            SecurityEventEntity(
                                severity = "HIGH",
                                title = "Local call burst detected",
                                description = "Multiple recent calls matched a local behavior rule.",
                                relatedNumber = number,
                                campaignId = campaign,
                                riskScore = decision.riskScore
                            )
                        )
                        FirewallEventBus.emit(FirewallEvent.CampaignDetected(campaign, 5))
                    }
                }

                if (decision.riskScore >= HIGH_RISK_THRESHOLD) {
                    database.securityEventDao().insert(
                        SecurityEventEntity(
                            severity = "HIGH",
                            title = "High-risk call blocked",
                            description = decision.reasons.joinToString(", ").take(MAX_REASON_LENGTH),
                            relatedNumber = number,
                            riskScore = decision.riskScore
                        )
                    )
                    FirewallEventBus.emit(
                        FirewallEvent.CallBlocked(
                            number.ifBlank { "private" },
                            decision.reasons.firstOrNull() ?: "local policy",
                            decision.riskScore
                        )
                    )
                }

                notifyBlockedIfEnabled(number, decision)
                runCatching { BlockedCallStatsWidgetProvider.refreshAll(applicationContext) }
                    .onFailure { error -> Log.w(TAG, "Could not refresh blocked-call widget", error) }
            } else if (decision.decision == Decision.ALLOW && event.phoneNumber.presentation == Presentation.ALLOWED) {
                runCatching {
                    ServiceLocator.provideReputationEngine(applicationContext)
                        .recordAllowed(event.phoneNumber.normalized)
                }
            }
        } catch (error: Exception) {
            // Post-decision failures never alter the Telecom response.
            Log.e(TAG, "Post-decision persistence failed", error)
            recordScreeningHealthFailure("Post-decision persistence failure")
        }
    }

    private suspend fun notifyBlockedIfEnabled(number: String?, decision: EnforcementDecision) {
        val settings = ServiceLocator.provideDatabase(applicationContext).appSettingsDao().get()
        val globalEnabled = settings?.showBlockedNotification ?: true

        val perNumberEnabled = runCatching {
            ServiceLocator.provideRepository(applicationContext)
                .findBlockedMatches(number.orEmpty())
                ?.showNotification ?: true
        }.getOrDefault(true)
        if (!BlockedNotificationGate.isAllowed(globalEnabled, perNumberEnabled)) return

        runCatching {
            ServiceLocator.provideNotificationManager(applicationContext).notifyCallBlocked(
                number = number,
                reason = decision.explainable.summary,
                riskScore = decision.riskScore,
                decision = decision
            )
        }.onFailure { error ->
            // Never fall back to an unmanaged notification path: the user's
            // global/per-number privacy choices must remain authoritative.
            Log.w(TAG, "Blocked-call notification was not delivered", error)
        }
    }

    private suspend fun recordScreeningHealthFailure(reason: String) {
        runCatching {
            ServiceLocator.provideDatabase(applicationContext).securityEventDao().insert(
                SecurityEventEntity(
                    severity = "WARNING",
                    title = "Protection decision degraded",
                    description = reason,
                    riskScore = 0
                )
            )
        }
    }

    private fun respondAllow(details: Call.Details) {
        try {
            respondToCall(
                details,
                CallResponse.Builder()
                    .setDisallowCall(false)
                    .setRejectCall(false)
                    .setSkipCallLog(false)
                    .setSkipNotification(false)
                    .build()
            )
        } catch (error: Exception) {
            Log.e(TAG, "Safe fallback response failed", error)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private companion object {
        const val TAG = "BlackListService"
        const val HOT_PATH_TIMEOUT_MS = 750L
        const val HIGH_RISK_THRESHOLD = 80
        const val MAX_REASON_LENGTH = 100
    }
}
