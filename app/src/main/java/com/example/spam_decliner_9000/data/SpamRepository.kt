package com.example.spam_decliner_9000.data

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import com.example.spam_decliner_9000.data.db.CallLogDao
import com.example.spam_decliner_9000.data.db.SpamNumberDao
import com.example.spam_decliner_9000.data.db.UserListDao
import com.example.spam_decliner_9000.data.model.BlockedCallEntry
import com.example.spam_decliner_9000.data.model.ListType
import com.example.spam_decliner_9000.data.model.SpamEntry
import com.example.spam_decliner_9000.data.model.UserListEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpamRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val spamDao: SpamNumberDao,
    private val userListDao: UserListDao,
    private val callLogDao: CallLogDao
) {
    private val TAG = "SpamRepository"

    // -------------------------------------------------------------------------
    // Personal allowlist
    // -------------------------------------------------------------------------

    suspend fun isAllowlisted(phoneNumber: String): Boolean {
        val normalized = normalizeNumber(phoneNumber)
        return userListDao.isAllowlisted(normalized)
    }

    suspend fun addToAllowlist(phoneNumber: String) {
        val normalized = normalizeNumber(phoneNumber)
        Log.d(TAG, "Adding to allowlist: $normalized")
        userListDao.insert(UserListEntry(phoneNumber = normalized, listType = ListType.ALLOW))
    }

    suspend fun removeFromAllowlist(phoneNumber: String) {
        val normalized = normalizeNumber(phoneNumber)
        userListDao.delete(normalized, ListType.ALLOW)
    }

    // -------------------------------------------------------------------------
    // Personal blocklist
    // -------------------------------------------------------------------------

    suspend fun isBlocklisted(phoneNumber: String): Boolean {
        val normalized = normalizeNumber(phoneNumber)
        return userListDao.isBlocklisted(normalized)
    }

    suspend fun addToBlocklist(phoneNumber: String, note: String? = null) {
        val normalized = normalizeNumber(phoneNumber)
        Log.d(TAG, "Adding to blocklist: $normalized")
        userListDao.insert(UserListEntry(phoneNumber = normalized, listType = ListType.BLOCK, note = note))
    }

    suspend fun removeFromBlocklist(phoneNumber: String) {
        val normalized = normalizeNumber(phoneNumber)
        userListDao.delete(normalized, ListType.BLOCK)
    }

    fun observeBlocklist() = userListDao.observeBlocklist()
    fun observeAllowlist() = userListDao.observeAllowlist()

    // -------------------------------------------------------------------------
    // Local spam database lookup
    // -------------------------------------------------------------------------

    /**
     * Looks up a number in the local Room spam database.
     * The DB is seeded weekly from the FTC Do Not Call complaint dataset
     * by [com.example.spam_decliner_9000.worker.SpamSyncWorker].
     *
     * Returns a [SpamEntry] if found, null otherwise.
     */
    suspend fun lookupSpamDatabase(phoneNumber: String): SpamEntry? {
        val normalized = normalizeNumber(phoneNumber)
        return spamDao.findByNumber(normalized)
    }

    suspend fun addToSpamDatabase(entry: SpamEntry) {
        spamDao.insert(entry)
    }

    // -------------------------------------------------------------------------
    // Contacts → allowlist sync
    // -------------------------------------------------------------------------

    /**
     * Reads every phone number saved on the device and adds it to the personal
     * allowlist. Safe to call multiple times — existing entries are ignored
     * (UserListDao uses OnConflictStrategy.IGNORE).
     *
     * Returns the number of contact numbers processed.
     * Requires READ_CONTACTS permission; returns 0 if not granted.
     */
    suspend fun syncContactsToAllowlist(): Int {
        val cursor = try {
            context.contentResolver.query(
                android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER,
                    android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                ),
                null, null, null
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "READ_CONTACTS not granted, cannot sync contacts")
            return 0
        } ?: return 0

        val entries = mutableListOf<UserListEntry>()
        cursor.use {
            val numberCol = it.getColumnIndexOrThrow(
                android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            val nameCol = it.getColumnIndexOrThrow(
                android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            )
            while (it.moveToNext()) {
                val raw  = it.getString(numberCol)?.trim() ?: continue
                if (raw.isBlank()) continue
                val name = it.getString(nameCol)?.trim()?.takeIf { n -> n.isNotBlank() }
                entries += UserListEntry(
                    phoneNumber = normalizeNumber(raw),
                    listType    = ListType.ALLOW,
                    contactName = name,
                    note        = "imported from contacts"
                )
            }
        }

        entries.forEach { userListDao.upsertContact(it) } // REPLACE keeps contactName current
        Log.d(TAG, "Contacts sync: inserted ${entries.size} allowlist entries")
        return entries.size
    }

    // -------------------------------------------------------------------------
    // Device contacts lookup
    // -------------------------------------------------------------------------

    /**
     * Returns true if the given phone number matches any contact saved on the
     * device. Uses Android's [ContactsContract.PhoneLookup] which is indexed
     * and returns in well under 1ms for typical contact list sizes.
     *
     * Requires the READ_CONTACTS permission to be granted at runtime.
     * Returns false (not a contact) if the permission has not been granted.
     */
    fun isInContacts(phoneNumber: String): Boolean {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.NUMBER),
                null, null, null
            )?.use { cursor -> cursor.moveToFirst() } ?: false
        } catch (e: SecurityException) {
            // READ_CONTACTS not granted — treat as not a contact
            Log.w(TAG, "READ_CONTACTS permission not granted, cannot check contacts")
            false
        }
    }

    // -------------------------------------------------------------------------
    // Blocked call log
    // -------------------------------------------------------------------------

    suspend fun logCall(
        phoneNumber: String,
        source: String,
        outcome: String,
        category: String? = null
    ) {
        val normalized = normalizeNumber(phoneNumber)
        Log.d(TAG, "Logging call: $normalized (source=$source, outcome=$outcome, category=$category)")
        callLogDao.insert(BlockedCallEntry(
            phoneNumber = normalized,
            source = source,
            category = category,
            outcome = outcome
        ))
    }

    /** Convenience wrapper kept for call sites that log a blocked/voicemail action. */
    suspend fun logBlockedCall(
        phoneNumber: String,
        source: String,
        outcome: String = "blocked",
        category: String? = null
    ) = logCall(phoneNumber, source, outcome, category)

    suspend fun hasPriorOutgoingCallTo(phoneNumber: String): Boolean {
        val normalized = normalizeNumber(phoneNumber)
        return callLogDao.hasOutgoingCallTo(normalized)
    }

    suspend fun getBlockedCallHistory(limit: Int = 100): List<BlockedCallEntry> =
        callLogDao.getRecent(limit)

    fun observeBlockedCallHistory() = callLogDao.observeAll()

    // -------------------------------------------------------------------------
    // Number normalization
    // -------------------------------------------------------------------------

    fun normalizeNumber(phoneNumber: String): String {
        val digitsOnly = phoneNumber.filter { it.isDigit() }
        return when {
            phoneNumber.startsWith("+") -> "+$digitsOnly"
            digitsOnly.length == 10 -> "+1$digitsOnly"
            digitsOnly.length == 11 && digitsOnly.startsWith("1") -> "+$digitsOnly"
            else -> phoneNumber.trim()
        }
    }
}
