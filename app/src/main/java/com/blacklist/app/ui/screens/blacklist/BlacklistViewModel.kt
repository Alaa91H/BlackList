package com.blacklist.app.ui.screens.blacklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blacklist.app.data.local.entity.BlacklistRuleEntity
import com.blacklist.app.domain.engine.DraftRuleDecisionPreviewer
import com.blacklist.app.domain.engine.TemporaryFirewall
import com.blacklist.app.domain.model.EnforcementDecision
import com.blacklist.app.domain.repository.BlackListRepository
import com.blacklist.app.util.PickerItem
import com.blacklist.app.util.PhoneNumberUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface TemporaryExactBlockEvent {
    data object Added : TemporaryExactBlockEvent
    data object InvalidNumber : TemporaryExactBlockEvent
    data object LimitReached : TemporaryExactBlockEvent
    data object Failed : TemporaryExactBlockEvent
}

sealed interface DraftRulePreviewError {
    data object InvalidNumber : DraftRulePreviewError
    data object Failed : DraftRulePreviewError
}

data class DraftRulePreviewState(
    val isPreviewing: Boolean = false,
    val result: EnforcementDecision? = null,
    val error: DraftRulePreviewError? = null
)

class BlacklistViewModel(
    private val repo: BlackListRepository,
    private val draftPreviewer: DraftRuleDecisionPreviewer
) : ViewModel() {
    val items = repo.observeBlockedNumbers().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query
    val filtered = combine(items, _query) { list, q ->
        if (q.isBlank()) list else list.filter { it.rawNumber.contains(q, true) || it.displayName?.contains(q, true) == true }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Pattern rules (PREFIX/SUFFIX/CONTAINS/RANGE/COUNTRY/EXACT)
    val rules = repo.observeBlacklistRules().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val temporaryExactBlocks = rules.map { list ->
        list.filter {
            it.ruleType == BlacklistRuleEntity.TYPE_TEMP_BLOCK_EXACT &&
                TemporaryFirewall.isActive(it)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val filteredRules = combine(rules, _query) { list, q ->
        list.filter { !TemporaryFirewall.isTempType(it.ruleType) }.let { persistentRules ->
            if (q.isBlank()) persistentRules else persistentRules.filter {
                it.pattern?.contains(q, true) == true ||
                    it.startNumber?.contains(q, true) == true ||
                    it.endNumber?.contains(q, true) == true ||
                    it.countryIso?.contains(q, true) == true ||
                    it.ruleType.contains(q, true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _temporaryExactBlockEvents = MutableSharedFlow<TemporaryExactBlockEvent>()
    val temporaryExactBlockEvents = _temporaryExactBlockEvents.asSharedFlow()

    private val _draftRulePreview = MutableStateFlow(DraftRulePreviewState())
    val draftRulePreview: StateFlow<DraftRulePreviewState> = _draftRulePreview.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun setQuery(v: String) { _query.value = v }
    fun add(number: String, name: String?) = viewModelScope.launch {
        val res = repo.addBlockedNumber(number, name?.takeIf { it.isNotBlank() })
        if (res.isFailure) _error.value = res.exceptionOrNull()?.message ?: "Error"
        else _error.value = null
    }

    fun addAll(selected: List<PickerItem>) = viewModelScope.launch {
        var added = 0
        var skipped = 0
        selected.distinctBy { PhoneNumberUtils.normalize(it.number) ?: it.number }.forEach { item ->
            val result = repo.addBlockedNumber(item.number, item.name?.takeIf { it.isNotBlank() })
            if (result.isSuccess) added++ else skipped++
        }
        _error.value = when {
            skipped == 0 -> null
            added == 0 -> "All selected numbers are already in the blacklist."
            else -> "$added numbers added; $skipped already existed."
        }
    }

    init {
        viewModelScope.launch { repo.cleanupExpiredTemporaryRules() }
    }

    fun addTemporaryExactBlock(number: String, durationMs: Long) = viewModelScope.launch {
        val result = repo.addTemporaryExactBlock(number, durationMs)
        val event = when (result.exceptionOrNull()) {
            null -> TemporaryExactBlockEvent.Added
            is IllegalArgumentException -> TemporaryExactBlockEvent.InvalidNumber
            is IllegalStateException -> TemporaryExactBlockEvent.LimitReached
            else -> TemporaryExactBlockEvent.Failed
        }
        _temporaryExactBlockEvents.emit(event)
    }

    fun cancelTemporaryExactBlock(id: Long) = viewModelScope.launch {
        repo.cancelTemporaryExactBlock(id)
    }

    /** Runs only after the user presses Preview; no rule or event is persisted. */
    fun previewDraftRule(rule: BlacklistRuleEntity, rawNumber: String) = viewModelScope.launch {
        _draftRulePreview.value = DraftRulePreviewState(isPreviewing = true)
        runCatching { draftPreviewer.preview(rawNumber, rule) }
            .onSuccess { decision -> _draftRulePreview.value = DraftRulePreviewState(result = decision) }
            .onFailure { error ->
                _draftRulePreview.value = DraftRulePreviewState(
                    error = if (error is IllegalArgumentException) {
                        DraftRulePreviewError.InvalidNumber
                    } else {
                        DraftRulePreviewError.Failed
                    }
                )
            }
    }

    /** Clears the current session-only test number result when the editor closes or changes. */
    fun clearDraftRulePreview() {
        _draftRulePreview.value = DraftRulePreviewState()
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
