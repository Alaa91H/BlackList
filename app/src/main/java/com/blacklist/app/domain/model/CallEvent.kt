package com.blacklist.app.domain.model

/**
 * Unified call event abstraction - contains only data the system actually provides.
 * No fake data. No network-derived fields at creation time.
 */
data class CallEvent(
    val callId: String, // telecom call ID or timestamp-based
    val timestamp: Long = System.currentTimeMillis(),
    val phoneNumber: PhoneNumber,
    val subscriptionId: Int? = null, // SIM id if available (requires READ_PHONE_STATE)
    val simSlot: Int? = null,
    val contact: CallerContact? = null,
    val isIncoming: Boolean = true,
    val presentationRaw: Int? = null, // Telecom presentation (PRESENTATION_* )
    val source: CallSource = CallSource.TELECOM
)

data class CallerContact(
    val displayName: String?,
    val isInContacts: Boolean,
    val contactId: Long? = null,
    val isStarred: Boolean = false,
    val isVip: Boolean = false
)

enum class CallSource { TELECOM, BROADCAST, SHIZUKU, TEST }

enum class CallDirection { INCOMING, OUTGOING, UNKNOWN }
