package com.example.spam_decliner_9000.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.spam_decliner_9000.data.model.ListType
import com.example.spam_decliner_9000.data.model.UserListEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface UserListDao {

    @Query("SELECT EXISTS(SELECT 1 FROM user_list WHERE phoneNumber = :phoneNumber AND listType = 'ALLOW')")
    suspend fun isAllowlisted(phoneNumber: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM user_list WHERE phoneNumber = :phoneNumber AND listType = 'BLOCK')")
    suspend fun isBlocklisted(phoneNumber: String): Boolean

    /** Used for manual adds — silently skips if the number already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: UserListEntry)

    /**
     * Used by the contacts sync — replaces existing rows so that the
     * contactName field is always kept up to date.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContact(entry: UserListEntry)

    @Query("DELETE FROM user_list WHERE phoneNumber = :phoneNumber AND listType = :listType")
    suspend fun delete(phoneNumber: String, listType: ListType)

    @Query("SELECT * FROM user_list WHERE listType = 'BLOCK' ORDER BY addedAtMs DESC")
    fun observeBlocklist(): Flow<List<UserListEntry>>

    @Query("SELECT * FROM user_list WHERE listType = 'ALLOW' ORDER BY addedAtMs DESC")
    fun observeAllowlist(): Flow<List<UserListEntry>>
}
