package com.example.spam_decliner_9000.worker

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.spam_decliner_9000.data.SpamRepository
import com.example.spam_decliner_9000.data.db.SpamDatabase
import com.example.spam_decliner_9000.data.model.ListType
import com.example.spam_decliner_9000.data.model.UserListEntry
import java.util.concurrent.TimeUnit

/**
 * Periodic WorkManager job that reads all phone numbers from the device's
 * contacts and upserts them into the personal allowlist.
 *
 * Schedule: every 24 hours, no network required (contacts are local).
 * New contacts added during the day are picked up on the next run.
 *
 * Requires READ_CONTACTS permission — silently skips if not granted.
 */
class ContactsSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting contacts → allowlist sync")

        val db = SpamDatabase.getInstance(applicationContext)
        val userListDao = db.userListDao()

        val cursor = try {
            applicationContext.contentResolver.query(
                android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER,
                    android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                ),
                null, null, null
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "READ_CONTACTS not granted — skipping contacts sync")
            return Result.success() // not an error, permission just hasn't been granted yet
        } ?: run {
            Log.w(TAG, "Contacts cursor was null — skipping")
            return Result.success()
        }

        var count = 0
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
                userListDao.upsertContact(
                    UserListEntry(
                        phoneNumber = normalizeE164(raw),
                        listType    = ListType.ALLOW,
                        contactName = name,
                        note        = "imported from contacts"
                    )
                )
                count++
            }
        }

        Log.d(TAG, "Contacts sync complete — $count numbers processed")
        return Result.success()
    }

    private fun normalizeE164(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return when {
            raw.startsWith("+")                            -> "+$digits"
            digits.length == 10                            -> "+1$digits"
            digits.length == 11 && digits.startsWith("1") -> "+$digits"
            else                                           -> raw.trim()
        }
    }

    companion object {
        private const val TAG              = "ContactsSyncWorker"
        private const val PERIODIC_WORK    = "contacts_sync_periodic"
        private const val IMMEDIATE_WORK   = "contacts_sync_immediate"

        /**
         * Schedules the 24-hour periodic sync.
         * Safe to call multiple times — KEEP policy won't reschedule if already queued.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ContactsSyncWorker>(
                repeatInterval = 24,
                repeatIntervalTimeUnit = TimeUnit.HOURS
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )

            Log.d(TAG, "Periodic contacts sync scheduled (every 24h)")
        }

        /**
         * Triggers an immediate one-off sync — call this right after the
         * READ_CONTACTS permission is granted so the allowlist is populated
         * without waiting up to 24 hours for the first periodic run.
         */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<ContactsSyncWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK,
                ExistingWorkPolicy.REPLACE,
                request
            )
            Log.d(TAG, "Immediate contacts sync enqueued")
        }
    }
}
