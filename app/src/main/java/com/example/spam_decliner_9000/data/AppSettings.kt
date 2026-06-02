package com.example.spam_decliner_9000.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin SharedPreferences wrapper for app-level settings.
 *
 * All settings are exposed as both direct read/write accessors (for use inside
 * [CallScreeningService] where coroutines aren't needed) and as [Flow]s for
 * the UI layer to observe reactively.
 */
@Singleton
class AppSettings @Inject constructor(@ApplicationContext context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "spam_blocker_settings",
        Context.MODE_PRIVATE
    )

    // -------------------------------------------------------------------------
    // Block unknown numbers toggle
    // -------------------------------------------------------------------------

    /**
     * When true, any incoming call from a number that is not in the user's
     * personal allowlist or device contacts is silently sent to voicemail.
     *
     * The user can then listen to the voicemail to decide whether to add the
     * number to their blocklist or allowlist.
     */
    var blockUnknownNumbers: Boolean
        get() = prefs.getBoolean(KEY_BLOCK_UNKNOWN, false)
        set(value) = prefs.edit { putBoolean(KEY_BLOCK_UNKNOWN, value) }

    /**
     * Emits the current value of [blockUnknownNumbers] and any future changes,
     * allowing Compose UI to react without polling.
     */
    fun observeBlockUnknownNumbers(): Flow<Boolean> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_BLOCK_UNKNOWN) trySend(blockUnknownNumbers)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(blockUnknownNumbers) // emit current value immediately
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    companion object {
        private const val KEY_BLOCK_UNKNOWN = "block_unknown_numbers"

        @Volatile private var INSTANCE: AppSettings? = null

        fun getInstance(context: Context): AppSettings =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppSettings(context.applicationContext).also { INSTANCE = it }
            }
    }
}
