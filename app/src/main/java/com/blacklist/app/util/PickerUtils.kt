package com.blacklist.app.util

import android.content.Context
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.PhoneNumberUtils

data class PickerItem(
    val number: String,
    val name: String?,
    val typeLabel: String? = null,
    val timestamp: Long? = null
)

/**
 * Privacy-minimized picker data source for the Play variant.
 * Contact access is opt-in and is never consulted in the call-screening hot path.
 */
object PickerUtils {
    fun getCallLog(context: Context): List<PickerItem> {
        val list = mutableListOf<PickerItem>()
        return try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE),
                null, null, CallLog.Calls.DATE + " DESC"
            )?.use { cursor ->
                val numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIndex = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val typeIndex = cursor.getColumnIndex(CallLog.Calls.TYPE)
                val dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE)
                val seen = mutableSetOf<String>()
                while (cursor.moveToNext() && list.size < MAX_HISTORY_ITEMS) {
                    val raw = cursor.getString(numberIndex) ?: continue
                    val normalized = PhoneNumberUtils.normalize(raw) ?: continue
                    if (!seen.add(normalized)) continue
                    val label = when (cursor.getInt(typeIndex)) {
                        CallLog.Calls.INCOMING_TYPE -> "Incoming"
                        CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                        CallLog.Calls.MISSED_TYPE -> "Missed"
                        CallLog.Calls.BLOCKED_TYPE -> "Blocked"
                        else -> null
                    }
                    list += PickerItem(raw, cursor.getString(nameIndex), label, cursor.getLong(dateIndex))
                }
                list
            } ?: emptyList()
        } catch (_: SecurityException) { emptyList() }
        catch (_: Exception) { emptyList() }
    }

    fun getMessages(context: Context): List<PickerItem> {
        val list = mutableListOf<PickerItem>()
        return try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.DATE, Telephony.Sms.TYPE),
                null, null, Telephony.Sms.DATE + " DESC"
            )?.use { cursor ->
                val addressIndex = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                val dateIndex = cursor.getColumnIndex(Telephony.Sms.DATE)
                val typeIndex = cursor.getColumnIndex(Telephony.Sms.TYPE)
                val seen = mutableSetOf<String>()
                while (cursor.moveToNext() && list.size < MAX_HISTORY_ITEMS) {
                    val raw = cursor.getString(addressIndex) ?: continue
                    val normalized = PhoneNumberUtils.normalize(raw) ?: continue
                    if (!seen.add(normalized)) continue
                    val label = if (cursor.getInt(typeIndex) == Telephony.Sms.MESSAGE_TYPE_SENT) "Sent" else "SMS"
                    list += PickerItem(raw, null, label, cursor.getLong(dateIndex))
                }
                list
            } ?: emptyList()
        } catch (_: SecurityException) { emptyList() }
        catch (_: Exception) { emptyList() }
    }

    fun getContacts(context: Context): List<PickerItem> {
        val list = mutableListOf<PickerItem>()
        try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.TYPE
                ),
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            ) ?: return emptyList()
            cursor.use {
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val typeIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
                val seen = mutableSetOf<String>()
                while (it.moveToNext()) {
                    val rawNumber = it.getString(numberIndex) ?: continue
                    val normalizedNumber = PhoneNumberUtils.normalize(rawNumber) ?: continue
                    if (!seen.add(normalizedNumber)) continue
                    val name = it.getString(nameIndex)
                    val label = runCatching { it.getInt(typeIndex) }.getOrNull()?.let { type ->
                        when (type) {
                            ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "Mobile"
                            ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Home"
                            ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "Work"
                            else -> null
                        }
                    }
                    list.add(PickerItem(rawNumber, name, label))
                    if (list.size >= MAX_CONTACTS) break
                }
            }
        } catch (_: SecurityException) {
            return emptyList()
        }
        return list
    }

    private const val MAX_CONTACTS = 300
    private const val MAX_HISTORY_ITEMS = 300
}
