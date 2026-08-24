package com.blacklist.app.ui.screens.blacklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blacklist.app.domain.repository.BlackListRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class BlacklistViewModel(private val repo: BlackListRepository): ViewModel() {
    val items = repo.observeBlockedNumbers().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query
    val filtered = combine(items, _query) { list, q ->
        if (q.isBlank()) list else list.filter { it.rawNumber.contains(q, true) || it.displayName?.contains(q,true)==true }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun setQuery(v: String) { _query.value = v }
    fun add(number: String, name: String?) = viewModelScope.launch {
        val res = repo.addBlockedNumber(number, name?.takeIf { it.isNotBlank() })
        if (res.isFailure) _error.value = res.exceptionOrNull()?.message ?: "Error"
        else _error.value = null
    }
    fun remove(id: Long) = viewModelScope.launch { repo.removeBlockedNumber(id) }
    fun clearError() { _error.value = null }
    fun toggleNotification(id: Long, enabled: Boolean) = viewModelScope.launch { repo.setBlockedNotificationEnabled(id, enabled) }
}
