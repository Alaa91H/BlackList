package com.blacklist.app.ui.screens.blacklist

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
import com.blacklist.app.ui.components.PickerDialog
import java.text.DateFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlacklistScreen(nav: NavController) {
    val ctx = LocalContext.current
    val repo = remember { ServiceLocator.provideRepository(ctx) }
    val vm: BlacklistViewModel = viewModel(factory = ViewModelFactory(repo))
    val list by vm.filtered.collectAsState()
    val query by vm.query.collectAsState()
    val error by vm.error.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var inputNumber by remember { mutableStateOf("") }
    var inputName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.blacklist_title), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = { IconButton(onClick = { showAdd = true }) { Icon(Icons.Filled.PersonAdd, null) } }
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Filled.Add, null) } }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { vm.setQuery(it) },
                placeholder = { Text(stringResource(R.string.blacklist_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                singleLine = true
            )
            if (error != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(error!!, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.clearError() }) { Icon(Icons.Filled.Close, null) }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (list.isEmpty()) EmptyState(Icons.Filled.Block, stringResource(R.string.blacklist_empty), stringResource(R.string.blacklist_empty_desc), modifier = Modifier.fillMaxSize())
            else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(list, key = { it.id }) { item ->
                    ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().animateItem()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.size(48.dp)) {
                                Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Block, null, tint = MaterialTheme.colorScheme.onErrorContainer) }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(item.rawNumber, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                if (!item.displayName.isNullOrBlank()) Text(item.displayName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(DateFormat.getDateTimeInstance().format(item.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { vm.toggleNotification(item.id, !item.showNotification) }) {
                                Icon(
                                    if (item.showNotification) Icons.Filled.Notifications else Icons.Filled.NotificationsOff,
                                    contentDescription = if (item.showNotification) stringResource(R.string.blacklist_notify_enabled) else stringResource(R.string.blacklist_notify_disabled),
                                    tint = if (item.showNotification) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { vm.remove(item.id) }) { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }
    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text(stringResource(R.string.blacklist_add)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = inputNumber, onValueChange = { inputNumber = it }, label = { Text(stringResource(R.string.blacklist_add_hint)) }, placeholder = { Text("+123456789") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = inputName, onValueChange = { inputName = it }, label = { Text(stringResource(R.string.blacklist_add_name_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Contacts, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.blacklist_add_from_contacts), style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.History, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.blacklist_add_from_log), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Message, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.blacklist_add_from_messages))
                    }
                }
            },
            confirmButton = {
                Button(onClick = { vm.add(inputNumber, inputName); showAdd = false; inputNumber = ""; inputName = "" }, enabled = inputNumber.isNotBlank()) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
    if (showPicker) {
        PickerDialog(onDismiss = { showPicker = false }, onPick = { item ->
            inputNumber = item.number
            if (!item.name.isNullOrBlank()) inputName = item.name
            showPicker = false
        })
    }
}
