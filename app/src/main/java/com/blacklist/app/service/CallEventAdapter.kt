package com.blacklist.app.service

import android.content.Context
import android.os.Build
import android.telecom.Call
import android.telecom.Connection
import com.blacklist.app.di.ServiceLocator
import com.blacklist.app.domain.model.CallEvent
import com.blacklist.app.domain.model.CallSource
import com.blacklist.app.domain.model.VerificationStatus
import java.util.UUID

/**
 * Converts framework details to a small immutable event for the screening path.
 *
 * Contact-provider queries are intentionally excluded. Contact membership is
 * loaded beforehand into PolicySnapshotStore, and display-name enrichment runs
 * only after a decision has already been returned to Telecom.
 */
object CallEventAdapter {
    fun fromDetails(context: Context, details: Call.Details): CallEvent {
        val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            details.handle?.schemeSpecificPart
        } else {
            @Suppress("DEPRECATION") details.handle?.schemeSpecificPart
        }
        val phone = ServiceLocator.provideNormalizer(context).normalize(raw)

        val subscriptionId = try {
            // This is best-effort metadata only; no telephony permission or lookup is used.
            details.accountHandle?.id?.hashCode()
        } catch (_: Exception) {
            null
        }

        val verificationStatus = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                when (details.callerNumberVerificationStatus) {
                    Connection.VERIFICATION_STATUS_PASSED -> VerificationStatus.SUCCESS
                    Connection.VERIFICATION_STATUS_FAILED -> VerificationStatus.FAILED
                    else -> VerificationStatus.UNKNOWN
                }
            } else {
                VerificationStatus.UNKNOWN
            }
        } catch (_: Exception) {
            VerificationStatus.UNKNOWN
        }

        return CallEvent(
            callId = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            phoneNumber = phone,
            subscriptionId = subscriptionId,
            contact = null,
            isIncoming = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                details.callDirection == Call.Details.DIRECTION_INCOMING,
            presentationRaw = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) details.handlePresentation else 1
            } catch (_: Exception) {
                null
            },
            verificationStatus = verificationStatus,
            source = CallSource.TELECOM
        )
    }
}
