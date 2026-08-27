package com.blacklist.app.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.blacklist.app.domain.repository.BlackListRepository
import com.blacklist.app.ui.screens.blacklist.BlacklistViewModel
import com.blacklist.app.ui.screens.blockedlog.BlockedLogViewModel
import com.blacklist.app.ui.screens.home.HomeViewModel
import com.blacklist.app.ui.screens.schedule.ScheduleViewModel
import com.blacklist.app.ui.screens.sharednumber.SharedNumberViewModel
import com.blacklist.app.ui.screens.simulator.DecisionSimulatorViewModel
import com.blacklist.app.ui.screens.settings.SettingsViewModel
import com.blacklist.app.ui.screens.whitelist.WhitelistViewModel

@Suppress("UNCHECKED_CAST")
class ViewModelFactory(
    private val repo: BlackListRepository,
    private val appContext: android.content.Context? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> HomeViewModel(repo, appContext!!) as T
            modelClass.isAssignableFrom(BlacklistViewModel::class.java) -> BlacklistViewModel(repo) as T
            modelClass.isAssignableFrom(WhitelistViewModel::class.java) -> WhitelistViewModel(repo) as T
            modelClass.isAssignableFrom(BlockedLogViewModel::class.java) -> BlockedLogViewModel(
                repo = repo,
                normalizer = ServiceLocator.provideNormalizer(appContext!!),
                reputationEngine = ServiceLocator.provideReputationEngine(appContext!!)
            ) as T
            modelClass.isAssignableFrom(ScheduleViewModel::class.java) -> ScheduleViewModel(repo) as T
            modelClass.isAssignableFrom(SharedNumberViewModel::class.java) -> SharedNumberViewModel(
                repo = repo,
                normalizer = ServiceLocator.provideNormalizer(appContext!!)
            ) as T
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(repo) as T
            modelClass.isAssignableFrom(DecisionSimulatorViewModel::class.java) -> DecisionSimulatorViewModel(appContext!!) as T
            else -> throw IllegalArgumentException("Unknown ViewModel $modelClass")
        }
    }
}
