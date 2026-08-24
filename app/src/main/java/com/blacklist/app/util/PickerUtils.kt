package com.blacklist.app.util

import android.content.Context
import android.database.Cursor
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony

data class PickerItem(
    val number: String,
    val name: String?,
    val typeLabel: String? = null,
    val timestamp: Long? = null
)

object PickerUtils {

    fun getContacts(context: Context): List<PickerItem> {
        val list = mutableListOf<PickerItem>()
        try {
            val cr = context.contentResolver
            val cursor = cr.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.TYPE
                ),
                null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            ) ?: return emptyList()
            cursor.use {
                val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val typeIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
                val seen = mutableSetOf<String>()
                while (it.moveToNext()) {
                    val raw = it.getString(numIdx) ?: continue
                    val norm = PhoneNumberUtils.normalize(raw) ?: continue
                    if (seen.contains(norm)) continue
                    seen.add(norm)
                    val name = it.getString(nameIdx)
                    val type = try { it.getInt(typeIdx) } catch (_: Exception) { null }
                    val label = type?.let { t ->
                        when (t) {
                            ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "Mobile"
                            ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Home"
                            ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "Work"
                            else -> null
                        }
                    }
                    list.add(PickerItem(raw, name, label))
                    if (list.size >= 300) break
                }
            }
        } catch (_: SecurityException) {}
        return list
    }

    fun getCallLog(context: Context): List<PickerItem> {
        val list = mutableListOf<PickerItem>()
        try {
            val cr = context.contentResolver
            val cursor: Cursor? = cr.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.DATE, CallLog.Calls.TYPE),
                null, null,
                CallLog.Calls.DATE + " DESC"
            ) ?: return emptyList()
            cursor?.use { c ->
                val numIdx = c.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIdx = c.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val dateIdx = c.getColumnIndex(CallLog.Calls.DATE)
                val typeIdx = c.getColumnIndex(CallLog.Calls.TYPE)
                val seen = mutableSetOf<String>()
                while (c.moveToNext()) {
                    val raw = c.getString(numIdx) ?: continue
                    if (raw.isBlank()) continue
                    val norm = PhoneNumberUtils.normalize(raw) ?: raw
                    if (seen.contains(norm)) continue
                    seen.add(norm)
                    val name = c.getString(nameIdx)
                    val date = try { c.getLong(dateIdx) } catch (_: Exception) { null }
                    val type = try { c.getInt(typeIdx) } catch (_: Exception) { null }
                    val label = when (type) {
                        CallLog.Calls.INCOMING_TYPE -> "Incoming"
                        CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                        CallLog.Calls.MISSED_TYPE -> "Missed"
                        CallLog.Calls.BLOCKED_TYPE -> "Blocked"
                        else -> null
                    }
                    list.add(PickerItem(raw, name, label, date))
                    if (list.size >= 250) break
                }
            }
        } catch (_: SecurityException) {}
        return list
    }

    fun getSmsSenders(context: Context): List<PickerItem> {
        val list = mutableListOf<PickerItem>()
        try {
            val cr = context.contentResolver
            val cursor = cr.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                null, null,
                Telephony.Sms.DATE + " DESC"
            ) ?: return emptyList()
            cursor?.use { c ->
                val addrIdx = c.getColumnIndex(Telephony.Sms.ADDRESS)
                val dateIdx = c.getColumnIndex(Telephony.Sms.DATE)
                val seen = mutableSetOf<String>()
                while (c.moveToNext()) {
                    val raw = c.getString(addrIdx) ?: continue
                    if (raw.isBlank()) continue
                    val norm = PhoneNumberUtils.normalize(raw) ?: raw
                    if (seen.contains(norm)) continue
                    seen.add(norm)
                    val date = try { c.getLong(dateIdx) } catch (_: Exception) { null }
                    list.add(PickerItem(raw, null, "SMS", date))
                    if (list.size >= 250) break
                }
            }
        } catch (_: SecurityException) {}
        return list
    }
}
