package com.example.spam_decliner_9000.worker

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.spam_decliner_9000.data.db.SpamDatabase
import com.example.spam_decliner_9000.data.model.SpamEntry
import com.example.spam_decliner_9000.data.remote.SpamApiClient
import java.util.concurrent.TimeUnit

/**
 * Periodic WorkManager job that downloads FTC Do Not Call complaint data and
 * upserts it into the local Room database.
 *
 * Data source: FTC Do Not Call Complaints dataset via the Socrata open data API
 *   https://data.ftc.gov/resource/dumd-b9yd.json
 *
 * This dataset contains phone numbers reported by consumers for making illegal
 * robocalls or violating the Do Not Call Registry. It is published by the
 * Federal Trade Commission and updated regularly.
 *
 * Schedule: once every 7 days, on Wi-Fi only, when battery is not low.
 * (Weekly is sufficient — the FTC dataset doesn't change hourly.)
 *
 * Pagination: Socrata caps responses at 50,000 records per request. The worker
 * pages through the dataset until it receives an empty page, then stops.
 */
class SpamSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val TAG get() = Companion.TAG

    // Socrata page size — 50,000 is the maximum allowed
    private val PAGE_SIZE = 50_000

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting FTC Do Not Call database sync")

        return try {
            val dao = SpamDatabase.getInstance(applicationContext).spamNumberDao()
            var offset = 0
            var totalInserted = 0

            // Page through the FTC dataset until we get an empty page
            while (true) {
                val page = SpamApiClient.ftcService.getComplaints(
                    limit  = PAGE_SIZE,
                    offset = offset
                )

                if (page.isEmpty()) {
                    Log.d(TAG, "FTC sync complete — all pages fetched")
                    break
                }

                // Normalize and filter out records with missing/blank numbers
                val entries = page
                    .mapNotNull { it.phoneNumber?.trim()?.takeIf { n -> n.isNotBlank() } }
                    .map { raw ->
                        SpamEntry(
                            phoneNumber = normalizeE164(raw),
                            category    = "ftc_complaint",
                            reportCount = 1,
                            confidence  = 0.9f
                        )
                    }

                dao.insertAll(entries)
                totalInserted += entries.size
                offset += PAGE_SIZE

                Log.d(TAG, "FTC sync: inserted ${entries.size} entries " +
                        "(total so far: $totalInserted, offset: $offset)")
            }

            Log.d(TAG, "FTC sync finished — $totalInserted total entries upserted")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "FTC sync failed", e)
            Result.retry()
        }
    }

    /**
     * Normalizes FTC phone numbers to E.164.
     * FTC records are typically 10-digit US numbers stored as plain digits.
     */
    private fun normalizeE164(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return when {
            raw.startsWith("+")                              -> "+$digits"
            digits.length == 10                              -> "+1$digits"
            digits.length == 11 && digits.startsWith("1")   -> "+$digits"
            else                                             -> raw.trim()
        }
    }

    companion object {
        private const val TAG = "SpamSyncWorker"
        private const val WORK_NAME = "ftc_spam_sync"

        /**
         * Enqueues the periodic sync. Safe to call multiple times —
         * [ExistingPeriodicWorkPolicy.KEEP] won't reschedule if already queued.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED) // Wi-Fi only
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<SpamSyncWorker>(
                repeatInterval = 7,
                repeatIntervalTimeUnit = TimeUnit.DAYS
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )

            Log.d(TAG, "FTC sync scheduled (weekly, Wi-Fi only)")
        }
    }
}
