package com.blacklist.app.ui.screens.sharednumber

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blacklist.app.domain.engine.TemporaryExactBlockPolicy
import com.blacklist.app.domain.model.Presentation
import com.blacklist.app.domain.normalization.PhoneNumberNormalizer
import com.blacklist.app.domain.repository.BlackListRepository
import com.blacklist.app.domain.sharing.SharedPhoneNumberExtractor
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SharedNumberAction {
    BLOCK,
    ALLOW,
    TEMPORARY_BLOCK
}

sealed interface SharedNumberEvent {
    data object Applied : SharedNumberEvent
    data object InvalidSelection : SharedNumberEvent
    data object Failed : SharedNumberEvent
}

/**
 * Owns an explicitly shared text payload only for the current UI session.
 * The raw payload is never persisted; only a user-confirmed canonical number can be stored.
 */
class SharedNumberViewModel(
    private val repo: BlackListRepository,
    private val normalizer: PhoneNumberNormalizer
) : ViewModel() {
    private val _candidates = MutableStateFlow<List<String>>(emptyList())
    val candidates = _candidates.asStateFlow()

    private val _events = MutableSharedFlow<SharedNumberEvent>()
    val events = _events.asSharedFlow()

    fun loadSharedText(sharedText: CharSequence?) {
        _candidates.value = SharedPhoneNumberExtractor.extract(sharedText)
            .mapNotNull { raw ->
                val number = normalizer.normalize(raw)
                val e164 = number.e164 ?: return@mapNotNull null
                val digits = e164.removePrefix("+")
                e164.takeIf {
                    number.presentation == Presentation.ALLOWED &&
                        TemporaryExactBlockPolicy.isValidE164Digits(digits) &&
                        !normalizer.isEmergencyNumber(number)
                }
            }
            .distinct()
            .take(SharedPhoneNumberExtractor.MAX_CANDIDATES)
    }

    fun apply(number: String, action: SharedNumberAction, durationMs: Long? = null) = viewModelScope.launch {
        if (number !in candidates.value) {
            _events.emit(SharedNumberEvent.InvalidSelection)
            return@launch
        }
        val result = when (action) {
            SharedNumberAction.BLOCK -> repo.addBlockedNumber(number, null)
            SharedNumberAction.ALLOW -> repo.addWhitelisted(number, null)
            SharedNumberAction.TEMPORARY_BLOCK -> {
                if (durationMs == null || !TemporaryExactBlockPolicy.isSupportedDuration(durationMs)) {
                    Result.failure(IllegalArgumentException("Unsupported temporary block duration."))
                } else {
                    repo.addTemporaryExactBlock(number, durationMs)
                }
            }
        }
        _events.emit(if (result.isSuccess) SharedNumberEvent.Applied else SharedNumberEvent.Failed)
    }
}
