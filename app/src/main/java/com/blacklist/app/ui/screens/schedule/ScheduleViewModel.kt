package com.blacklist.app.ui.screens.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blacklist.app.data.local.entity.ScheduleRuleEntity
import com.blacklist.app.domain.repository.BlackListRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ScheduleViewModel(private val repo: BlackListRepository): ViewModel() {
    val rules = repo.observeScheduleRules().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun add(rule: ScheduleRuleEntity) = viewModelScope.launch { repo.addScheduleRule(rule) }
    fun update(rule: ScheduleRuleEntity) = viewModelScope.launch { repo.updateScheduleRule(rule) }
    fun delete(rule: ScheduleRuleEntity) = viewModelScope.launch { repo.deleteScheduleRule(rule) }
    fun toggle(rule: ScheduleRuleEntity) = viewModelScope.launch { repo.updateScheduleRule(rule.copy(isEnabled = !rule.isEnabled)) }
    fun addException(scheduleRuleId: Long, number: String) = viewModelScope.launch {
        repo.addScheduleException(scheduleRuleId, number)
    }
    fun deleteException(id: Long) = viewModelScope.launch { repo.deleteScheduleException(id) }
}
