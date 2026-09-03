package com.blacklist.app.ui.screens.settings

import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
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
import com.blacklist.app.domain.importexport.CsvListTarget
import com.blacklist.app.data.local.entity.AppSettingsEntity
import com.blacklist.app.domain.retention.BlockedCallLogRetentionPolicy
import com.blacklist.app.service.TemporaryBlockTileService
import com.blacklist.app.ui.navigation.Routes
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private enum class BackupUiAction { EXPORT, RESTORE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavController) {
    val context = LocalContext.current
    val repo = remember { ServiceLocator.provideRepository(context) }
    val notificationManager = remember { ServiceLocator.provideNotificationManager(context) }
    val vm: SettingsViewModel = viewModel(factory = ViewModelFactory(repo))
    val settings by vm.settings.collectAsState()
    val blockedNumbers by vm.blockedNumbers.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var pendingAction by remember { mutableStateOf<BackupUiAction?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var exportPassphrase by remember { mutableStateOf<CharArray?>(null) }
    var showNotificationManager by remember { mutableStateOf(false) }
    var showBlockedLogRetentionPicker by remember { mutableStateOf(false) }
    var csvExportTarget by remember { mutableStateOf<CsvListTarget?>(null) }
    var csvImportTarget by remember { mutableStateOf<CsvListTarget?>(null) }
    val pendingCsvImport by vm.pendingCsvImport.collectAsState()
    val pendingOfflineReputationImport by vm.pendingOfflineReputationImport.collectAsState()
    val offlineReputationSources by vm.offlineReputationSources.collectAsState()

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
    val csvExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val target = csvExportTarget
        csvExportTarget = null
        if (uri != null && target != null) vm.exportCsv(context.contentResolver, uri, target)
    }
    val csvImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = csvImportTarget
        csvImportTarget = null
        if (uri != null && target != null) vm.previewCsvImport(context.contentResolver, uri, target)
    }
    val offlineReputationImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri?.scheme == "content") vm.previewOfflineReputationImport(context.contentResolver, uri)
    }

    LaunchedEffect(settings?.showBlockedNotification) {
        settings?.showBlockedNotification?.let { enabled ->
            notificationManager.updatePolicy(notificationManager.getPolicy().copy(enabled = enabled))
        }
    }

    fun requestTemporaryBlockTile() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val statusBarManager = context.getSystemService(StatusBarManager::class.java)
        if (statusBarManager == null) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.settings_temporary_block_tile_request_error))
            }
            return
        }
        statusBarManager.requestAddTileService(
            ComponentName(context, TemporaryBlockTileService::class.java),
            context.getString(R.string.tile_temporary_block),
            Icon.createWithResource(context, R.drawable.ic_temporary_block_tile),
            context.mainExecutor
        ) { result ->
            val message = when (result) {
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> R.string.settings_temporary_block_tile_added
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> R.string.settings_temporary_block_tile_already_added
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> R.string.settings_temporary_block_tile_not_added
                else -> R.string.settings_temporary_block_tile_request_error
            }
            coroutineScope.launch { snackbarHostState.showSnackbar(context.getString(message)) }
        }
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

    LaunchedEffect(vm) {
        vm.csvEvents.collectLatest { event ->
            val message = when (event) {
                is SettingsViewModel.CsvEvent.Exported -> context.getString(R.string.csv_export_success, event.count)
                is SettingsViewModel.CsvEvent.Imported -> context.getString(R.string.csv_import_success, event.added, event.skipped)
                is SettingsViewModel.CsvEvent.Failed -> context.getString(R.string.csv_operation_failed)
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(vm) {
        vm.offlineReputationEvents.collectLatest { event ->
            val message = when (event) {
                is SettingsViewModel.OfflineReputationEvent.Imported -> context.getString(
                    R.string.offline_reputation_import_success,
                    event.entries,
                    event.sourceName
                )
                is SettingsViewModel.OfflineReputationEvent.Failed -> context.getString(R.string.offline_reputation_import_failed)
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    pendingCsvImport?.let { pending ->
        CsvImportPreviewDialog(
            pending = pending,
            onDismiss = vm::dismissCsvImportPreview,
            onConfirm = vm::confirmCsvImport
        )
    }
    pendingOfflineReputationImport?.let { pending ->
        OfflineReputationImportPreviewDialog(
            pending = pending,
            onDismiss = vm::dismissOfflineReputationImport,
            onConfirm = vm::confirmOfflineReputationImport
        )
    }

    if (showNotificationManager) {
        NotificationManagerDialog(
            globallyEnabled = settings?.showBlockedNotification ?: true,
            blockedNumbers = blockedNumbers,
            onDismiss = { showNotificationManager = false },
            onGlobalEnabledChange = { enabled ->
                vm.setNotifications(enabled)
                notificationManager.updatePolicy(notificationManager.getPolicy().copy(enabled = enabled))
            },
            onSetAll = vm::setAllBlockedNumberNotifications,
            onSetNumber = vm::setBlockedNumberNotification
        )
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
            ProtectionProfileCard(settings?.activeProfileId ?: com.blacklist.app.domain.model.ProtectionProfiles.CUSTOM) { profile ->
                vm.applyProtectionProfile(profile)
            }
            ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                Column {
                    SettingSwitch(stringResource(R.string.home_block_unknown), stringResource(R.string.settings_block_unknown_desc), Icons.Filled.PersonOff, settings?.blockUnknown ?: false) { vm.setBlockUnknown(it) }
                    HorizontalDivider()
                    SettingSwitch(
                        stringResource(R.string.settings_silence_unknown),
                        stringResource(R.string.settings_silence_unknown_desc),
                        Icons.AutoMirrored.Filled.VolumeOff,
                        settings?.silenceUnknown ?: false,
                        enabled = settings?.blockUnknown ?: false
                    ) { vm.setSilenceUnknown(it) }
                    HorizontalDivider()
                    SettingSwitch(stringResource(R.string.home_block_private), stringResource(R.string.settings_block_private_desc), Icons.Filled.VisibilityOff, settings?.blockPrivate ?: true) { vm.setBlockPrivate(it) }
                    HorizontalDivider()
                    SettingSwitch(
                        stringResource(R.string.settings_silence_private),
                        stringResource(R.string.settings_silence_private_desc),
                        Icons.AutoMirrored.Filled.VolumeOff,
                        settings?.silencePrivate ?: false,
                        enabled = settings?.blockPrivate ?: true
                    ) { vm.setSilencePrivate(it) }
                    HorizontalDivider()
                    SettingSwitch(
                        stringResource(R.string.settings_block_international),
                        stringResource(R.string.settings_block_international_desc),
                        Icons.Filled.Public,
                        settings?.blockInternational ?: false
                    ) { vm.setBlockInternational(it) }
                    HorizontalDivider()
                    SettingSwitch(
                        stringResource(R.string.settings_silence_international),
                        stringResource(R.string.settings_silence_international_desc),
                        Icons.AutoMirrored.Filled.VolumeOff,
                        settings?.silenceInternational ?: false,
                        enabled = settings?.blockInternational ?: false
                    ) { vm.setSilenceInternational(it) }
                    HorizontalDivider()
                    SettingSwitch(
                        stringResource(R.string.settings_first_time_block),
                        stringResource(R.string.settings_first_time_caller_desc),
                        Icons.Filled.PersonAdd,
                        settings?.firstTimeCallerPolicy == AppSettingsEntity.FIRST_TIME_BLOCK
                    ) { vm.setFirstTimeCallerPolicy(if (it) AppSettingsEntity.FIRST_TIME_BLOCK else AppSettingsEntity.FIRST_TIME_OFF) }
                    HorizontalDivider()
                    SettingSwitch(
                        stringResource(R.string.settings_first_time_silence),
                        stringResource(R.string.settings_first_time_caller),
                        Icons.AutoMirrored.Filled.VolumeOff,
                        settings?.firstTimeCallerPolicy == AppSettingsEntity.FIRST_TIME_SILENCE
                    ) { vm.setFirstTimeCallerPolicy(if (it) AppSettingsEntity.FIRST_TIME_SILENCE else AppSettingsEntity.FIRST_TIME_OFF) }
                    HorizontalDivider()
                    SettingSwitch(
                        stringResource(R.string.settings_repeated_block),
                        stringResource(R.string.settings_repeated_caller_desc),
                        Icons.Filled.Repeat,
                        settings?.repeatedCallerPolicy == AppSettingsEntity.REPEATED_BLOCK
                    ) { vm.setRepeatedCallerPolicy(if (it) AppSettingsEntity.REPEATED_BLOCK else AppSettingsEntity.REPEATED_OFF) }
                    HorizontalDivider()
                    SettingSwitch(
                        stringResource(R.string.settings_repeated_silence),
                        stringResource(R.string.settings_repeated_caller),
                        Icons.AutoMirrored.Filled.VolumeOff,
                        settings?.repeatedCallerPolicy == AppSettingsEntity.REPEATED_SILENCE
                    ) { vm.setRepeatedCallerPolicy(if (it) AppSettingsEntity.REPEATED_SILENCE else AppSettingsEntity.REPEATED_OFF) }
                    HorizontalDivider()
                    SettingSwitch(stringResource(R.string.home_block_all_except_whitelist), stringResource(R.string.settings_block_all_except_desc), Icons.Filled.DoNotDisturbOn, settings?.blockAllExceptWhitelist ?: false) { vm.setBlockAllExcept(it) }
                    HorizontalDivider()
                    SettingSwitch(stringResource(R.string.settings_outbound_callback_grace), stringResource(R.string.settings_outbound_callback_grace_desc), Icons.Filled.PersonOff, settings?.allowOutboundCallbackGrace ?: false) { vm.setOutboundCallbackGrace(it) }
                }
            }

            Text(stringResource(R.string.settings_quick_access), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.DoNotDisturbOn, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_temporary_block_tile), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                            Text(stringResource(R.string.settings_temporary_block_tile_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        OutlinedButton(onClick = ::requestTemporaryBlockTile, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.settings_temporary_block_tile_add))
                        }
                    } else {
                        Text(stringResource(R.string.settings_temporary_block_tile_manual_add), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Text(stringResource(R.string.settings_notifications), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                Column {
                    SettingSwitch(stringResource(R.string.settings_notifications_global), stringResource(R.string.settings_notifications_global_desc), Icons.Filled.NotificationsActive, settings?.showBlockedNotification ?: true) { enabled ->
                        vm.setNotifications(enabled)
                        notificationManager.updatePolicy(notificationManager.getPolicy().copy(enabled = enabled))
                    }
                    HorizontalDivider()
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.Tune, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_notification_manager), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                            Text(stringResource(R.string.settings_notification_manager_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { showNotificationManager = true }) { Text(stringResource(R.string.home_manage)) }
                    }
                }
            }

            Text(stringResource(R.string.settings_privacy), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                Column {
                    SettingSwitch(
                        stringResource(R.string.settings_private_blocked_history),
                        stringResource(R.string.settings_private_blocked_history_desc),
                        Icons.Filled.VisibilityOff,
                        settings?.hideBlockedCallsFromSystemLog ?: false
                    ) { vm.setPrivateBlockedHistory(it) }
                    HorizontalDivider()
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Filled.History, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_blocked_log_retention), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                            Text(stringResource(R.string.settings_blocked_log_retention_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { showBlockedLogRetentionPicker = true }) {
                            Text(retentionOptionLabel(settings?.blockedLogRetentionDays ?: BlockedCallLogRetentionPolicy.NEVER))
                        }
                    }
                }
            }

            Text(stringResource(R.string.settings_permissions), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.AdminPanelSettings, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_permission_manager), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                        Text(stringResource(R.string.settings_permission_manager_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { nav.navigate(Routes.PERMISSIONS) }) { Text(stringResource(R.string.home_manage)) }
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

            Text(stringResource(R.string.settings_csv_transfer), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.settings_csv_transfer_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = {
                        csvExportTarget = CsvListTarget.BLACKLIST
                        csvExportLauncher.launch(context.getString(R.string.csv_blacklist_file_name))
                    }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.csv_export_blacklist)) }
                    OutlinedButton(onClick = {
                        csvImportTarget = CsvListTarget.BLACKLIST
                        csvImportLauncher.launch(arrayOf("text/csv", "text/plain", "application/csv"))
                    }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.csv_import_blacklist)) }
                    HorizontalDivider()
                    OutlinedButton(onClick = {
                        csvExportTarget = CsvListTarget.WHITELIST
                        csvExportLauncher.launch(context.getString(R.string.csv_whitelist_file_name))
                    }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.csv_export_whitelist)) }
                    OutlinedButton(onClick = {
                        csvImportTarget = CsvListTarget.WHITELIST
                        csvImportLauncher.launch(arrayOf("text/csv", "text/plain", "application/csv"))
                    }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.csv_import_whitelist)) }
                }
            }

            Text(stringResource(R.string.offline_reputation_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.offline_reputation_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(
                        onClick = { offlineReputationImportLauncher.launch("text/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.FileUpload, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.offline_reputation_import))
                    }
                    Text(stringResource(R.string.offline_reputation_sources), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                    if (offlineReputationSources.isEmpty()) {
                        Text(stringResource(R.string.offline_reputation_no_sources), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        offlineReputationSources.forEach { source ->
                            HorizontalDivider()
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(source.sourceName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    source.sourceVersion?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    Text(
                                        stringResource(R.string.offline_reputation_source_summary, source.entryCount, source.fingerprintSha256.take(12)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        stringResource(
                                            R.string.offline_reputation_source_imported,
                                            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(source.importedAt))
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    source.sourceUrl?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                                TextButton(onClick = { vm.deleteOfflineReputationSource(source.id) }) {
                                    Text(stringResource(R.string.offline_reputation_remove))
                                }
                            }
                        }
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

    if (showBlockedLogRetentionPicker) {
        BlockedLogRetentionPickerDialog(
            selectedDays = settings?.blockedLogRetentionDays ?: BlockedCallLogRetentionPolicy.NEVER,
            onSelected = vm::setBlockedLogRetentionDays,
            onDismiss = { showBlockedLogRetentionPicker = false }
        )
    }
}

@Composable
private fun BlockedLogRetentionPickerDialog(
    selectedDays: Long,
    onSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_blocked_log_retention)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.settings_blocked_log_retention_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                BlockedCallLogRetentionPolicy.supportedDays.forEach { days ->
                    TextButton(
                        onClick = {
                            onSelected(days)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (days == selectedDays) {
                            Icon(Icons.Filled.Check, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(retentionOptionLabel(days))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
private fun retentionOptionLabel(days: Long): String = when (days) {
    BlockedCallLogRetentionPolicy.NEVER -> stringResource(R.string.settings_blocked_log_retention_never)
    else -> stringResource(R.string.settings_blocked_log_retention_days, days)
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
    enabled: Boolean = true,
    on: (Boolean) -> Unit
) {
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = on, enabled = enabled)
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
