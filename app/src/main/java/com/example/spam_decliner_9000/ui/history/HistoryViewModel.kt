package com.example.spam_decliner_9000.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spam_decliner_9000.data.SpamRepository
import com.example.spam_decliner_9000.data.model.BlockedCallEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: SpamRepository
) : ViewModel() {

    val calls = repository.observeBlockedCallHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addToBlocklist(entry: BlockedCallEntry) = viewModelScope.launch {
        repository.addToBlocklist(entry.phoneNumber)
    }

    fun addToAllowlist(entry: BlockedCallEntry) = viewModelScope.launch {
        repository.addToAllowlist(entry.phoneNumber)
    }
}
