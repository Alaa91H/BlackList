package com.blacklist.app.ui.screens.whitelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blacklist.app.domain.repository.BlackListRepository
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
    fun remove(id:Long)=viewModelScope.launch{repo.removeWhitelisted(id)}
    fun clearError(){_error.value=null}
}
