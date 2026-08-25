package com.blacklist.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blacklist.app.R
import com.blacklist.app.data.local.entity.BlockedNumberEntity

/** Local notification controls; this UI never participates in call enforcement. */
@Composable
fun NotificationManagerDialog(
    globallyEnabled: Boolean,
    blockedNumbers: List<BlockedNumberEntity>,
    onDismiss: () -> Unit,
    onGlobalEnabledChange: (Boolean) -> Unit,
    onSetAll: (Boolean) -> Unit,
    onSetNumber: (Long, Boolean) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val visibleNumbers = remember(blockedNumbers, query) {
        if (query.isBlank()) blockedNumbers else blockedNumbers.filter {
            it.rawNumber.contains(query, ignoreCase = true) || it.displayName?.contains(query, ignoreCase = true) == true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_notification_manager)) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 540.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.settings_notification_global_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ElevatedCard(shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(if (globallyEnabled) Icons.Filled.Notifications else Icons.Filled.NotificationsOff, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_notifications_global), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.settings_notifications_global_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = globallyEnabled, onCheckedChange = onGlobalEnabledChange)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { onSetAll(true) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Notifications, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.settings_notification_enable_all), style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(onClick = { onSetAll(false) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.NotificationsOff, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.settings_notification_mute_all), style = MaterialTheme.typography.labelSmall)
                    }
                }

                if (blockedNumbers.isEmpty()) {
                    Text(stringResource(R.string.settings_notification_manager_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        placeholder = { Text(stringResource(R.string.blacklist_search_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                        items(visibleNumbers, key = { it.id }) { number ->
                            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(if (number.showNotification) Icons.Filled.Notifications else Icons.Filled.NotificationsOff, null, tint = MaterialTheme.colorScheme.primary)
                                    Column(Modifier.weight(1f)) {
                                        number.displayName?.takeIf { it.isNotBlank() }?.let {
                                            Text(it, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                            Text(number.rawNumber, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        } ?: Text(number.rawNumber, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    }
                                    Switch(checked = number.showNotification, onCheckedChange = { onSetNumber(number.id, it) })
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } }
    )
}
