package com.example.spam_decliner_9000.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.spam_decliner_9000.data.model.BlockedCallEntry
import com.example.spam_decliner_9000.data.model.SpamEntry
import com.example.spam_decliner_9000.data.model.UserListEntry

@Database(
    entities = [SpamEntry::class, UserListEntry::class, BlockedCallEntry::class],
    version = 3,
    exportSchema = false
)
abstract class SpamDatabase : RoomDatabase() {

    abstract fun spamNumberDao(): SpamNumberDao
    abstract fun userListDao(): UserListDao
    abstract fun callLogDao(): CallLogDao

    companion object {
        @Volatile private var INSTANCE: SpamDatabase? = null

        /** Adds the contactName column introduced in version 2. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE user_list ADD COLUMN contactName TEXT"
                )
            }
        }

        /** Adds the outcome column introduced in version 3. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE blocked_call_log ADD COLUMN outcome TEXT NOT NULL DEFAULT 'blocked'"
                )
            }
        }

        fun getInstance(context: Context): SpamDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SpamDatabase::class.java,
                    "spam_blocker.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
