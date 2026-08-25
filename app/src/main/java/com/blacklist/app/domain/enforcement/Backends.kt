package com.blacklist.app.domain.enforcement

import android.content.Context
import android.os.Build
import com.blacklist.app.domain.model.CallEvent
import com.blacklist.app.domain.model.Decision
import com.blacklist.app.domain.model.VerificationStatus

/**
 * Standard Android CallScreening backend. Actual enforcement occurs only in
 * CallScreeningService through respondToCall after local policy evaluation.
 */
class AndroidCallScreeningBackend(
    @Suppress("unused") private val context: Context
) : CallEnforcementBackend {
    override val type = BackendType.CALL_SCREENING
    override val isAvailable: Boolean = true

    override suspend fun enforce(event: CallEvent, decision: Decision): EnforcementResult = EnforcementResult(
        success = true,
        backend = type,
        verification = VerificationStatus.UNKNOWN,
        message = "CallScreening intent for ${decision.name}",
        durationMs = 0
    )

    override suspend fun verify(event: CallEvent, decision: Decision): VerificationStatus = VerificationStatus.SUCCESS
}

/** Non-privileged compatibility fallback; it never bypasses CallScreeningService. */
class TelecomBackend(
    @Suppress("unused") private val context: Context
) : CallEnforcementBackend {
    override val type = BackendType.TELECOM
    override val isAvailable: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    override suspend fun enforce(event: CallEvent, decision: Decision): EnforcementResult = EnforcementResult(
        success = false,
        backend = type,
        verification = VerificationStatus.UNKNOWN,
        message = "Telecom fallback is not an enforcement path; use CallScreening.",
        durationMs = 0
    )

    override suspend fun verify(event: CallEvent, decision: Decision): VerificationStatus = VerificationStatus.UNKNOWN
}

/**
 * Resolves only Android-supported, non-privileged backends. Root, Shizuku and
 * accessibility-driven blocking are deliberately absent from the product.
 */
class EnforcementResolver(
    private val backends: List<CallEnforcementBackend>
) {
    fun resolve(preferred: BackendType? = null): List<CallEnforcementBackend> {
        val supported = backends.filter { it.type in setOf(BackendType.CALL_SCREENING, BackendType.TELECOM) && it.isAvailable }
            .sortedBy { if (it.type == BackendType.CALL_SCREENING) 0 else 1 }
        val selected = preferred?.takeIf { it in setOf(BackendType.CALL_SCREENING, BackendType.TELECOM) }
            ?.let { type -> supported.find { it.type == type } }
        return if (selected == null) supported else listOf(selected) + supported.filterNot { it === selected }
    }

    suspend fun enforceWithFallback(event: CallEvent, decision: Decision): EnforcementResult {
        val chain = resolve()
        if (chain.isEmpty()) return EnforcementResult(false, BackendType.CALL_SCREENING, VerificationStatus.FAILED, "No Android enforcement backend is available")
        var lastFailure: EnforcementResult? = null
        for (backend in chain) {
            val result = backend.enforce(event, decision)
            if (result.success) return result.copy(verification = backend.verify(event, decision))
            lastFailure = result
        }
        return lastFailure ?: EnforcementResult(false, BackendType.CALL_SCREENING, VerificationStatus.FAILED, "No enforcement result")
    }

    fun capabilityMatrix(): CapabilityMatrix {
        fun capability(type: BackendType): Capability = if (backends.any { it.type == type && it.isAvailable }) Capability.AVAILABLE else Capability.UNAVAILABLE
        return CapabilityMatrix(
            callScreening = capability(BackendType.CALL_SCREENING),
            telecom = capability(BackendType.TELECOM),
            root = Capability.UNAVAILABLE,
            shizuku = Capability.UNAVAILABLE,
            notifications = Capability.UNKNOWN,
            contacts = Capability.UNKNOWN
        )
    }
}
