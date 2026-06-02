package com.example.spam_decliner_9000.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocked_call_log")
data class BlockedCallEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phoneNumber: String,
    val blockedAtMs: Long = System.currentTimeMillis(),
    // source: "android_spam_risk" | "personal_blocklist" | "spam_database" |
    //         "unknown_number" | "allowlist" | "contact" | "default_allow"
    val source: String,
    val category: String? = null,
    // outcome: "blocked" | "voicemail" | "allowed"
    val outcome: String = "blocked"
)
