package com.blacklist.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.blacklist.app.di.ServiceLocator
import com.blacklist.app.ui.navigation.AppNavGraph
import com.blacklist.app.ui.navigation.Routes
import com.blacklist.app.ui.theme.BlackListTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = ServiceLocator.provideRepository(applicationContext)
        setContent {
            val settings by repo.observeSettings().collectAsStateWithLifecycle(initialValue = null)
            val themeMode = settings?.themeMode ?: "SYSTEM"
            BlackListTheme(themeMode = themeMode) {
                val navController = rememberNavController()
                val backStack by navController.currentBackStackEntryAsState()
                val currentRoute = backStack?.destination?.route

                val bottomItems = listOf(
                    Triple(Routes.HOME, "Home", Icons.Filled.Home),
                    Triple(Routes.BLACKLIST, "Blacklist", Icons.Filled.Block),
                    Triple(Routes.WHITELIST, "Whitelist", Icons.Filled.VerifiedUser),
                    Triple(Routes.BLOCKED_LOG, "Log", Icons.Filled.ListAlt),
                    Triple(Routes.SCHEDULE, "Schedule", Icons.Filled.Schedule),
                )
                val showBottomBar = currentRoute in bottomItems.map { it.first }

                Scaffold(
                    bottomBar = {
                        if (showBottomBar) {
                            NavigationBar {
                                bottomItems.forEach { (route, label, icon) ->
                                    NavigationBarItem(
                                        selected = currentRoute == route,
                                        onClick = {
                                            if (currentRoute != route) {
                                                navController.navigate(route) {
                                                    popUpTo(Routes.HOME) { saveState = true }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        },
                                        icon = { Icon(icon, contentDescription = label) },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }
                    }
                ) { inner ->
                    Surface(modifier = Modifier.fillMaxSize().padding(inner), color = MaterialTheme.colorScheme.background) {
                        AppNavGraph(navController = navController)
                    }
                }
            }
        }
    }
}
