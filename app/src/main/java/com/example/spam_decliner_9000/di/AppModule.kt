package com.example.spam_decliner_9000.di

import android.content.Context
import androidx.work.WorkManager
import com.example.spam_decliner_9000.data.AppSettings
import com.example.spam_decliner_9000.data.SpamRepository
import com.example.spam_decliner_9000.data.db.CallLogDao
import com.example.spam_decliner_9000.data.db.SpamDatabase
import com.example.spam_decliner_9000.data.db.SpamNumberDao
import com.example.spam_decliner_9000.data.db.UserListDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSpamDatabase(@ApplicationContext context: Context): SpamDatabase =
        SpamDatabase.getInstance(context)

    @Provides
    @Singleton
    fun provideSpamNumberDao(db: SpamDatabase): SpamNumberDao = db.spamNumberDao()

    @Provides
    @Singleton
    fun provideUserListDao(db: SpamDatabase): UserListDao = db.userListDao()

    @Provides
    @Singleton
    fun provideCallLogDao(db: SpamDatabase): CallLogDao = db.callLogDao()

    @Provides
    @Singleton
    fun provideSpamRepository(
        @ApplicationContext context: Context,
        spamNumberDao: SpamNumberDao,
        userListDao: UserListDao,
        callLogDao: CallLogDao
    ): SpamRepository = SpamRepository(context, spamNumberDao, userListDao, callLogDao)

    @Provides
    @Singleton
    fun provideAppSettings(@ApplicationContext context: Context): AppSettings =
        AppSettings.getInstance(context)

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)
}
