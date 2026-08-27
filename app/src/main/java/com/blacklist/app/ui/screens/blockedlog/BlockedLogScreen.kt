package com.blacklist.app.ui.screens.blockedlog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.blacklist.app.R
import com.blacklist.app.di.ServiceLocator
import com.blacklist.app.di.ViewModelFactory
import com.blacklist.app.ui.components.EmptyState
import kotlinx.coroutines.flow.collectLatest
import java.text.DateFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedLogScreen(nav: NavController) {
    val ctx = LocalContext.current
    val repo = remember { ServiceLocator.provideRepository(ctx) }
    val vm: BlockedLogViewModel = viewModel(factory = ViewModelFactory(repo, ctx.applicationContext))
    val logs by vm.logs.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmClear by remember { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<com.blacklist.app.data.local.entity.BlockedCallLogEntity?>(null) }
    var tempAllowTarget by remember { mutableStateOf<com.blacklist.app.data.local.entity.BlockedCallLogEntity?>(null) }
    LaunchedEffect(Unit) { vm.cleanupExpiredTemporaryRules() }
    LaunchedEffect(vm) {
        vm.recoveryEvents.collectLatest { event ->
            val message = when (event) {
                BlockedLogRecoveryEvent.MarkedNotSpam -> ctx.getString(R.string.fp_not_spam_saved)
                BlockedLogRecoveryEvent.InvalidNumber -> ctx.getString(R.string.fp_not_spam_invalid)
                BlockedLogRecoveryEvent.Failed -> ctx.getString(R.string.fp_not_spam_failed)
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.blocked_log_title), fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }, actions = { if (logs.isNotEmpty()) IconButton(onClick = { confirmClear = true }) { Icon(Icons.Filled.DeleteSweep, null) } })
        }
    ) { pad ->
        if (logs.isEmpty()) Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { EmptyState(Icons.Filled.CallEnd, stringResource(R.string.blocked_log_empty), stringResource(R.string.blocked_log_empty_desc)) }
        else LazyColumn(modifier = Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(logs, key = { it.id }) { log ->
                ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().animateItem()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.size(48.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Block, null) } }
                        Column(Modifier.weight(1f)) {
                            Text(log.phoneNumber ?: stringResource(R.string.blocked_log_private_hidden), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            if (!log.displayName.isNullOrBlank()) Text(log.displayName, style = MaterialTheme.typography.bodySmall)
                            Text(reasonLabel(log.reason), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(log.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        AssistChip(onClick = { actionTarget = log }, label = { Text(log.reason) }, leadingIcon = { Icon(Icons.Filled.Shield, null, modifier = Modifier.size(16.dp)) })
                    }
                    FalsePositiveActions(log = log,
                        onNotSpam = { actionTarget = log },
                        onAlwaysAllow = { vm.alwaysAllow(log.phoneNumber!!, log.displayName) },
                        onTempAllow = { tempAllowTarget = log },
                        enabled = !log.phoneNumber.isNullOrBlank())
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text(stringResource(R.string.blocked_log_clear)) }, text = { Text(stringResource(R.string.blocked_log_clear_confirm)) },
            confirmButton = { Button(onClick = { vm.clear(ctx); confirmClear = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.action_cancel)) } })
    }
    actionTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { actionTarget = null },
            title = { Text(stringResource(R.string.fp_action_title)) },
            text = { Text(stringResource(R.string.fp_action_desc, target.phoneNumber ?: "")) },
            confirmButton = {
                TextButton(onClick = {
                    vm.markNotSpam(target.phoneNumber.orEmpty())
                    actionTarget = null
                }) { Text(stringResource(R.string.fp_not_spam_confirm)) }
            },
            dismissButton = { TextButton(onClick = { actionTarget = null }) { Text(stringResource(R.string.action_cancel)) } })
    }
    tempAllowTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { tempAllowTarget = null },
            title = { Text(stringResource(R.string.fp_temp_allow_title)) },
            text = { Text(stringResource(R.string.fp_temp_allow_desc, target.phoneNumber ?: "")) },
            confirmButton = {
                TextButton(onClick = {
                    vm.temporaryAllow(target.phoneNumber!!, com.blacklist.app.domain.engine.TemporaryFirewall.HOUR_1)
                    tempAllowTarget = null
                }) { Text(stringResource(R.string.fp_temp_allow_confirm)) }
            },
            dismissButton = { TextButton(onClick = { tempAllowTarget = null }) { Text(stringResource(R.string.action_cancel)) } })
    }
}
@Composable
private fun FalsePositiveActions(
    log: com.blacklist.app.data.local.entity.BlockedCallLogEntity,
    onNotSpam: () -> Unit,
    onAlwaysAllow: () -> Unit,
    onTempAllow: () -> Unit,
    enabled: Boolean
) {
    Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = onNotSpam, enabled = enabled, contentPadding = PaddingValues(horizontal = 12.dp)) {
            Icon(Icons.Filled.ThumbUp, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.fp_not_spam), style = MaterialTheme.typography.labelMedium)
        }
        FilledTonalButton(onClick = onAlwaysAllow, enabled = enabled, contentPadding = PaddingValues(horizontal = 12.dp)) {
            Icon(Icons.Filled.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.fp_always_allow), style = MaterialTheme.typography.labelMedium)
        }
        FilledTonalButton(onClick = onTempAllow, enabled = enabled, contentPadding = PaddingValues(horizontal = 12.dp)) {
            Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.fp_temp_allow), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun reasonLabel(r: String): String {
    return when (r) {
        "BLACKLIST" -> "Blacklist"
        "UNKNOWN" -> "Unknown number"
        "PRIVATE" -> "Private / Hidden"
        "SCHEDULE" -> "Schedule rule"
        "ALL_EXCEPT_WHITELIST" -> "All except whitelist"
        else -> r
    }
}
