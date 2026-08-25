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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
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

enum class PickerSource(
    val permission: String,
    val titleRes: Int
) {
    CONTACTS(Manifest.permission.READ_CONTACTS, R.string.picker_contacts),
    CALL_LOG(Manifest.permission.READ_CALL_LOG, R.string.picker_call_log),
    MESSAGES(Manifest.permission.READ_SMS, R.string.picker_messages)
}

/**
 * Multi-select local number picker. Each source is loaded only after its own
 * optional runtime permission is granted; no source is required for blocking.
 */
@Composable
fun PickerDialog(
    initialSource: PickerSource = PickerSource.CONTACTS,
    onDismiss: () -> Unit,
    onConfirm: (List<PickerItem>) -> Unit
) {
    val context = LocalContext.current
    var source by remember { mutableStateOf(initialSource) }
    var items by remember { mutableStateOf<List<PickerItem>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var hasPermission by remember(source) { mutableStateOf(source.isGranted(context)) }
    val selectedNumbers = remember { mutableStateListOf<String>() }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
    }

    LaunchedEffect(source) {
        query = ""
        selectedNumbers.clear()
        hasPermission = source.isGranted(context)
    }
    LaunchedEffect(source, hasPermission) {
        if (!hasPermission) {
            items = emptyList()
            loading = false
            return@LaunchedEffect
        }
        loading = true
        items = withContext(Dispatchers.IO) {
            when (source) {
                PickerSource.CONTACTS -> PickerUtils.getContacts(context)
                PickerSource.CALL_LOG -> PickerUtils.getCallLog(context)
                PickerSource.MESSAGES -> PickerUtils.getSmsSenders(context)
            }
        }
        loading = false
    }

    val filtered = remember(items, query) {
        if (query.isBlank()) items else items.filter {
            it.number.contains(query, ignoreCase = true) || it.name?.contains(query, ignoreCase = true) == true
        }
    }
    val selectedItems = remember(items, selectedNumbers.toList()) { items.filter { it.number in selectedNumbers } }
    val allVisibleSelected = filtered.isNotEmpty() && filtered.all { it.number in selectedNumbers }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.picker_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(min = 340.dp, max = 560.dp)) {
                SourceTabs(selected = source, onSelect = { source = it })
                Spacer(Modifier.height(8.dp))
                when {
                    !hasPermission -> PermissionExplanation(
                        source = source,
                        onGrant = { permissionLauncher.launch(source.permission) }
                    )
                    loading -> Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    else -> {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text(stringResource(R.string.picker_search)) },
                            leadingIcon = { Icon(Icons.Filled.Search, null, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.picker_selected_count, selectedItems.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            TextButton(
                                onClick = {
                                    if (allVisibleSelected) {
                                        selectedNumbers.removeAll(filtered.map { it.number }.toSet())
                                    } else {
                                        filtered.forEach { if (it.number !in selectedNumbers) selectedNumbers.add(it.number) }
                                    }
                                },
                                enabled = filtered.isNotEmpty()
                            ) {
                                Icon(if (allVisibleSelected) Icons.Filled.CheckBoxOutlineBlank else Icons.Filled.CheckBox, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(if (allVisibleSelected) R.string.picker_clear_all else R.string.picker_select_all))
                            }
                        }
                        if (filtered.isEmpty()) {
                            Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Filled.SearchOff, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(stringResource(R.string.picker_no_results), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                                items(filtered, key = { "${source.name}_${it.number}" }) { item ->
                                    val selected = item.number in selectedNumbers
                                    PickerRow(item = item, selected = selected, onToggle = {
                                        if (selected) selectedNumbers.remove(item.number) else selectedNumbers.add(item.number)
                                    })
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedItems) }, enabled = selectedItems.isNotEmpty()) {
                Text(stringResource(R.string.picker_add_selected, selectedItems.size))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
private fun SourceTabs(selected: PickerSource, onSelect: (PickerSource) -> Unit) {
    TabRow(selectedTabIndex = PickerSource.entries.indexOf(selected)) {
        PickerSource.entries.forEach { source ->
            Tab(
                selected = source == selected,
                onClick = { onSelect(source) },
                text = { Text(stringResource(source.titleRes), style = MaterialTheme.typography.labelSmall) },
                icon = {
                    Icon(
                        when (source) {
                            PickerSource.CONTACTS -> Icons.Filled.Contacts
                            PickerSource.CALL_LOG -> Icons.Filled.Call
                            PickerSource.MESSAGES -> Icons.Filled.Message
                        },
                        null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }
    }
}

@Composable
private fun PickerRow(item: PickerItem, selected: Boolean, onToggle: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = if (selected) 2.dp else 0.dp,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Checkbox(checked = selected, onCheckedChange = { onToggle() })
            Icon(Icons.Filled.Person, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                item.name?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(item.number, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } ?: Text(item.number, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                val detail = listOfNotNull(item.typeLabel, item.timestamp?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)) }).joinToString(" • ")
                if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun PermissionExplanation(source: PickerSource, onGrant: () -> Unit) {
    val message = when (source) {
        PickerSource.CONTACTS -> stringResource(R.string.picker_permission_contacts)
        PickerSource.CALL_LOG -> stringResource(R.string.picker_permission_call_log)
        PickerSource.MESSAGES -> stringResource(R.string.picker_permission_messages)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Filled.Lock, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onGrant) { Text(stringResource(R.string.picker_grant)) }
    }
}

private fun PickerSource.isGranted(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
