package com.blacklist.app.ui.screens.simulator

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blacklist.app.di.ServiceLocator
import com.blacklist.app.domain.model.CallEvent
import com.blacklist.app.domain.model.CallSource
import com.blacklist.app.domain.model.EnforcementDecision
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DecisionSimulatorState(
    val isEvaluating: Boolean = false,
    val result: EnforcementDecision? = null,
    val error: String? = null
)

/**
 * Read-only policy simulator. It evaluates against the current immutable
 * in-memory policy snapshot but never calls Telecom, writes a log, or sends a notification.
 */
class DecisionSimulatorViewModel(context: Context) : ViewModel() {
    private val applicationContext = context.applicationContext
    private val normalizer = ServiceLocator.provideNormalizer(applicationContext)
    private val firewall = ServiceLocator.provideFirewallEngine(applicationContext)

    private val _state = MutableStateFlow(DecisionSimulatorState())
    val state: StateFlow<DecisionSimulatorState> = _state.asStateFlow()

    fun simulate(rawNumber: String) {
        _state.value = DecisionSimulatorState(isEvaluating = true)
        viewModelScope.launch {
            runCatching {
                val phoneNumber = normalizer.normalize(rawNumber)
                firewall.evaluate(
                    CallEvent(
                        callId = "simulator-${System.nanoTime()}",
                        phoneNumber = phoneNumber,
                        source = CallSource.TEST
                    )
                )
            }.onSuccess { decision ->
                _state.value = DecisionSimulatorState(result = decision)
            }.onFailure { error ->
                _state.value = DecisionSimulatorState(error = error.message ?: "Unable to simulate this number.")
            }
        }
    }

    fun clear() {
        _state.value = DecisionSimulatorState()
    }
}
