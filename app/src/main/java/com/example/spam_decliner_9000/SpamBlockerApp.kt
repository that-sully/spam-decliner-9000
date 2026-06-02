package com.example.spam_decliner_9000

import android.app.Application
import android.util.Log
import com.example.spam_decliner_9000.worker.ContactsSyncWorker
import com.example.spam_decliner_9000.worker.SpamSyncWorker
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SpamBlockerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d("SpamBlockerApp", "Application starting — scheduling background workers")
        SpamSyncWorker.schedule(this)
        ContactsSyncWorker.schedule(this) // runs every 24h; skips silently if no permission yet
    }
}
