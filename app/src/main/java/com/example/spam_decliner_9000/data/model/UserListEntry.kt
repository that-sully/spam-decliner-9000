package com.example.spam_decliner_9000.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ListType { ALLOW, BLOCK }

@Entity(
    tableName = "user_list",
    indices = [Index(value = ["phoneNumber", "listType"], unique = true)]
)
data class UserListEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phoneNumber: String,
    val listType: ListType,
    val contactName: String? = null,  // display name from device contacts, if available
    val note: String? = null,
    val addedAtMs: Long = System.currentTimeMillis()
)
