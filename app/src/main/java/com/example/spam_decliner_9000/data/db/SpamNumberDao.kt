package com.example.spam_decliner_9000.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.spam_decliner_9000.data.model.SpamEntry

@Dao
interface SpamNumberDao {

    @Query("SELECT * FROM spam_numbers WHERE phoneNumber = :phoneNumber LIMIT 1")
    suspend fun findByNumber(phoneNumber: String): SpamEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: SpamEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<SpamEntry>)

    @Query("DELETE FROM spam_numbers WHERE phoneNumber = :phoneNumber")
    suspend fun delete(phoneNumber: String)

    @Query("SELECT COUNT(*) FROM spam_numbers")
    suspend fun count(): Int
}
