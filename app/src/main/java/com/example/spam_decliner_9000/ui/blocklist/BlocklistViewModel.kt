package com.example.spam_decliner_9000.ui.blocklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spam_decliner_9000.data.SpamRepository
import com.example.spam_decliner_9000.data.model.UserListEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BlocklistViewModel @Inject constructor(
    private val repository: SpamRepository
) : ViewModel() {

    val blocklist = repository.observeBlocklist()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allowlist = repository.observeAllowlist()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addToBlocklist(number: String) = viewModelScope.launch {
        repository.addToBlocklist(number.trim())
    }

    fun removeFromBlocklist(entry: UserListEntry) = viewModelScope.launch {
        repository.removeFromBlocklist(entry.phoneNumber)
    }

    fun addToAllowlist(number: String) = viewModelScope.launch {
        repository.addToAllowlist(number.trim())
    }

    fun removeFromAllowlist(entry: UserListEntry) = viewModelScope.launch {
        repository.removeFromAllowlist(entry.phoneNumber)
    }
}
