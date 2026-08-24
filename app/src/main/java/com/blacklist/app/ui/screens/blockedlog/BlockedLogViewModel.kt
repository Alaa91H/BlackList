package com.blacklist.app.ui.screens.blockedlog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blacklist.app.domain.repository.BlackListRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BlockedLogViewModel(private val repo: BlackListRepository): ViewModel() {
    val logs = repo.observeBlockedLogs().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun clear() = viewModelScope.launch { repo.clearLogs() }
}
