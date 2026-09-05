package com.blacklist.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract

data class ContactGroupSnapshot(
    val id: Long,
    val title: String,
    val phoneNumbers: List<String>
)

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

    /**
     * Loads bounded per-account contact groups and their phone numbers off the
     * screening path. Provider failures return an empty list so group rules fail open.
     */
    fun getGroupsWithPhoneNumbers(maxGroups: Int = 100, maxNumbersPerGroup: Int = 500): List<ContactGroupSnapshot> {
        if (!canReadContacts()) return emptyList()
        return try {
            val resolver = context.contentResolver
            val groups = buildList {
                resolver.query(
                    ContactsContract.Groups.CONTENT_URI,
                    arrayOf(ContactsContract.Groups._ID, ContactsContract.Groups.TITLE),
                    "${ContactsContract.Groups.DELETED} = 0",
                    null,
                    "${ContactsContract.Groups.TITLE} COLLATE NOCASE ASC"
                )?.use { cursor ->
                    val idIndex = cursor.getColumnIndex(ContactsContract.Groups._ID)
                    val titleIndex = cursor.getColumnIndex(ContactsContract.Groups.TITLE)
                    while (cursor.moveToNext() && size < maxGroups) {
                        val id = cursor.getLong(idIndex)
                        val title = cursor.getString(titleIndex).orEmpty().ifBlank { "Group $id" }
                        add(id to title)
                    }
                }
            }
            groups.mapNotNull { (groupId, title) ->
                val contactIds = buildList {
                    resolver.query(
                        ContactsContract.Data.CONTENT_URI,
                        arrayOf(ContactsContract.Data.CONTACT_ID),
                        "${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID} = ?",
                        arrayOf(ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE, groupId.toString()),
                        null
                    )?.use { cursor ->
                        val contactIndex = cursor.getColumnIndex(ContactsContract.Data.CONTACT_ID)
                        while (cursor.moveToNext() && size < maxNumbersPerGroup) add(cursor.getLong(contactIndex))
                    }
                }.distinct()
                val numbers = contactIds.flatMap { contactId ->
                    buildList {
                        resolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                            arrayOf(contactId.toString()),
                            null
                        )?.use { cursor ->
                            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                            while (cursor.moveToNext() && size < maxNumbersPerGroup) {
                                cursor.getString(numberIndex)?.takeIf(String::isNotBlank)?.let(::add)
                            }
                        }
                    }
                }.distinct().take(maxNumbersPerGroup)
                ContactGroupSnapshot(groupId, title, numbers)
            }
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
