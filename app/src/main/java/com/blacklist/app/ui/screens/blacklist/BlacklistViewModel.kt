package com.blacklist.app.ui.screens.blacklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blacklist.app.data.local.entity.BlacklistRuleEntity
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

    // Pattern rules (PREFIX/SUFFIX/CONTAINS/RANGE/COUNTRY/EXACT)
    val rules = repo.observeBlacklistRules().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val filteredRules = combine(rules, _query) { list, q ->
        if (q.isBlank()) list else list.filter {
            it.pattern?.contains(q, true) == true ||
                it.startNumber?.contains(q, true) == true ||
                it.endNumber?.contains(q, true) == true ||
                it.countryIso?.contains(q, true) == true ||
                it.ruleType.contains(q, true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun setQuery(v: String) { _query.value = v }
    fun add(number: String, name: String?) = viewModelScope.launch {
        val res = repo.addBlockedNumber(number, name?.takeIf { it.isNotBlank() })
        if (res.isFailure) _error.value = res.exceptionOrNull()?.message ?: "Error"
        else _error.value = null
    }

    fun addRule(rule: BlacklistRuleEntity) = viewModelScope.launch {
        val res = repo.addBlacklistRule(rule)
        if (res.isFailure) _error.value = res.exceptionOrNull()?.message ?: "Error"
        else _error.value = null
    }
    fun removeRule(id: Long) = viewModelScope.launch { repo.deleteBlacklistRule(id) }
    fun toggleRule(id: Long, enabled: Boolean) = viewModelScope.launch { repo.setBlacklistRuleEnabled(id, enabled) }

    fun remove(id: Long) = viewModelScope.launch { repo.removeBlockedNumber(id) }
    fun clearError() { _error.value = null }
    fun toggleNotification(id: Long, enabled: Boolean) = viewModelScope.launch { repo.setBlockedNotificationEnabled(id, enabled) }
}
