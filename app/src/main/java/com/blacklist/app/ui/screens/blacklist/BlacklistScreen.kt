package com.blacklist.app.ui.screens.blacklist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.blacklist.app.R
import com.blacklist.app.data.local.entity.BlacklistRuleEntity
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
    val rules by vm.filteredRules.collectAsState()
    val query by vm.query.collectAsState()
    val error by vm.error.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }

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
            if (list.isEmpty() && rules.isEmpty()) {
                EmptyState(Icons.Filled.Block, stringResource(R.string.blacklist_empty), stringResource(R.string.blacklist_empty_desc), modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (rules.isNotEmpty()) {
                        item(key = "hdr_rules") {
                            Text(stringResource(R.string.blacklist_rules_section), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                        items(rules, key = { "rule_${it.id}" }) { rule ->
                            RuleCard(
                                rule = rule,
                                onToggle = { vm.toggleRule(rule.id, it) },
                                onDelete = { vm.removeRule(rule.id) }
                            )
                        }
                    }
                    if (list.isNotEmpty()) {
                        item(key = "hdr_numbers") {
                            Text(stringResource(R.string.blacklist_numbers_section), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                        items(list, key = { "num_${it.id}" }) { item ->
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
        }
    }
    if (showAdd) {
        AddRuleDialog(
            onDismiss = { showAdd = false },
            onSaveRule = { rule ->
                vm.addRule(rule)
                showAdd = false
            },
            onPickFromPicker = { showPicker = true }
        )
    }
    if (showPicker) {
        PickerDialog(onDismiss = { showPicker = false }, onPick = { item ->
            // Picked numbers land as exact blocked entries
            vm.add(item.number, item.name)
            showPicker = false
        })
    }
}

/** Returns localized label for a rule type. */
@Composable
fun ruleTypeLabel(type: String): String = when (type) {
    BlacklistRuleEntity.TYPE_EXACT -> stringResource(R.string.rule_type_exact)
    BlacklistRuleEntity.TYPE_PREFIX -> stringResource(R.string.rule_type_prefix)
    BlacklistRuleEntity.TYPE_SUFFIX -> stringResource(R.string.rule_type_suffix)
    BlacklistRuleEntity.TYPE_CONTAINS -> stringResource(R.string.rule_type_contains)
    BlacklistRuleEntity.TYPE_RANGE -> stringResource(R.string.rule_type_range)
    BlacklistRuleEntity.TYPE_COUNTRY -> stringResource(R.string.rule_type_country)
    BlacklistRuleEntity.TYPE_HIDDEN -> stringResource(R.string.rule_type_hidden)
    BlacklistRuleEntity.TYPE_UNKNOWN -> stringResource(R.string.rule_type_unknown)
    else -> type
}

private fun typeLabelRes(type: String): Int = when (type) {
    BlacklistRuleEntity.TYPE_EXACT -> R.string.rule_type_exact
    BlacklistRuleEntity.TYPE_PREFIX -> R.string.rule_type_prefix
    BlacklistRuleEntity.TYPE_SUFFIX -> R.string.rule_type_suffix
    BlacklistRuleEntity.TYPE_CONTAINS -> R.string.rule_type_contains
    BlacklistRuleEntity.TYPE_RANGE -> R.string.rule_type_range
    BlacklistRuleEntity.TYPE_COUNTRY -> R.string.rule_type_country
    else -> R.string.rule_type_exact
}

@Composable
private fun RuleCard(rule: BlacklistRuleEntity, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        when (rule.ruleType) {
                            BlacklistRuleEntity.TYPE_PREFIX -> Icons.Filled.CallMade
                            BlacklistRuleEntity.TYPE_SUFFIX -> Icons.Filled.CallReceived
                            BlacklistRuleEntity.TYPE_CONTAINS -> Icons.Filled.Grain
                            BlacklistRuleEntity.TYPE_RANGE -> Icons.Filled.SwapVert
                            BlacklistRuleEntity.TYPE_COUNTRY -> Icons.Filled.Public
                            else -> Icons.Filled.Block
                        },
                        null, tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = {},
                        label = { Text(ruleTypeLabel(rule.ruleType), style = MaterialTheme.typography.labelSmall) },
                        enabled = false
                    )
                }
                Text(
                    when (rule.ruleType) {
                        BlacklistRuleEntity.TYPE_RANGE -> "${rule.startNumber} … ${rule.endNumber}"
                        BlacklistRuleEntity.TYPE_COUNTRY -> rule.countryIso ?: ""
                        else -> rule.pattern ?: ""
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Switch(checked = rule.isEnabled, onCheckedChange = onToggle)
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddRuleDialog(
    onDismiss: () -> Unit,
    onSaveRule: (BlacklistRuleEntity) -> Unit,
    onPickFromPicker: () -> Unit
) {
    var selectedType by remember { mutableStateOf(BlacklistRuleEntity.TYPE_EXACT) }
    var pattern by remember { mutableStateOf("") }
    var rangeStart by remember { mutableStateOf("") }
    var rangeEnd by remember { mutableStateOf("") }
    var countryIso by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }

    fun buildRule(): BlacklistRuleEntity? {
        return when (selectedType) {
            BlacklistRuleEntity.TYPE_RANGE -> {
                val s = rangeStart.filter { it.isDigit() }
                val e = rangeEnd.filter { it.isDigit() }
                if (s.isEmpty() || e.isEmpty()) null else if (s.length != e.length || s >= e) null else
                    BlacklistRuleEntity(ruleType = selectedType, startNumber = s, endNumber = e, displayName = displayName.takeIf { it.isNotBlank() })
            }
            BlacklistRuleEntity.TYPE_COUNTRY -> {
                val iso = countryIso.trim().uppercase()
                if (iso.length != 2 || iso.any { !it.isLetter() }) null else
                    BlacklistRuleEntity(ruleType = selectedType, countryIso = iso, displayName = displayName.takeIf { it.isNotBlank() })
            }
            else -> {
                val p = pattern.trim()
                if (p.isBlank()) null else
                    BlacklistRuleEntity(ruleType = selectedType, pattern = p, displayName = displayName.takeIf { it.isNotBlank() })
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.blacklist_add_rule_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.blacklist_match_type), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BlacklistRuleEntity.USER_TYPES.forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(stringResource(typeLabelRes(type)), style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                when (selectedType) {
                    BlacklistRuleEntity.TYPE_RANGE -> {
                        OutlinedTextField(value = rangeStart, onValueChange = { rangeStart = it }, label = { Text(stringResource(R.string.blacklist_range_start)) }, placeholder = { Text("500000000") }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = rangeEnd, onValueChange = { rangeEnd = it }, label = { Text(stringResource(R.string.blacklist_range_end)) }, placeholder = { Text("599999999") }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true, modifier = Modifier.fillMaxWidth())
                        Text(stringResource(R.string.blacklist_range_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    BlacklistRuleEntity.TYPE_COUNTRY -> {
                        OutlinedTextField(value = countryIso, onValueChange = { countryIso = it }, label = { Text(stringResource(R.string.blacklist_country_hint)) }, placeholder = { Text("DE") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    else -> {
                        OutlinedTextField(
                            value = pattern,
                            onValueChange = { pattern = it },
                            label = { Text(stringResource(R.string.blacklist_pattern_label)) },
                            placeholder = {
                                Text(
                                    when (selectedType) {
                                        BlacklistRuleEntity.TYPE_PREFIX -> "+1233…"
                                        BlacklistRuleEntity.TYPE_SUFFIX -> "…1234"
                                        BlacklistRuleEntity.TYPE_CONTAINS -> "900"
                                        else -> "+123456789"
                                    }
                                )
                            },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (selectedType == BlacklistRuleEntity.TYPE_PREFIX || selectedType == BlacklistRuleEntity.TYPE_SUFFIX || selectedType == BlacklistRuleEntity.TYPE_CONTAINS) {
                            Text(stringResource(R.string.blacklist_pattern_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                OutlinedTextField(value = displayName, onValueChange = { displayName = it }, label = { Text(stringResource(R.string.blacklist_add_name_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (selectedType == BlacklistRuleEntity.TYPE_EXACT) {
                    HorizontalDivider()
                    Text(stringResource(R.string.blacklist_pick_source), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = onPickFromPicker, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Contacts, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.blacklist_add_from_contacts), style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(onClick = onPickFromPicker, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.History, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.blacklist_add_from_log), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            val ready = when (selectedType) {
                BlacklistRuleEntity.TYPE_RANGE -> rangeStart.filter { it.isDigit() }.isNotEmpty() && rangeEnd.filter { it.isDigit() }.isNotEmpty()
                BlacklistRuleEntity.TYPE_COUNTRY -> countryIso.trim().length == 2
                else -> pattern.isNotBlank()
            }
            Button(
                onClick = {
                    val rule = buildRule()
                    if (rule != null) {
                        onSaveRule(rule)
                        pattern = ""; rangeStart = ""; rangeEnd = ""; countryIso = ""; displayName = ""
                    }
                },
                enabled = ready
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
