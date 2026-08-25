package com.blacklist.app.ui.screens.whitelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blacklist.app.domain.repository.BlackListRepository
import com.blacklist.app.util.PickerItem
import com.blacklist.app.util.PhoneNumberUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class WhitelistViewModel(private val repo: BlackListRepository): ViewModel() {
    val items = repo.observeWhitelisted().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query
    val filtered = combine(items, _query) { list, q ->
        if (q.isBlank()) list else list.filter { it.rawNumber.contains(q,true) || it.displayName?.contains(q,true)==true }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    fun setQuery(v:String){_query.value=v}
    fun add(n:String, name:String?) = viewModelScope.launch {
        val r = repo.addWhitelisted(n, name?.takeIf{it.isNotBlank()})
        _error.value = if(r.isFailure) r.exceptionOrNull()?.message else null
    }

    fun addAll(selected: List<PickerItem>) = viewModelScope.launch {
        var added = 0
        var skipped = 0
        selected.distinctBy { PhoneNumberUtils.normalize(it.number) ?: it.number }.forEach { item ->
            val result = repo.addWhitelisted(item.number, item.name?.takeIf { it.isNotBlank() })
            if (result.isSuccess) added++ else skipped++
        }
        _error.value = when {
            skipped == 0 -> null
            added == 0 -> "All selected numbers are already in the whitelist."
            else -> "$added numbers added; $skipped already existed."
        }
    }
    fun remove(id:Long)=viewModelScope.launch{repo.removeWhitelisted(id)}
    fun clearError(){_error.value=null}
}
