package com.blacklist.app.ui.screens.home

import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import android.telecom.TelecomManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blacklist.app.domain.model.ProtectionProfiles
import com.blacklist.app.domain.repository.BlackListRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repo: BlackListRepository,
    private val ctx: Context
) : ViewModel() {

    val settings = repo.observeSettings().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val blockedCount = repo.observeBlockedCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val blockedLogs = repo.observeBlockedLogs().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val blockedNumbers = repo.observeBlockedNumbers().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _todayCount = MutableStateFlow(0)
    val todayCount: StateFlow<Int> = _todayCount

    init {
        viewModelScope.launch {
            blockedLogs.collect { logs ->
                val startOfDay = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
                }.timeInMillis
                _todayCount.value = logs.count { it.timestamp >= startOfDay }
            }
        }
    }

    fun isRoleHeld(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = ctx.getSystemService(RoleManager::class.java)
            rm?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
        } else {
            val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            tm.defaultDialerPackage == ctx.packageName
        }
    }

    fun toggleBlockUnknown(v: Boolean) = viewModelScope.launch {
        repo.updateSettings { it.copy(blockUnknown = v, activeProfileId = ProtectionProfiles.CUSTOM) }
    }
    fun toggleBlockPrivate(v: Boolean) = viewModelScope.launch {
        repo.updateSettings { it.copy(blockPrivate = v, activeProfileId = ProtectionProfiles.CUSTOM) }
    }
    fun toggleBlockAllExceptWhitelist(v: Boolean) = viewModelScope.launch {
        repo.updateSettings { it.copy(blockAllExceptWhitelist = v, activeProfileId = ProtectionProfiles.CUSTOM) }
    }
    fun toggleNotifications(v: Boolean) = viewModelScope.launch { repo.updateSettings { it.copy(showBlockedNotification = v) } }

    // Temporary firewall
    val blacklistRules = repo.observeBlacklistRules().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun enableTempBlockAll(durationMs: Long) = viewModelScope.launch {
        repo.cleanupExpiredTemporaryRules()
        repo.enableTemporaryBlockAll(durationMs)
    }
    fun cancelTempBlockAll() = viewModelScope.launch { repo.cancelTemporaryBlockAll() }
    fun cleanupExpired() = viewModelScope.launch { repo.cleanupExpiredTemporaryRules() }
}
