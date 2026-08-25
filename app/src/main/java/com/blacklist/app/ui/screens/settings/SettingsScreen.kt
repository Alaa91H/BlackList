package com.blacklist.app.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.blacklist.app.R
import com.blacklist.app.di.ServiceLocator
import com.blacklist.app.di.ViewModelFactory
import kotlinx.coroutines.flow.collectLatest

private enum class BackupUiAction { EXPORT, RESTORE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavController) {
    val context = LocalContext.current
    val repo = remember { ServiceLocator.provideRepository(context) }
    val vm: SettingsViewModel = viewModel(factory = ViewModelFactory(repo))
    val settings by vm.settings.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingAction by remember { mutableStateOf<BackupUiAction?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var exportPassphrase by remember { mutableStateOf<CharArray?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val passphrase = exportPassphrase
        exportPassphrase = null
        if (uri != null && passphrase != null) {
            vm.exportEncryptedBackup(context.contentResolver, uri, passphrase)
        } else {
            passphrase?.fill('\u0000')
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pendingRestoreUri = uri
        if (uri != null) pendingAction = BackupUiAction.RESTORE
    }

    LaunchedEffect(vm) {
        vm.backupEvents.collectLatest { event ->
            val message = when (event) {
                is SettingsViewModel.BackupEvent.Exported -> context.getString(
                    R.string.settings_backup_export_success,
                    event.summary.rules,
                    event.summary.blockedNumbers
                )
                is SettingsViewModel.BackupEvent.Restored -> context.getString(
                    R.string.settings_backup_restore_success,
                    event.summary.rules,
                    event.summary.blockedNumbers
                )
                is SettingsViewModel.BackupEvent.Failed -> context.getString(R.string.settings_backup_failed)
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    if (pendingAction != null) {
        BackupPassphraseDialog(
            action = pendingAction!!,
            onDismiss = {
                pendingAction = null
                pendingRestoreUri = null
            },
            onConfirm = { passphrase ->
                when (pendingAction) {
                    BackupUiAction.EXPORT -> {
                        exportPassphrase = passphrase.toCharArray()
                        exportLauncher.launch(context.getString(R.string.settings_backup_file_name))
                    }
                    BackupUiAction.RESTORE -> {
                        pendingRestoreUri?.let { uri ->
                            vm.restoreEncryptedBackup(context.contentResolver, uri, passphrase.toCharArray())
                        }
                    }
                    null -> Unit
                }
                pendingAction = null
                pendingRestoreUri = null
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.settings_general), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                Column {
                    SettingSwitch(stringResource(R.string.home_block_unknown), stringResource(R.string.settings_block_unknown_desc), Icons.Filled.PersonOff, settings?.blockUnknown ?: false) { vm.setBlockUnknown(it) }
                    HorizontalDivider()
                    SettingSwitch(stringResource(R.string.home_block_private), stringResource(R.string.settings_block_private_desc), Icons.Filled.VisibilityOff, settings?.blockPrivate ?: true) { vm.setBlockPrivate(it) }
                    HorizontalDivider()
                    SettingSwitch(stringResource(R.string.home_block_all_except_whitelist), stringResource(R.string.settings_block_all_except_desc), Icons.Filled.DoNotDisturbOn, settings?.blockAllExceptWhitelist ?: false) { vm.setBlockAllExcept(it) }
                }
            }

            Text(stringResource(R.string.settings_notifications), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                Column {
                    SettingSwitch(stringResource(R.string.settings_notifications_global), stringResource(R.string.settings_notifications_global_desc), Icons.Filled.NotificationsActive, settings?.showBlockedNotification ?: true) { vm.setNotifications(it) }
                    HorizontalDivider()
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.Tune, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_notifications_per_number), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                            Text(stringResource(R.string.settings_notifications_per_number_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.settings_theme_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeChip(stringResource(R.string.settings_theme_system), settings?.themeMode == "SYSTEM" || settings?.themeMode == null) { vm.setTheme("SYSTEM") }
                        ThemeChip(stringResource(R.string.settings_theme_light), settings?.themeMode == "LIGHT") { vm.setTheme("LIGHT") }
                        ThemeChip(stringResource(R.string.settings_theme_dark), settings?.themeMode == "DARK") { vm.setTheme("DARK") }
                    }
                }
            }

            Text(stringResource(R.string.settings_backup), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.settings_backup_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { pendingAction = BackupUiAction.EXPORT }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.FileUpload, null)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                            Text(stringResource(R.string.settings_export))
                            Text(stringResource(R.string.settings_backup_export_desc), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    OutlinedButton(onClick = { restoreLauncher.launch(arrayOf("application/octet-stream", "application/json")) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.FileDownload, null)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                            Text(stringResource(R.string.settings_import))
                            Text(stringResource(R.string.settings_backup_import_desc), style = MaterialTheme.typography.labelSmall)
                        }
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

@Composable
private fun BackupPassphraseDialog(
    action: BackupUiAction,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var passphrase by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_backup_passphrase_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.settings_backup_passphrase_desc))
                if (action == BackupUiAction.RESTORE) {
                    Text(stringResource(R.string.settings_backup_restore_warning), color = MaterialTheme.colorScheme.error)
                }
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text(stringResource(R.string.settings_backup_passphrase_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(passphrase) }, enabled = passphrase.length >= 12) {
                Text(stringResource(if (action == BackupUiAction.EXPORT) R.string.settings_backup_create_file else R.string.settings_backup_restore_file))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
private fun SettingSwitch(
    title: String,
    desc: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    on: (Boolean) -> Unit
) {
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = on)
    }
}

@Composable
private fun ThemeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { if (selected) Icon(Icons.Filled.Check, null, modifier = Modifier.size(16.dp)) }
    )
}
