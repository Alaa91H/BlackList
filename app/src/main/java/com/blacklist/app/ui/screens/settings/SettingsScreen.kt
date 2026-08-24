package com.blacklist.app.ui.screens.settings

import androidx.compose.foundation.layout.*
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavController) {
    val ctx = LocalContext.current
    val repo = remember { ServiceLocator.provideRepository(ctx) }
    val vm: SettingsViewModel = viewModel(factory = ViewModelFactory(repo))
    val s by vm.settings.collectAsState()
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Filled.ArrowBack, null) } }) }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.settings_general), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                Column {
                    SettingSwitch("Block Unknown Numbers", stringResource(R.string.settings_block_unknown_desc), Icons.Filled.PersonOff, s?.blockUnknown ?: false) { vm.setBlockUnknown(it) }
                    HorizontalDivider()
                    SettingSwitch("Block Private / Hidden", stringResource(R.string.settings_block_private_desc), Icons.Filled.VisibilityOff, s?.blockPrivate ?: true) { vm.setBlockPrivate(it) }
                    HorizontalDivider()
                    SettingSwitch("Block All Except Whitelist", "When enabled, only whitelisted numbers ring", Icons.Filled.DoNotDisturbOn, s?.blockAllExceptWhitelist ?: false) { vm.setBlockAllExcept(it) }
                    HorizontalDivider()
                    SettingSwitch(stringResource(R.string.settings_notifications), "Show low-priority notification for blocked calls", Icons.Filled.Notifications, s?.showBlockedNotification ?: true) { vm.setNotifications(it) }
                }
            }
            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose how BlackList looks. System Default follows your phone.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeChip("System", s?.themeMode == "SYSTEM" || s?.themeMode == null) { vm.setTheme("SYSTEM") }
                        ThemeChip("Light", s?.themeMode == "LIGHT") { vm.setTheme("LIGHT") }
                        ThemeChip("Dark", s?.themeMode == "DARK") { vm.setTheme("DARK") }
                    }
                }
            }
            ElevatedCard(shape = RoundedCornerShape(20.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.Lock, null)
                    Column {
                        Text(stringResource(R.string.settings_privacy), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.settings_privacy_desc), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
@Composable private fun SettingSwitch(title: String, desc: String, icon: androidx.compose.ui.graphics.vector.ImageVector, checked: Boolean, on: (Boolean)->Unit) {
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium); Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(checked = checked, onCheckedChange = on)
    }
}
@Composable private fun ThemeChip(label: String, selected: Boolean, onClick: ()->Unit) { FilterChip(selected = selected, onClick = onClick, label = { Text(label) }, leadingIcon = { if (selected) Icon(Icons.Filled.Check, null, modifier = Modifier.size(16.dp)) }) }
