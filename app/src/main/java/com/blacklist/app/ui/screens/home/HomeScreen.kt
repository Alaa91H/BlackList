package com.blacklist.app.ui.screens.home

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.telecom.TelecomManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.blacklist.app.R
import com.blacklist.app.di.ServiceLocator
import com.blacklist.app.di.ViewModelFactory
import com.blacklist.app.ui.navigation.Routes
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    val ctx = LocalContext.current
    val repo = remember { ServiceLocator.provideRepository(ctx) }
    val vm: HomeViewModel = viewModel(factory = ViewModelFactory(repo, ctx))
    val settings by vm.settings.collectAsState()
    val todayCount by vm.todayCount.collectAsState()
    val totalCount by vm.blockedCount.collectAsState()
    val isRoleHeld = remember { mutableStateOf(vm.isRoleHeld()) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isRoleHeld.value = vm.isRoleHeld()
    }
    LaunchedEffect(Unit) { while (true) { delay(2000); isRoleHeld.value = vm.isRoleHeld() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title), fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { navController.navigate(Routes.SETTINGS) }) { Icon(Icons.Filled.Settings, contentDescription = null) }
                    IconButton(onClick = { navController.navigate(Routes.ABOUT) }) { Icon(Icons.Filled.Info, contentDescription = null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = if (isRoleHeld.value) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(if (isRoleHeld.value) Icons.Filled.Shield else Icons.Filled.ShieldMoon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(32.dp))
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(if (isRoleHeld.value) stringResource(R.string.home_protection_enabled) else stringResource(R.string.home_protection_disabled), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.home_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (isRoleHeld.value) Badge(containerColor = MaterialTheme.colorScheme.primary) { Text("ON", modifier = Modifier.padding(horizontal = 6.dp)) }
                    }
                    AnimatedVisibility(visible = !isRoleHeld.value, enter = fadeIn()+expandVertically(), exit = fadeOut()+shrinkVertically()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.home_set_as_default), style = MaterialTheme.typography.bodySmall)
                            Button(onClick = {
                                try {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        val rm = ctx.getSystemService(RoleManager::class.java)
                                        val intent = rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                                        launcher.launch(intent)
                                    } else {
                                        val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, ctx.packageName)
                                        launcher.launch(intent)
                                    }
                                } catch (_: Exception) { ctx.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }
                            }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Filled.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.home_request_role))
                            }
                        }
                    }
                }
            }
            Text(stringResource(R.string.home_stats), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard(stringResource(R.string.home_blocked_today), "$todayCount", Icons.Filled.Block, Modifier.weight(1f))
                StatCard(stringResource(R.string.home_total_blocked), "$totalCount", Icons.Filled.History, Modifier.weight(1f))
            }
            Text(stringResource(R.string.home_quick_actions), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            QuickToggleCard(title = stringResource(R.string.home_block_unknown), desc = stringResource(R.string.settings_block_unknown_desc), icon = Icons.Filled.PersonOff, checked = settings?.blockUnknown ?: false, onChecked = { vm.toggleBlockUnknown(it) })
            QuickToggleCard(title = stringResource(R.string.home_block_private), desc = stringResource(R.string.settings_block_private_desc), icon = Icons.Filled.VisibilityOff, checked = settings?.blockPrivate ?: true, onChecked = { vm.toggleBlockPrivate(it) })
            QuickToggleCard(title = stringResource(R.string.home_block_all_except_whitelist), desc = "Whitelist bypasses this", icon = Icons.Filled.DoNotDisturbOn, checked = settings?.blockAllExceptWhitelist ?: false, onChecked = { vm.toggleBlockAllExceptWhitelist(it) })
            Text("Manage", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            GridNav(navController)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable private fun StatCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable private fun QuickToggleCard(title: String, desc: String, icon: androidx.compose.ui.graphics.vector.ImageVector, checked: Boolean, onChecked: (Boolean)->Unit) {
    ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(48.dp)) { Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null) } }
            Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold); Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Switch(checked = checked, onCheckedChange = onChecked)
        }
    }
}
@Composable private fun GridNav(nav: NavController) {
    val items = listOf(Triple("Blacklist", Icons.Filled.Block, Routes.BLACKLIST), Triple("Whitelist", Icons.Filled.VerifiedUser, Routes.WHITELIST), Triple("Blocked Log", Icons.Filled.ListAlt, Routes.BLOCKED_LOG), Triple("Schedule", Icons.Filled.Schedule, Routes.SCHEDULE))
    LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.height(220.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), userScrollEnabled = false) {
        items(items.size) { idx ->
            val (label, icon, route) = items[idx]
            ElevatedCard(onClick = { nav.navigate(route) }, shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
