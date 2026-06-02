package com.example.spam_decliner_9000.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.spam_decliner_9000.data.model.BlockedCallEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface CallLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: BlockedCallEntry)

    @Query("SELECT * FROM blocked_call_log ORDER BY blockedAtMs DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<BlockedCallEntry>

    @Query("SELECT * FROM blocked_call_log ORDER BY blockedAtMs DESC")
    fun observeAll(): Flow<List<BlockedCallEntry>>

    @Query("DELETE FROM blocked_call_log WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) > 0 FROM blocked_call_log WHERE phoneNumber = :phoneNumber AND outcome = 'outgoing'")
    suspend fun hasOutgoingCallTo(phoneNumber: String): Boolean

    @Query("DELETE FROM blocked_call_log")
    suspend fun clearAll()
}
