package com.blacklist.app.util

import android.content.Context
import android.provider.ContactsContract

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
}
