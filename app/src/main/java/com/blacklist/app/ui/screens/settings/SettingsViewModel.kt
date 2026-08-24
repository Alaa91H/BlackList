package com.blacklist.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blacklist.app.domain.repository.BlackListRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repo: BlackListRepository): ViewModel() {
    val settings = repo.observeSettings().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    fun setBlockUnknown(v:Boolean)=viewModelScope.launch{repo.updateSettings{it.copy(blockUnknown=v)}}
    fun setBlockPrivate(v:Boolean)=viewModelScope.launch{repo.updateSettings{it.copy(blockPrivate=v)}}
    fun setBlockAllExcept(v:Boolean)=viewModelScope.launch{repo.updateSettings{it.copy(blockAllExceptWhitelist=v)}}
    fun setNotifications(v:Boolean)=viewModelScope.launch{repo.updateSettings{it.copy(showBlockedNotification=v)}}
    fun setTheme(m:String)=viewModelScope.launch{repo.updateSettings{it.copy(themeMode=m)}}
}
