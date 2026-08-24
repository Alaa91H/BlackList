package com.blacklist.app.service

import android.content.Context
import android.os.Build
import android.telecom.Call
import com.blacklist.app.di.ServiceLocator
import com.blacklist.app.domain.model.CallEvent
import com.blacklist.app.domain.model.CallSource
import com.blacklist.app.domain.model.CallerContact
import java.util.UUID

object CallEventAdapter {
    suspend fun fromDetails(context: Context, details: Call.Details): CallEvent {
        val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            details.handle?.schemeSpecificPart
        } else {
            @Suppress("DEPRECATION") details.handle?.schemeSpecificPart
        }
        val normalizer = ServiceLocator.provideNormalizer(context)
        val contactUtils = ServiceLocator.provideContactUtils(context)
        val phone = normalizer.normalize(raw)
        val isInContacts = try {
            if (phone.presentation == com.blacklist.app.domain.model.Presentation.ALLOWED) contactUtils.isInContacts(raw) else false
        } catch (_: Exception) { false }
        val displayName = try { contactUtils.getContactName(raw) } catch (_: Exception) { null }
        val contact = CallerContact(
            displayName = displayName,
            isInContacts = isInContacts,
            isStarred = false,
            isVip = false
        )
        // Try to get SIM slot via accountHandle (best effort, no crash)
        var subId: Int? = null
        var slot: Int? = null
        try {
            val account = details.accountHandle
            // account.id may contain subId; we skip detailed parsing to keep offline-first
            subId = account?.id?.hashCode()
        } catch (_: Exception) {}

        return CallEvent(
            callId = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            phoneNumber = phone,
            subscriptionId = subId,
            simSlot = slot,
            contact = contact,
            isIncoming = details.callDirection == Call.Details.DIRECTION_INCOMING,
            presentationRaw = try { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) details.handlePresentation else 1 } catch (_: Exception) { null },
            source = CallSource.TELECOM
        )
    }
}
