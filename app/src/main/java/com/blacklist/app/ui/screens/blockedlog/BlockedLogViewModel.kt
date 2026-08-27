package com.blacklist.app.ui.screens.blockedlog

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blacklist.app.domain.repository.BlackListRepository
import com.blacklist.app.widget.BlockedCallStatsWidgetProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BlockedLogViewModel(private val repo: BlackListRepository): ViewModel() {
    val logs = repo.observeBlockedLogs().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun clear(context: Context) = viewModelScope.launch {
        repo.clearLogs()
        BlockedCallStatsWidgetProvider.refreshAll(context.applicationContext)
    }
    fun alwaysAllow(number: String, displayName: String?, onDone: (Boolean) -> Unit = {}) = viewModelScope.launch {
        onDone(repo.addWhitelisted(number, displayName).isSuccess)
    }
    fun temporaryAllow(number: String, durationMs: Long) = viewModelScope.launch {
        repo.addTemporaryAllow(number, durationMs)
    }
    fun cleanupExpiredTemporaryRules() = viewModelScope.launch { repo.cleanupExpiredTemporaryRules() }
}
