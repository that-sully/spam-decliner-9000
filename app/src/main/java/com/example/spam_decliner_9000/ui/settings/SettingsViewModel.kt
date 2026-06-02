package com.example.spam_decliner_9000.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.spam_decliner_9000.data.AppSettings
import com.example.spam_decliner_9000.data.db.SpamNumberDao
import com.example.spam_decliner_9000.worker.SpamSyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: AppSettings,
    private val spamNumberDao: SpamNumberDao,
    private val workManager: WorkManager
) : ViewModel() {

    val blockUnknownNumbers = settings.observeBlockUnknownNumbers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val spamDatabaseCount = flow {
        emit(spamNumberDao.count())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun setBlockUnknownNumbers(enabled: Boolean) {
        settings.blockUnknownNumbers = enabled
    }

    fun syncNow() = viewModelScope.launch {
        val request = OneTimeWorkRequestBuilder<SpamSyncWorker>().build()
        workManager.enqueue(request)
    }
}
