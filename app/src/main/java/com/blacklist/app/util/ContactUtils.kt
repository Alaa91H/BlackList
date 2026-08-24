package com.blacklist.app.util

import android.content.Context
import android.provider.ContactsContract

class ContactUtils(
    private val context: Context
) {
    fun isInContacts(phoneNumber: String?): Boolean {
        if (phoneNumber.isNullOrBlank()) return false
        if (PhoneNumberUtils.isPrivateOrHidden(phoneNumber)) return false
        return try {
            val resolver = context.contentResolver
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            resolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    val cNum = cursor.getString(idx) ?: continue
                    if (PhoneNumberUtils.matches(cNum, phoneNumber)) return true
                }
            }
            false
        } catch (_: SecurityException) { false } catch (_: Exception) { false }
    }

    fun getContactName(phoneNumber: String?): String? {
        if (phoneNumber.isNullOrBlank()) return null
        return try {
            val resolver = context.contentResolver
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            resolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    val cNum = cursor.getString(numIdx) ?: continue
                    if (PhoneNumberUtils.matches(cNum, phoneNumber)) return cursor.getString(nameIdx)
                }
            }
            null
        } catch (_: Exception) { null }
    }
}
