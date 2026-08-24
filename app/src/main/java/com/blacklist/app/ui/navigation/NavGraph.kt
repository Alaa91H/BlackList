package com.blacklist.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.blacklist.app.ui.screens.about.AboutScreen
import com.blacklist.app.ui.screens.blacklist.BlacklistScreen
import com.blacklist.app.ui.screens.blockedlog.BlockedLogScreen
import com.blacklist.app.ui.screens.home.HomeScreen
import com.blacklist.app.ui.screens.schedule.ScheduleScreen
import com.blacklist.app.ui.screens.settings.SettingsScreen
import com.blacklist.app.ui.screens.whitelist.WhitelistScreen

object Routes {
    const val HOME = "home"
    const val BLACKLIST = "blacklist"
    const val WHITELIST = "whitelist"
    const val BLOCKED_LOG = "blocked_log"
    const val SCHEDULE = "schedule"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
}

@Composable
fun AppNavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) { HomeScreen(navController) }
        composable(Routes.BLACKLIST) { BlacklistScreen(navController) }
        composable(Routes.WHITELIST) { WhitelistScreen(navController) }
        composable(Routes.BLOCKED_LOG) { BlockedLogScreen(navController) }
        composable(Routes.SCHEDULE) { ScheduleScreen(navController) }
        composable(Routes.SETTINGS) { SettingsScreen(navController) }
        composable(Routes.ABOUT) { AboutScreen(navController) }
    }
}
