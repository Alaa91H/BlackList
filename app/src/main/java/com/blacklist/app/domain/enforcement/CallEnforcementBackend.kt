package com.blacklist.app.domain.enforcement

import android.content.Context
import com.blacklist.app.domain.model.CallEvent
import com.blacklist.app.domain.model.Decision
import com.blacklist.app.domain.model.VerificationStatus

/**
 * Typed, constrained Android call-enforcement contract.
 * The product uses only standard CallScreening and Telecom capabilities.
 */
interface CallEnforcementBackend {
    val type: BackendType
    val isAvailable: Boolean
    suspend fun enforce(event: CallEvent, decision: Decision): EnforcementResult
    suspend fun verify(event: CallEvent, decision: Decision): VerificationStatus
}

enum class BackendType { CALL_SCREENING, TELECOM, ROOT, SHIZUKU }

data class EnforcementResult(
    val success: Boolean,
    val backend: BackendType,
    val verification: VerificationStatus,
    val message: String? = null,
    val durationMs: Long = 0
)

data class CapabilityMatrix(
    val callScreening: Capability,
    val telecom: Capability,
    val root: Capability,
    val shizuku: Capability,
    val notifications: Capability,
    val contacts: Capability
)

enum class Capability { AVAILABLE, UNAVAILABLE, GRANTED, DENIED, UNKNOWN }
