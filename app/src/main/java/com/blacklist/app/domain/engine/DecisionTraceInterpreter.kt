package com.blacklist.app.domain.engine

import com.blacklist.app.domain.model.EnforcementDecision

/**
 * Turns an already-evaluated local firewall decision into a stable, read-only
 * precedence trace for the editor preview. It does not evaluate a rule, access
 * policy storage, or participate in the call-screening path.
 */
object DecisionTraceInterpreter {
    enum class Stage {
        EMERGENCY,
        BEHAVIOR_SIGNALS,
        TEMPORARY_ALLOW,
        WHITELIST,
        TEMPORARY_EXACT_BLOCK,
        PERSISTENT_BLACKLIST,
        LEGACY_BLACKLIST,
        OUTBOUND_CALLBACK_GRACE,
        EMERGENCY_CALLBACK_GRACE,
        SCHEDULE,
        TEMPORARY_FIREWALL,
        BROAD_POLICY,
        REPUTATION_AND_RISK,
        DEFAULT_ALLOW
    }

    enum class State { PASSED, DECISIVE, NOT_REACHED }

    data class Entry(val stage: Stage, val state: State)

    data class Trace(
        val decisiveStage: Stage,
        val entries: List<Entry>
    )

    fun forDecision(decision: EnforcementDecision): Trace {
        val decisiveStage = stageForBackend(decision.explainable.backend)
        val decisiveIndex = ORDER.indexOf(decisiveStage)
        return Trace(
            decisiveStage = decisiveStage,
            entries = ORDER.mapIndexed { index, stage ->
                Entry(
                    stage = stage,
                    state = when {
                        index < decisiveIndex -> State.PASSED
                        index == decisiveIndex -> State.DECISIVE
                        else -> State.NOT_REACHED
                    }
                )
            }
        )
    }

    private fun stageForBackend(backend: String): Stage = when (backend) {
        "emergency" -> Stage.EMERGENCY
        "temporary_allow" -> Stage.TEMPORARY_ALLOW
        "whitelist" -> Stage.WHITELIST
        "temporary_block_exact" -> Stage.TEMPORARY_EXACT_BLOCK
        "blacklist", "blacklist_silence" -> Stage.PERSISTENT_BLACKLIST
        "legacy_blacklist" -> Stage.LEGACY_BLACKLIST
        "outbound_callback_grace" -> Stage.OUTBOUND_CALLBACK_GRACE
        "emergency_callback_grace" -> Stage.EMERGENCY_CALLBACK_GRACE
        "schedule", "schedule_exception" -> Stage.SCHEDULE
        "temporary_block_all" -> Stage.TEMPORARY_FIREWALL
        "policy", "private", "private_silence", "unknown", "unknown_silence" -> Stage.BROAD_POLICY
        "offline_reputation", "risk", "behavior" -> Stage.REPUTATION_AND_RISK
        else -> Stage.DEFAULT_ALLOW
    }

    private val ORDER = listOf(
        Stage.EMERGENCY,
        Stage.BEHAVIOR_SIGNALS,
        Stage.TEMPORARY_ALLOW,
        Stage.WHITELIST,
        Stage.TEMPORARY_EXACT_BLOCK,
        Stage.PERSISTENT_BLACKLIST,
        Stage.LEGACY_BLACKLIST,
        Stage.OUTBOUND_CALLBACK_GRACE,
        Stage.EMERGENCY_CALLBACK_GRACE,
        Stage.SCHEDULE,
        Stage.TEMPORARY_FIREWALL,
        Stage.BROAD_POLICY,
        Stage.REPUTATION_AND_RISK,
        Stage.DEFAULT_ALLOW
    )
}
