package com.example.spam_decliner_9000.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "spam_numbers",
    indices = [Index(value = ["phoneNumber"], unique = true)]
)
data class SpamEntry(
    @PrimaryKey val phoneNumber: String,
    val category: String,       // "robocall", "telemarketer", "scam", "unknown"
    val reportCount: Int = 0,
    val confidence: Float = 1.0f,
    val addedAtMs: Long = System.currentTimeMillis()
)
