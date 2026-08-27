package com.blacklist.app.ui.screens.blockedlog

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blacklist.app.domain.engine.ReputationEngine
import com.blacklist.app.domain.model.Presentation
import com.blacklist.app.domain.model.UserVerdict
import com.blacklist.app.domain.normalization.PhoneNumberNormalizer
import com.blacklist.app.domain.repository.BlackListRepository
import com.blacklist.app.widget.BlockedCallStatsWidgetProvider
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface BlockedLogRecoveryEvent {
    data object MarkedNotSpam : BlockedLogRecoveryEvent
    data object InvalidNumber : BlockedLogRecoveryEvent
    data object Failed : BlockedLogRecoveryEvent
}

class BlockedLogViewModel(
    private val repo: BlackListRepository,
    private val normalizer: PhoneNumberNormalizer,
    private val reputationEngine: ReputationEngine
) : ViewModel() {
    val logs = repo.observeBlockedLogs().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _recoveryEvents = MutableSharedFlow<BlockedLogRecoveryEvent>()
    val recoveryEvents = _recoveryEvents.asSharedFlow()

    fun clear(context: Context) = viewModelScope.launch {
        repo.clearLogs()
        BlockedCallStatsWidgetProvider.refreshAll(context.applicationContext)
    }

    fun alwaysAllow(number: String, displayName: String?, onDone: (Boolean) -> Unit = {}) = viewModelScope.launch {
        onDone(repo.addWhitelisted(number, displayName).isSuccess)
    }

    /**
     * Records a durable local NOT_SPAM verdict for a real dialable caller.
     * This does not add the caller to the whitelist or weaken broad policies;
     * it only makes the user's verdict authoritative over imported reputation data.
     */
    fun markNotSpam(rawNumber: String) = viewModelScope.launch {
        val number = normalizer.normalize(rawNumber)
        val digitCount = number.normalized.count(Char::isDigit)
        if (number.presentation != Presentation.ALLOWED || digitCount !in 3..32) {
            _recoveryEvents.emit(BlockedLogRecoveryEvent.InvalidNumber)
            return@launch
        }

        runCatching {
            reputationEngine.setUserVerdict(number.normalized, UserVerdict.NOT_SPAM)
        }.onSuccess {
            _recoveryEvents.emit(BlockedLogRecoveryEvent.MarkedNotSpam)
        }.onFailure {
            _recoveryEvents.emit(BlockedLogRecoveryEvent.Failed)
        }
    }

    fun temporaryAllow(number: String, durationMs: Long) = viewModelScope.launch {
        repo.addTemporaryAllow(number, durationMs)
    }

    fun cleanupExpiredTemporaryRules() = viewModelScope.launch { repo.cleanupExpiredTemporaryRules() }
}
