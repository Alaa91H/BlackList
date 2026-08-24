package com.blacklist.app.ui.screens.blockedlog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.blacklist.app.ui.components.EmptyState
import java.text.DateFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedLogScreen(nav: NavController) {
    val ctx = LocalContext.current
    val repo = remember { ServiceLocator.provideRepository(ctx) }
    val vm: BlockedLogViewModel = viewModel(factory = ViewModelFactory(repo))
    val logs by vm.logs.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.blocked_log_title), fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Filled.ArrowBack, null) } }, actions = { if (logs.isNotEmpty()) IconButton(onClick = { confirmClear = true }) { Icon(Icons.Filled.DeleteSweep, null) } })
        }
    ) { pad ->
        if (logs.isEmpty()) Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { EmptyState(Icons.Filled.CallEnd, stringResource(R.string.blocked_log_empty), "All blocked calls will appear here with time and reason") }
        else LazyColumn(modifier = Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(logs, key = { it.id }) { log ->
                ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().animateItem()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.size(48.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Block, null) } }
                        Column(Modifier.weight(1f)) {
                            Text(log.phoneNumber ?: "Private / Hidden", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            if (!log.displayName.isNullOrBlank()) Text(log.displayName, style = MaterialTheme.typography.bodySmall)
                            Text(reasonLabel(log.reason), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(log.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        AssistChip(onClick = {}, label = { Text(log.reason) }, leadingIcon = { Icon(Icons.Filled.Shield, null, modifier = Modifier.size(16.dp)) })
                    }
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text(stringResource(R.string.blocked_log_clear)) }, text = { Text("Clear all blocked call history? This cannot be undone.") },
            confirmButton = { Button(onClick = { vm.clear(); confirmClear = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.action_cancel)) } })
    }
}
private fun reasonLabel(r: String): String = when (r) {
    "BLACKLIST" -> "Blacklist"
    "UNKNOWN" -> "Unknown number"
    "PRIVATE" -> "Private / Hidden"
    "SCHEDULE" -> "Schedule rule"
    "ALL_EXCEPT_WHITELIST" -> "All except whitelist"
    else -> r
}
