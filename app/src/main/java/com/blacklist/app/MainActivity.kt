package com.blacklist.app

import android.content.Intent
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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.blacklist.app.R
import com.blacklist.app.di.ServiceLocator
import com.blacklist.app.ui.navigation.AppNavGraph
import com.blacklist.app.ui.navigation.Routes
import com.blacklist.app.domain.sharing.SharedPhoneNumberExtractor
import com.blacklist.app.ui.theme.BlackListTheme

class MainActivity : ComponentActivity() {
    private var pendingSharedText by mutableStateOf<CharSequence?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingSharedText = sharedPlainText(intent)
        enableEdgeToEdge()
        val repo = ServiceLocator.provideRepository(applicationContext)
        setContent {
            val settings by repo.observeSettings().collectAsStateWithLifecycle(initialValue = null)
            val themeMode = settings?.themeMode ?: "SYSTEM"
            BlackListTheme(themeMode = themeMode) {
                val navController = rememberNavController()
                val backStack by navController.currentBackStackEntryAsState()
                val currentRoute = backStack?.destination?.route
                val sharedText = pendingSharedText

                LaunchedEffect(sharedText) {
                    if (!sharedText.isNullOrBlank() && currentRoute != Routes.SHARED_NUMBER) {
                        navController.navigate(Routes.SHARED_NUMBER) { launchSingleTop = true }
                    }
                }

                data class BottomItem(val route: String, val labelRes: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector)
                val bottomItems = listOf(
                    BottomItem(Routes.HOME, R.string.nav_home, Icons.Filled.Home),
                    BottomItem(Routes.BLACKLIST, R.string.nav_blacklist, Icons.Filled.Block),
                    BottomItem(Routes.WHITELIST, R.string.nav_whitelist, Icons.Filled.VerifiedUser),
                    BottomItem(Routes.BLOCKED_LOG, R.string.nav_blocked_log, Icons.Filled.ListAlt),
                )
                val showBottomBar = currentRoute in bottomItems.map { it.route }

                Scaffold(
                    bottomBar = {
                        if (showBottomBar) {
                            NavigationBar {
                                bottomItems.forEach { item ->
                                    NavigationBarItem(
                                        selected = currentRoute == item.route,
                                        onClick = {
                                            if (currentRoute != item.route) {
                                                navController.navigate(item.route) {
                                                    popUpTo(Routes.HOME) { saveState = true }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        },
                                        icon = { Icon(item.icon, contentDescription = stringResource(item.labelRes)) },
                                        label = { Text(stringResource(item.labelRes)) }
                                    )
                                }
                            }
                        }
                    }
                ) { inner ->
                    Surface(modifier = Modifier.fillMaxSize().padding(inner), color = MaterialTheme.colorScheme.background) {
                        AppNavGraph(
                            navController = navController,
                            sharedText = sharedText,
                            onSharedTextConsumed = ::clearPendingSharedText
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingSharedText = sharedPlainText(intent)
    }

    private fun clearPendingSharedText() {
        pendingSharedText = null
        intent?.removeExtra(Intent.EXTRA_TEXT)
    }

    private fun sharedPlainText(intent: Intent?): CharSequence? {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return null
        return intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
            ?.toString()
            ?.take(SharedPhoneNumberExtractor.MAX_INPUT_LENGTH)
            ?.takeIf { it.isNotBlank() }
    }
}
