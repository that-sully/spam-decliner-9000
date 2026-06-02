package com.example.spam_decliner_9000.service

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import com.example.spam_decliner_9000.data.AppSettings
import com.example.spam_decliner_9000.data.SpamRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Core spam call screening service.
 *
 * Android calls [onScreenCall] for every incoming call. We query our local
 * spam database and personal lists, then respond with a [CallResponse] that
 * tells the system what to do with the call.
 *
 * To activate: Phone app → Settings → Caller ID & Spam → select this app
 *
 * Decision priority (first match wins):
 *   1. Personal allowlist        → always allow
 *   2. Android "Spam Risk" flag  → block (STIR/SHAKEN network verification failed)
 *   3. Personal blocklist        → block
 *   4. Local spam database       → block (seeded from FTC DNC complaint data)
 *   5. Block unknown toggle ON   → send to voicemail if not in device contacts
 *   6. Default                   → allow
 */
@AndroidEntryPoint
class SpamCallScreeningService : CallScreeningService() {

    private val TAG = "SpamCallScreening"

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    @Inject lateinit var repository: SpamRepository
    @Inject lateinit var settings: AppSettings

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "SpamCallScreeningService created")
    }

    override fun onScreenCall(callDetails: Call.Details) {
        val phoneNumber = callDetails.handle?.schemeSpecificPart ?: run {
            // No number available (private/restricted) — check the toggle
            Log.d(TAG, "No phone number available (restricted/private)")
            serviceScope.launch {
                if (settings.blockUnknownNumbers) {
                    Log.d(TAG, "Block unknown toggle is ON — sending restricted number to voicemail")
                    repository.logCall("restricted", source = "unknown_number", outcome = "voicemail")
                    respondToCall(callDetails, buildVoicemailResponse())
                } else {
                    repository.logCall("restricted", source = "default_allow", outcome = "allowed")
                    respondToCall(callDetails, buildAllowResponse())
                }
            }
            return
        }

        Log.d(TAG, "Screening call from: $phoneNumber")

        serviceScope.launch {
            val response = screenNumber(phoneNumber, callDetails)
            respondToCall(callDetails, response)
        }
    }

    private suspend fun screenNumber(
        phoneNumber: String,
        callDetails: Call.Details
    ): CallResponse {

        // 1. Personal allowlist — user explicitly trusts this number
        if (repository.isAllowlisted(phoneNumber)) {
            Log.d(TAG, "Allowlisted, allowing: $phoneNumber")
            repository.logCall(phoneNumber, source = "allowlist", outcome = "allowed")
            return buildAllowResponse()
        }

        // 2. Android network spam flag (STIR/SHAKEN)
        //    The carrier sets callerNumberVerificationStatus = 2 when it flags a call
        //    as suspicious/spoofed. This is what produces the "Spam Risk" label in
        //    the dialer. The raw value 2 == Call.Details.VERIFICATION_STATUS_FAILED.
        if (callDetails.callerNumberVerificationStatus == VERIFICATION_STATUS_FAILED) {
            Log.d(TAG, "Android Spam Risk flag set, blocking: $phoneNumber")
            repository.logCall(phoneNumber, source = "android_spam_risk", outcome = "blocked")
            return buildBlockResponse()
        }

        // 3. Personal blocklist — user manually blocked this number
        if (repository.isBlocklisted(phoneNumber)) {
            Log.d(TAG, "On personal blocklist, blocking: $phoneNumber")
            repository.logCall(phoneNumber, source = "personal_blocklist", outcome = "blocked")
            return buildBlockResponse()
        }

        // 4. Local spam database (FTC DNC complaint data)
        val spamEntry = repository.lookupSpamDatabase(phoneNumber)
        if (spamEntry != null) {
            Log.d(TAG, "Found in spam DB (${spamEntry.category}), blocking: $phoneNumber")
            repository.logCall(phoneNumber, source = "spam_database", outcome = "blocked", category = spamEntry.category)
            return buildBlockResponse()
        }

        // 5. Block unknown numbers toggle
        //    If enabled, any number that is not in the user's device contacts
        //    is silently sent to voicemail. The user can listen to voicemail
        //    and decide whether to block or allowlist the number.
        //    PhoneLookup is indexed — lookup is sub-millisecond even for large contact lists.
        val inContacts = repository.isInContacts(phoneNumber)
        if (settings.blockUnknownNumbers && !inContacts) {
            Log.d(TAG, "Block unknown toggle ON and not in contacts, sending to voicemail: $phoneNumber")
            repository.logCall(phoneNumber, source = "unknown_number", outcome = "voicemail")
            return buildVoicemailResponse()
        }

        // 6. Default — allow; distinguish known contacts from cold unknowns in the log
        val allowSource = if (inContacts) "contact" else "default_allow"
        Log.d(TAG, "No match found, allowing: $phoneNumber (source=$allowSource)")
        repository.logCall(phoneNumber, source = allowSource, outcome = "allowed")
        return buildAllowResponse()
    }

    // -------------------------------------------------------------------------
    // Response builders
    // -------------------------------------------------------------------------

    private fun buildAllowResponse(): CallResponse =
        CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSilenceCall(false)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build()

    /**
     * Hard block — call is rejected immediately with no voicemail.
     * Used for confirmed spam: Android flag, personal blocklist, FTC spam DB.
     *
     * setDisallowCall(true) + setRejectCall(false) = hang up, no voicemail.
     * setRejectCall(true) would route to voicemail — intentionally NOT set here.
     */
    private fun buildBlockResponse(): CallResponse =
        CallResponse.Builder()
            .setDisallowCall(true)
            .setRejectCall(false)      // do NOT send to voicemail
            .setSilenceCall(true)
            .setSkipCallLog(false)     // still appears in call log
            .setSkipNotification(false)
            .build()

    /**
     * Soft block — caller is sent to voicemail silently.
     * Used for the "block unknown numbers" toggle so the user can review
     * voicemails and decide what to do with the number.
     *
     * Identical behaviour to [buildBlockResponse] but semantically distinct —
     * this is "unknown, not confirmed spam" rather than "confirmed spam".
     */
    private fun buildVoicemailResponse(): CallResponse =
        CallResponse.Builder()
            .setDisallowCall(true)
            .setRejectCall(true)       // routes to voicemail
            .setSilenceCall(true)
            .setSkipCallLog(false)     // show in call log so user knows someone called
            .setSkipNotification(false)
            .build()

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        Log.d(TAG, "SpamCallScreeningService destroyed")
    }

    companion object {
        // Equivalent to Call.Details.VERIFICATION_STATUS_FAILED (API 30+).
        // Defined locally to avoid resolution issues across SDK versions.
        // Value is stable and documented in the Android platform source.
        private const val VERIFICATION_STATUS_FAILED = 2
    }
}
