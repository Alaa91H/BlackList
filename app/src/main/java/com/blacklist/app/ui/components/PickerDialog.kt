package com.blacklist.app.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.core.content.ContextCompat
import com.blacklist.app.R
import com.blacklist.app.util.PickerItem
import com.blacklist.app.util.PickerUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
fun PickerDialog(
    onDismiss: () -> Unit,
    onPick: (PickerItem) -> Unit
) {
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) } // 0 contacts, 1 call log, 2 messages
    var items by remember { mutableStateOf<List<PickerItem>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var hasPermission by remember {
        mutableStateOf(
            when (tab) {
                0 -> ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
                1 -> ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
                else -> ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        hasPermission = when (tab) {
            0 -> grants[Manifest.permission.READ_CONTACTS] == true
            1 -> grants[Manifest.permission.READ_CALL_LOG] == true
            else -> grants[Manifest.permission.READ_SMS] == true
        }
        if (hasPermission) loading = true
    }

    LaunchedEffect(tab, hasPermission) {
        if (!hasPermission) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        val data = withContext(Dispatchers.IO) {
            when (tab) {
                0 -> PickerUtils.getContacts(ctx)
                1 -> PickerUtils.getCallLog(ctx)
                else -> PickerUtils.getSmsSenders(ctx)
            }
        }
        items = data
        loading = false
    }

    // Update permission when tab changes
    LaunchedEffect(tab) {
        hasPermission = when (tab) {
            0 -> ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
            1 -> ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
            else -> ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        }
    }

    val filtered = remember(items, query) {
        if (query.isBlank()) items else items.filter {
            it.number.contains(query, true) || it.name?.contains(query, true) == true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.picker_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp, max = 500.dp)) {
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.picker_contacts), style = MaterialTheme.typography.labelSmall) }, icon = { Icon(Icons.Filled.Contacts, null, modifier = Modifier.size(16.dp)) })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.picker_call_log), style = MaterialTheme.typography.labelSmall) }, icon = { Icon(Icons.Filled.Call, null, modifier = Modifier.size(16.dp)) })
                    Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text(stringResource(R.string.picker_messages), style = MaterialTheme.typography.labelSmall) }, icon = { Icon(Icons.Filled.Message, null, modifier = Modifier.size(16.dp)) })
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.picker_search)) },
                    leadingIcon = { Icon(Icons.Filled.Search, null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(8.dp))
                when {
                    !hasPermission -> {
                        val permText = when (tab) {
                            0 -> stringResource(R.string.picker_permission_contacts)
                            1 -> stringResource(R.string.picker_permission_call_log)
                            else -> stringResource(R.string.picker_permission_messages)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Filled.Lock, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(permText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = {
                                val perm = when (tab) {
                                    0 -> Manifest.permission.READ_CONTACTS
                                    1 -> Manifest.permission.READ_CALL_LOG
                                    else -> Manifest.permission.READ_SMS
                                }
                                permissionLauncher.launch(arrayOf(perm))
                            }) { Text(stringResource(R.string.picker_grant)) }
                        }
                    }
                    loading -> Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    filtered.isEmpty() -> Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.SearchOff, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.picker_no_results), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                when (tab) {
                                    0 -> stringResource(R.string.blacklist_no_contacts)
                                    1 -> stringResource(R.string.blacklist_no_call_log)
                                    else -> stringResource(R.string.blacklist_no_messages)
                                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                        items(filtered) { item ->
                            Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth().clickable { onPick(item) }) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                when (tab) {
                                                    0 -> Icons.Filled.Person
                                                    1 -> when (item.typeLabel) {
                                                        "Incoming" -> Icons.Filled.CallReceived
                                                        "Outgoing" -> Icons.Filled.CallMade
                                                        "Missed" -> Icons.Filled.CallMissed
                                                        else -> Icons.Filled.History
                                                    }
                                                    else -> Icons.Filled.Message
                                                }, null, modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(item.number, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                        if (!item.name.isNullOrBlank()) Text(item.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            item.typeLabel?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                                            item.timestamp?.let { Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                        }
                                    }
                                    Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
