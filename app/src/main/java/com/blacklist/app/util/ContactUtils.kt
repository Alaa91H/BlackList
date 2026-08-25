package com.blacklist.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract

class ContactUtils(
    private val context: Context
) {
    fun canReadContacts(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

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

    /**
     * Loads the user's phone numbers outside the screening path. Permission
     * failures deliberately become an empty set so protection keeps working
     * when contact access is optional or has been revoked.
     */
    fun getAllPhoneNumbers(): List<String> {
        if (!canReadContacts()) return emptyList()
        return try {
            val resolver = context.contentResolver
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            resolver.query(
                uri,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                null,
                null,
                null
            )?.use { cursor ->
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                buildList {
                    while (cursor.moveToNext()) {
                        cursor.getString(numberIndex)?.takeIf { it.isNotBlank() }?.let(::add)
                    }
                }
            } ?: emptyList()
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
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
