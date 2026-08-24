package com.blacklist.app.domain.events

import com.blacklist.app.domain.model.Decision
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Local event bus for firewall → NexaFlow/automation.
 * No coupling, no network, local-first.
 */
object FirewallEventBus {
    private val _events = MutableSharedFlow<FirewallEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<FirewallEvent> = _events

    fun emit(event: FirewallEvent) {
        _events.tryEmit(event)
    }
}

sealed class FirewallEvent {
    data class CallBlocked(val number: String, val reason: String, val risk: Int) : FirewallEvent()
    data class CallAllowed(val number: String) : FirewallEvent()
    data class CallSilenced(val number: String) : FirewallEvent()
    data class SpamDetected(val number: String, val score: Int) : FirewallEvent()
    data class RiskChanged(val number: String, val newRisk: Int) : FirewallEvent()
    data class CampaignDetected(val prefix: String, val count: Int) : FirewallEvent()
    data class ProtectionModeChanged(val profile: String) : FirewallEvent()
    data class SecurityEventCreated(val severity: String, val title: String) : FirewallEvent()
}

// NexaFlow triggers/actions (typed, policy-gated)
enum class NexaFlowTrigger { CALL_BLOCKED, SPAM_DETECTED, RISK_GE_X, CAMPAIGN_DETECTED, REPEATED_CALLER, PROTECTION_CHANGED }
enum class NexaFlowAction { BLOCK_NUMBER, UNBLOCK_NUMBER, ADD_BLACKLIST, ADD_WHITELIST, SILENCE_CALLER, CHANGE_PROFILE, ENABLE_MAX_PROTECTION, CREATE_TEMP_RULE, NOTIFY_USER, CREATE_SECURITY_EVENT }
