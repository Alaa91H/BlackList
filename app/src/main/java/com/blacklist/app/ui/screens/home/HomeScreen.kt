package com.blacklist.app.ui.screens.home

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.telecom.TelecomManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
                        if (isRoleHeld.value) Badge(containerColor = MaterialTheme.colorScheme.primary) { Text(stringResource(R.string.home_on), modifier = Modifier.padding(horizontal = 6.dp)) }
                    }
                    ProtectionScoreRow()
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
            QuickToggleCard(title = stringResource(R.string.home_block_all_except_whitelist), desc = stringResource(R.string.home_whitelist_bypass), icon = Icons.Filled.DoNotDisturbOn, checked = settings?.blockAllExceptWhitelist ?: false, onChecked = { vm.toggleBlockAllExceptWhitelist(it) })

            TemporaryFirewallCard(vm)

            Text(stringResource(R.string.home_manage), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            GridNav(navController)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable private fun ProtectionScoreRow() {
    val ctx = LocalContext.current
    val capManager = remember { ServiceLocator.provideCapabilityManager(ctx) }
    var health by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        health = capManager.getHealthPercentage()
        capManager.refresh()
        capManager.capabilityStates.collect { health = capManager.getHealthPercentage() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.home_protection_score), style = MaterialTheme.typography.labelMedium)
            Text("$health%", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
        LinearProgressIndicator(progress = { health / 100f }, modifier = Modifier.fillMaxWidth().height(8.dp), trackColor = MaterialTheme.colorScheme.surfaceVariant)
    }
}

@Composable private fun TemporaryFirewallCard(vm: HomeViewModel) {
    val rules by vm.blacklistRules.collectAsState()
    val active = remember(rules) { com.blacklist.app.domain.engine.TemporaryFirewall.blockAllActive(rules) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active?.id) {
        vm.cleanupExpired()
        while (active != null) { delay(1000); now = System.currentTimeMillis() }
    }
    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = if (active != null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(12.dp), color = if (active != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Timer, contentDescription = null, tint = if (active != null) MaterialTheme.colorScheme.onError else LocalContentColor.current) }
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.temp_firewall_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (active != null) stringResource(R.string.temp_firewall_active_until, formatRemaining(com.blacklist.app.domain.engine.TemporaryFirewall.remainingMs(active!!, now)))
                        else stringResource(R.string.temp_firewall_desc),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (active != null) {
                Button(onClick = { vm.cancelTempBlockAll() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.temp_firewall_stop))
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    FilledTonalButton(onClick = { vm.enableTempBlockAll(com.blacklist.app.domain.engine.TemporaryFirewall.MIN_15) }, modifier = Modifier.weight(1f)) { Text("15m") }
                    FilledTonalButton(onClick = { vm.enableTempBlockAll(com.blacklist.app.domain.engine.TemporaryFirewall.MIN_30) }, modifier = Modifier.weight(1f)) { Text("30m") }
                    FilledTonalButton(onClick = { vm.enableTempBlockAll(com.blacklist.app.domain.engine.TemporaryFirewall.HOUR_1) }, modifier = Modifier.weight(1f)) { Text("1h") }
                    FilledTonalButton(onClick = { vm.enableTempBlockAll(com.blacklist.app.domain.engine.TemporaryFirewall.HOUR_2) }, modifier = Modifier.weight(1f)) { Text("2h") }
                }
            }
        }
    }
}

private fun formatRemaining(ms: Long): String {
    val totalMin = ms / 60000
    val h = totalMin / 60
    val m = totalMin % 60
    val s = (ms / 1000) % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
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
    val items = listOf(
        Triple(stringResource(R.string.nav_blacklist), Icons.Filled.Block, Routes.BLACKLIST),
        Triple(stringResource(R.string.nav_whitelist), Icons.Filled.VerifiedUser, Routes.WHITELIST),
        Triple(stringResource(R.string.nav_blocked_log), Icons.Filled.ListAlt, Routes.BLOCKED_LOG),
        Triple(stringResource(R.string.nav_schedule), Icons.Filled.Schedule, Routes.SCHEDULE),
        Triple("Diagnostics", Icons.Filled.BugReport, Routes.DIAGNOSTICS),
        Triple("Statistics", Icons.Filled.BarChart, Routes.STATISTICS),
        Triple("Security", Icons.Filled.Security, Routes.SECURITY_EVENTS),
        Triple(stringResource(R.string.settings_permission_center), Icons.Filled.VerifiedUser, Routes.PERMISSIONS)
    )
    LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.height(320.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), userScrollEnabled = false) {
        items(items.size) { idx ->
            val (label, icon, route) = items[idx]
            ElevatedCard(onClick = { nav.navigate(route) }, shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, maxLines = 1)
                }
            }
        }
    }
}
