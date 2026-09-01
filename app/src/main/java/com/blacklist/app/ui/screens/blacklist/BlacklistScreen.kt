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
import com.blacklist.app.data.local.entity.ScheduleRuleEntity
import com.blacklist.app.di.ServiceLocator
import com.blacklist.app.di.ViewModelFactory
import com.blacklist.app.domain.engine.BlacklistRuleConflictAnalyzer
import com.blacklist.app.domain.engine.DecisionTraceInterpreter
import com.blacklist.app.domain.engine.RuleConflictKind
import com.blacklist.app.domain.engine.RuleConflictPreview
import com.blacklist.app.domain.engine.RuleConflictWinner
import com.blacklist.app.domain.engine.TemporaryExactBlockPolicy
import com.blacklist.app.domain.engine.TemporaryFirewall
import com.blacklist.app.domain.model.Decision
import com.blacklist.app.domain.model.EnforcementDecision
import com.blacklist.app.ui.components.EmptyState
import com.blacklist.app.ui.components.PickerDialog
import com.blacklist.app.ui.components.PickerSource
import kotlinx.coroutines.flow.collect
import java.text.DateFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlacklistScreen(nav: NavController) {
    val ctx = LocalContext.current
    val repo = remember { ServiceLocator.provideRepository(ctx) }
    val normalizer = remember { ServiceLocator.provideNormalizer(ctx) }
    val conflictAnalyzer = remember { BlacklistRuleConflictAnalyzer(normalizer) }
    val vm: BlacklistViewModel = viewModel(factory = ViewModelFactory(repo, ctx))
    val list by vm.filtered.collectAsState()
    val rules by vm.filteredRules.collectAsState()
    val allRules by vm.rules.collectAsState()
    val temporaryExactBlocks by vm.temporaryExactBlocks.collectAsState()
    val query by vm.query.collectAsState()
    val error by vm.error.collectAsState()
    val draftPreview by vm.draftRulePreview.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAdd by remember { mutableStateOf(false) }
    var showTemporaryBlock by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var pickerSource by remember { mutableStateOf(PickerSource.CONTACTS) }

    LaunchedEffect(vm) {
        vm.temporaryExactBlockEvents.collect { event ->
            val message = when (event) {
                TemporaryExactBlockEvent.Added -> R.string.temporary_exact_block_added
                TemporaryExactBlockEvent.InvalidNumber -> R.string.temporary_exact_block_invalid_number
                TemporaryExactBlockEvent.LimitReached -> R.string.temporary_exact_block_limit_reached
                TemporaryExactBlockEvent.Failed -> R.string.temporary_exact_block_failed
            }
            snackbarHostState.showSnackbar(ctx.getString(message))
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.blacklist_title), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { showTemporaryBlock = true }) {
                        Icon(Icons.Filled.Timer, stringResource(R.string.temporary_exact_block_action))
                    }
                    IconButton(onClick = { showAdd = true }) { Icon(Icons.Filled.PersonAdd, null) }
                }
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
            if (list.isEmpty() && rules.isEmpty() && temporaryExactBlocks.isEmpty()) {
                EmptyState(Icons.Filled.Block, stringResource(R.string.blacklist_empty), stringResource(R.string.blacklist_empty_desc), modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (temporaryExactBlocks.isNotEmpty()) {
                        item(key = "hdr_temporary_rules") {
                            Text(stringResource(R.string.temporary_exact_block_active_section), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                        items(temporaryExactBlocks, key = { "temp_rule_${it.id}" }) { rule ->
                            TemporaryExactBlockCard(
                                rule = rule,
                                onCancel = { vm.cancelTemporaryExactBlock(rule.id) }
                            )
                        }
                    }
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
                                        if (!item.displayName.isNullOrBlank()) {
                                            Text(item.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                            Text(item.rawNumber, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        } else {
                                            Text(item.rawNumber, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        }
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
    if (showTemporaryBlock) {
        TemporaryExactBlockDialog(
            onDismiss = { showTemporaryBlock = false },
            onConfirm = { number, duration ->
                vm.addTemporaryExactBlock(number, duration)
                showTemporaryBlock = false
            }
        )
    }
    if (showAdd) {
        AddRuleDialog(
            existingRules = allRules,
            conflictAnalyzer = conflictAnalyzer,
            previewState = draftPreview,
            onPreview = vm::previewDraftRule,
            onClearPreview = vm::clearDraftRulePreview,
            onDismiss = {
                vm.clearDraftRulePreview()
                showAdd = false
            },
            onSaveRule = { rule ->
                vm.addRule(rule)
                vm.clearDraftRulePreview()
                showAdd = false
            },
            onPickSource = { source ->
                pickerSource = source
                showPicker = true
            }
        )
    }
    if (showPicker) {
        PickerDialog(
            initialSource = pickerSource,
            onDismiss = { showPicker = false },
            onConfirm = { selected ->
                vm.addAll(selected)
                showPicker = false
                showAdd = false
            }
        )
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
    BlacklistRuleEntity.TYPE_INTERNATIONAL -> stringResource(R.string.rule_type_international)
    BlacklistRuleEntity.TYPE_HIDDEN -> stringResource(R.string.rule_type_hidden)
    BlacklistRuleEntity.TYPE_UNKNOWN -> stringResource(R.string.rule_type_unknown)
    else -> type
}

/** A stored rule defaults to rejection unless it explicitly opts into ringtone silence. */
@Composable
fun ruleEnforcementLabel(enforcement: String): String = when (enforcement) {
    BlacklistRuleEntity.ENFORCEMENT_SILENCE -> stringResource(R.string.rule_enforcement_silence)
    else -> stringResource(R.string.rule_enforcement_block)
}

private fun typeLabelRes(type: String): Int = when (type) {
    BlacklistRuleEntity.TYPE_EXACT -> R.string.rule_type_exact
    BlacklistRuleEntity.TYPE_PREFIX -> R.string.rule_type_prefix
    BlacklistRuleEntity.TYPE_SUFFIX -> R.string.rule_type_suffix
    BlacklistRuleEntity.TYPE_CONTAINS -> R.string.rule_type_contains
    BlacklistRuleEntity.TYPE_RANGE -> R.string.rule_type_range
    BlacklistRuleEntity.TYPE_COUNTRY -> R.string.rule_type_country
    BlacklistRuleEntity.TYPE_INTERNATIONAL -> R.string.rule_type_international
    else -> R.string.rule_type_exact
}

@Composable
private fun TemporaryExactBlockCard(rule: BlacklistRuleEntity, onCancel: () -> Unit) {
    val expiry = TemporaryFirewall.expiryOf(rule) ?: 0L
    ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Timer, null, tint = MaterialTheme.colorScheme.onErrorContainer) }
            }
            Column(Modifier.weight(1f)) {
                Text(rule.pattern.orEmpty(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(
                        R.string.temporary_exact_block_ends_at,
                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(java.util.Date(expiry))
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onCancel) { Text(stringResource(R.string.temporary_exact_block_cancel)) }
        }
    }
}

@Composable
private fun TemporaryExactBlockDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Long) -> Unit
) {
    var number by remember { mutableStateOf("") }
    var selectedDuration by remember { mutableLongStateOf(TemporaryExactBlockPolicy.HOUR_1) }
    var manualDurationEnabled by remember { mutableStateOf(false) }
    var manualDurationMinutes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.temporary_exact_block_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.temporary_exact_block_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it },
                    label = { Text(stringResource(R.string.temporary_exact_block_number)) },
                    placeholder = { Text("+49 151 12345678") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(stringResource(R.string.temporary_exact_block_duration), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                TemporaryExactBlockPolicy.supportedDurationsMs.forEach { duration ->
                    FilterChip(
                        selected = !manualDurationEnabled && selectedDuration == duration,
                        onClick = { manualDurationEnabled = false; selectedDuration = duration },
                        label = { Text(temporaryExactBlockDurationLabel(duration)) },
                        leadingIcon = { if (!manualDurationEnabled && selectedDuration == duration) Icon(Icons.Filled.Check, null, modifier = Modifier.size(16.dp)) }
                    )
                }
                FilterChip(
                    selected = manualDurationEnabled,
                    onClick = { manualDurationEnabled = true },
                    label = { Text(stringResource(R.string.temporary_exact_block_duration_custom)) },
                    leadingIcon = { if (manualDurationEnabled) Icon(Icons.Filled.Check, null, modifier = Modifier.size(16.dp)) }
                )
                if (manualDurationEnabled) {
                    OutlinedTextField(
                        value = manualDurationMinutes,
                        onValueChange = { manualDurationMinutes = it.filter(Char::isDigit) },
                        label = { Text(stringResource(R.string.temporary_exact_block_duration_custom_minutes)) },
                        placeholder = { Text("90") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            val duration = if (manualDurationEnabled) {
                TemporaryExactBlockPolicy.manualDurationMs(manualDurationMinutes.toLongOrNull() ?: -1L)
            } else {
                selectedDuration
            }
            Button(onClick = { onConfirm(number, duration ?: selectedDuration) }, enabled = number.isNotBlank() && duration != null) {
                Text(stringResource(R.string.temporary_exact_block_action))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
private fun temporaryExactBlockDurationLabel(durationMs: Long): String = when (durationMs) {
    TemporaryExactBlockPolicy.HOUR_1 -> stringResource(R.string.temporary_exact_block_duration_hour)
    TemporaryExactBlockPolicy.DAY_1 -> stringResource(R.string.temporary_exact_block_duration_day)
    TemporaryExactBlockPolicy.DAYS_7 -> stringResource(R.string.temporary_exact_block_duration_week)
    TemporaryExactBlockPolicy.DAYS_30 -> stringResource(R.string.temporary_exact_block_duration_month)
    else -> ""
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
                            BlacklistRuleEntity.TYPE_COUNTRY, BlacklistRuleEntity.TYPE_INTERNATIONAL -> Icons.Filled.Public
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
                    AssistChip(
                        onClick = {},
                        label = { Text(ruleEnforcementLabel(rule.enforcement), style = MaterialTheme.typography.labelSmall) },
                        enabled = false
                    )
                }
                Text(
                    when (rule.ruleType) {
                        BlacklistRuleEntity.TYPE_RANGE -> "${rule.startNumber} … ${rule.endNumber}"
                        BlacklistRuleEntity.TYPE_COUNTRY -> rule.countryIso ?: ""
                        BlacklistRuleEntity.TYPE_INTERNATIONAL -> stringResource(R.string.rule_type_international)
                        BlacklistRuleEntity.TYPE_HIDDEN, BlacklistRuleEntity.TYPE_UNKNOWN -> stringResource(typeLabelRes(rule.ruleType))
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
    existingRules: List<BlacklistRuleEntity>,
    conflictAnalyzer: BlacklistRuleConflictAnalyzer,
    previewState: DraftRulePreviewState,
    onPreview: (BlacklistRuleEntity, String) -> Unit,
    onClearPreview: () -> Unit,
    onDismiss: () -> Unit,
    onSaveRule: (BlacklistRuleEntity) -> Unit,
    onPickSource: (PickerSource) -> Unit
) {
    var selectedType by remember { mutableStateOf(BlacklistRuleEntity.TYPE_EXACT) }
    var selectedEnforcement by remember { mutableStateOf(BlacklistRuleEntity.ENFORCEMENT_BLOCK) }
    var pattern by remember { mutableStateOf("") }
    var rangeStart by remember { mutableStateOf("") }
    var rangeEnd by remember { mutableStateOf("") }
    var countryIso by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var testNumber by remember { mutableStateOf("") }
    var scheduleEnabled by remember { mutableStateOf(false) }
    var scheduleStart by remember { mutableStateOf("09:00") }
    var scheduleEnd by remember { mutableStateOf("17:00") }
    var scheduleDays by remember { mutableIntStateOf(ScheduleRuleEntity.ALL_DAYS) }

    fun parseScheduleTime(value: String): Int? {
        val parts = value.trim().split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        return if (hour in 0..23 && minute in 0..59) hour * 60 + minute else null
    }

    fun withSchedule(rule: BlacklistRuleEntity): BlacklistRuleEntity? {
        if (!scheduleEnabled) return rule
        val start = parseScheduleTime(scheduleStart) ?: return null
        val end = parseScheduleTime(scheduleEnd) ?: return null
        if (scheduleDays !in 1..ScheduleRuleEntity.ALL_DAYS) return null
        return rule.copy(
            scheduleEnabled = true,
            scheduleStartMinutes = start,
            scheduleEndMinutes = end,
            scheduleDaysOfWeek = scheduleDays
        )
    }

    fun buildRule(): BlacklistRuleEntity? {
        return when (selectedType) {
            BlacklistRuleEntity.TYPE_RANGE -> {
                val s = rangeStart.filter { it.isDigit() }
                val e = rangeEnd.filter { it.isDigit() }
                if (s.isEmpty() || e.isEmpty()) null else if (s.length != e.length || s >= e) null else
                    withSchedule(BlacklistRuleEntity(ruleType = selectedType, enforcement = selectedEnforcement, startNumber = s, endNumber = e, displayName = displayName.takeIf { it.isNotBlank() }))
            }
            BlacklistRuleEntity.TYPE_COUNTRY -> {
                val iso = countryIso.trim().uppercase()
                if (iso.length != 2 || iso.any { !it.isLetter() }) null else
                    withSchedule(BlacklistRuleEntity(ruleType = selectedType, enforcement = selectedEnforcement, countryIso = iso, displayName = displayName.takeIf { it.isNotBlank() }))
            }
            BlacklistRuleEntity.TYPE_INTERNATIONAL,
            BlacklistRuleEntity.TYPE_HIDDEN,
            BlacklistRuleEntity.TYPE_UNKNOWN ->
                withSchedule(BlacklistRuleEntity(ruleType = selectedType, enforcement = selectedEnforcement, displayName = displayName.takeIf { it.isNotBlank() }))
            else -> {
                val p = pattern.trim()
                if (p.isBlank()) null else
                    withSchedule(BlacklistRuleEntity(ruleType = selectedType, enforcement = selectedEnforcement, pattern = p, displayName = displayName.takeIf { it.isNotBlank() }))
            }
        }
    }

    val draft = buildRule()
    val conflictPreview = draft?.let {
        conflictAnalyzer.analyze(it, existingRules, MAX_RULES_FOR_LIVE_PREVIEW)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.blacklist_add_rule_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.blacklist_match_type), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    (BlacklistRuleEntity.USER_TYPES + listOf(BlacklistRuleEntity.TYPE_HIDDEN, BlacklistRuleEntity.TYPE_UNKNOWN)).forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = {
                                selectedType = type
                                onClearPreview()
                            },
                            label = { Text(stringResource(typeLabelRes(type)), style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Text(stringResource(R.string.rule_enforcement_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BlacklistRuleEntity.USER_ENFORCEMENTS.forEach { enforcement ->
                        FilterChip(
                            selected = selectedEnforcement == enforcement,
                            onClick = {
                                selectedEnforcement = enforcement
                                onClearPreview()
                            },
                            label = { Text(ruleEnforcementLabel(enforcement), style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Text(
                    stringResource(
                        if (selectedEnforcement == BlacklistRuleEntity.ENFORCEMENT_SILENCE) {
                            R.string.rule_enforcement_silence_description
                        } else {
                            R.string.rule_enforcement_block_description
                        }
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                when (selectedType) {
                    BlacklistRuleEntity.TYPE_RANGE -> {
                        OutlinedTextField(value = rangeStart, onValueChange = { rangeStart = it; onClearPreview() }, label = { Text(stringResource(R.string.blacklist_range_start)) }, placeholder = { Text("500000000") }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = rangeEnd, onValueChange = { rangeEnd = it; onClearPreview() }, label = { Text(stringResource(R.string.blacklist_range_end)) }, placeholder = { Text("599999999") }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true, modifier = Modifier.fillMaxWidth())
                        Text(stringResource(R.string.blacklist_range_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    BlacklistRuleEntity.TYPE_COUNTRY -> {
                        OutlinedTextField(value = countryIso, onValueChange = { countryIso = it; onClearPreview() }, label = { Text(stringResource(R.string.blacklist_country_hint)) }, placeholder = { Text("DE") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    BlacklistRuleEntity.TYPE_HIDDEN, BlacklistRuleEntity.TYPE_UNKNOWN -> {
                        Text(
                            stringResource(
                                if (selectedType == BlacklistRuleEntity.TYPE_HIDDEN) {
                                    R.string.blacklist_hidden_rule_hint
                                } else {
                                    R.string.blacklist_unknown_rule_hint
                                }
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
                        OutlinedTextField(
                            value = pattern,
                            onValueChange = {
                                pattern = it
                                onClearPreview()
                            },
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
                if (conflictPreview != null) {
                    RuleConflictPreviewCard(conflictPreview)
                }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.schedule_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.schedule_desc), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = scheduleEnabled, onCheckedChange = { scheduleEnabled = it; onClearPreview() })
                }
                if (scheduleEnabled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = scheduleStart,
                            onValueChange = { scheduleStart = it; onClearPreview() },
                            label = { Text(stringResource(R.string.schedule_start_time)) },
                            placeholder = { Text("09:00") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = scheduleEnd,
                            onValueChange = { scheduleEnd = it; onClearPreview() },
                            label = { Text(stringResource(R.string.schedule_end_time)) },
                            placeholder = { Text("17:00") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(stringResource(R.string.schedule_days), style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(
                            ScheduleRuleEntity.MON to R.string.day_mon,
                            ScheduleRuleEntity.TUE to R.string.day_tue,
                            ScheduleRuleEntity.WED to R.string.day_wed,
                            ScheduleRuleEntity.THU to R.string.day_thu,
                            ScheduleRuleEntity.FRI to R.string.day_fri,
                            ScheduleRuleEntity.SAT to R.string.day_sat,
                            ScheduleRuleEntity.SUN to R.string.day_sun
                        ).forEach { (bit, labelRes) ->
                            FilterChip(
                                selected = scheduleDays and bit != 0,
                                onClick = {
                                    scheduleDays = if (scheduleDays and bit != 0) scheduleDays and bit.inv() else scheduleDays or bit
                                    onClearPreview()
                                },
                                label = { Text(stringResource(labelRes), style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
                OutlinedTextField(value = displayName, onValueChange = { displayName = it }, label = { Text(stringResource(R.string.blacklist_add_name_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                HorizontalDivider()
                Text(stringResource(R.string.draft_decision_preview_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.draft_decision_preview_description), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = testNumber,
                    onValueChange = {
                        testNumber = it
                        onClearPreview()
                    },
                    label = { Text(stringResource(R.string.draft_decision_preview_number_label)) },
                    placeholder = { Text(stringResource(R.string.draft_decision_preview_number_placeholder)) },
                    supportingText = { Text(stringResource(R.string.draft_decision_preview_number_hint)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { draft?.let { onPreview(it, testNumber) } },
                    enabled = draft != null && testNumber.isNotBlank() && !previewState.isPreviewing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (previewState.isPreviewing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.draft_decision_preview_run))
                }
                previewState.error?.let { error ->
                    Text(
                        stringResource(
                            if (error is DraftRulePreviewError.InvalidNumber) {
                                R.string.draft_decision_preview_invalid_number
                            } else {
                                R.string.draft_decision_preview_failed
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (previewState.result != null) {
                    DraftDecisionPreviewCard(previewState.result)
                }
                if (selectedType == BlacklistRuleEntity.TYPE_EXACT) {
                    HorizontalDivider()
                    Text(stringResource(R.string.blacklist_pick_source), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(onClick = { onPickSource(PickerSource.CONTACTS) }) {
                            Icon(Icons.Filled.Contacts, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.blacklist_add_from_contacts), style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(onClick = { onPickSource(PickerSource.CALL_LOG) }) {
                            Icon(Icons.Filled.Call, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.blacklist_add_from_log), style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(onClick = { onPickSource(PickerSource.MESSAGES) }) {
                            Icon(Icons.Filled.Message, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.blacklist_add_from_messages), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            val ready = when (selectedType) {
                BlacklistRuleEntity.TYPE_RANGE -> rangeStart.filter { it.isDigit() }.isNotEmpty() && rangeEnd.filter { it.isDigit() }.isNotEmpty()
                BlacklistRuleEntity.TYPE_COUNTRY -> countryIso.trim().length == 2
                BlacklistRuleEntity.TYPE_INTERNATIONAL, BlacklistRuleEntity.TYPE_HIDDEN, BlacklistRuleEntity.TYPE_UNKNOWN -> true
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
                enabled = ready && (!scheduleEnabled || (parseScheduleTime(scheduleStart) != null && parseScheduleTime(scheduleEnd) != null && scheduleDays in 1..ScheduleRuleEntity.ALL_DAYS)) && conflictPreview?.hasDuplicate != true
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
private fun RuleConflictPreviewCard(preview: RuleConflictPreview) {
    val hasConflicts = preview.conflicts.isNotEmpty()
    val colors = CardDefaults.cardColors(
        containerColor = when {
            preview.hasDuplicate -> MaterialTheme.colorScheme.errorContainer
            hasConflicts -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        }
    )
    ElevatedCard(colors = colors, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.rule_conflict_preview_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            if (!hasConflicts) {
                Text(
                    stringResource(R.string.rule_conflict_preview_clean),
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Text(
                    stringResource(R.string.rule_conflict_preview_summary, preview.conflicts.size),
                    style = MaterialTheme.typography.bodySmall
                )
                preview.conflicts.take(MAX_PREVIEWED_CONFLICTS).forEach { conflict ->
                    Text(
                        stringResource(
                            R.string.rule_conflict_preview_item,
                            conflict.existingRule.id,
                            stringResource(conflictKindLabelRes(conflict.kind)),
                            stringResource(conflictWinnerLabelRes(conflict.winner))
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (preview.conflicts.size > MAX_PREVIEWED_CONFLICTS) {
                    Text(
                        stringResource(
                            R.string.rule_conflict_preview_more,
                            preview.conflicts.size - MAX_PREVIEWED_CONFLICTS
                        ),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                if (preview.isTruncated) {
                    Text(
                        stringResource(
                            R.string.rule_conflict_preview_truncated,
                            preview.inspectedRuleCount,
                            preview.activeRuleCount
                        ),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                if (preview.hasDuplicate) {
                    Text(
                        stringResource(R.string.rule_conflict_duplicate_not_saved),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Text(
                stringResource(R.string.rule_conflict_preview_read_only),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun conflictKindLabelRes(kind: RuleConflictKind): Int = when (kind) {
    RuleConflictKind.DUPLICATE -> R.string.rule_conflict_kind_duplicate
    RuleConflictKind.OVERLAP -> R.string.rule_conflict_kind_overlap
}

private fun conflictWinnerLabelRes(winner: RuleConflictWinner): Int = when (winner) {
    RuleConflictWinner.DRAFT -> R.string.rule_conflict_winner_draft
    RuleConflictWinner.EXISTING -> R.string.rule_conflict_winner_existing
}

private const val MAX_PREVIEWED_CONFLICTS = 3
private const val MAX_RULES_FOR_LIVE_PREVIEW = 200

@Composable
private fun DraftDecisionPreviewCard(result: EnforcementDecision) {
    val (container, content, title) = when (result.decision) {
        Decision.BLOCK -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            R.string.draft_decision_preview_result_block
        )
        Decision.SILENCE -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            R.string.draft_decision_preview_result_silence
        )
        Decision.ALLOW -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            R.string.draft_decision_preview_result_allow
        )
    }
    Card(colors = CardDefaults.cardColors(containerColor = container), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = content
            )
            Text(result.explainable.summary, style = MaterialTheme.typography.bodySmall, color = content)
            result.explainable.details.take(MAX_PREVIEW_REASONS).forEach { reason ->
                Text("• $reason", style = MaterialTheme.typography.bodySmall, color = content)
            }
            if (result.explainable.matchedRuleIds.isNotEmpty()) {
                Text(
                    stringResource(
                        R.string.draft_decision_preview_matched_rules,
                        result.explainable.matchedRuleIds.joinToString()
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = content
                )
            }
            HorizontalDivider(color = content.copy(alpha = 0.25f))
            Text(
                stringResource(R.string.draft_decision_trace_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = content
            )
            val trace = remember(result.explainable.backend) {
                DecisionTraceInterpreter.forDecision(result)
            }
            trace.entries.forEach { entry ->
                Text(
                    "• ${decisionTraceStageLabel(entry.stage)} — ${decisionTraceStateLabel(entry.state)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = content
                )
            }
            Text(
                stringResource(R.string.draft_decision_preview_read_only),
                style = MaterialTheme.typography.labelSmall,
                color = content
            )
        }
    }
}

@Composable
private fun decisionTraceStageLabel(stage: DecisionTraceInterpreter.Stage): String = stringResource(
    when (stage) {
        DecisionTraceInterpreter.Stage.EMERGENCY -> R.string.draft_decision_trace_emergency
        DecisionTraceInterpreter.Stage.BEHAVIOR_SIGNALS -> R.string.draft_decision_trace_behavior
        DecisionTraceInterpreter.Stage.TEMPORARY_ALLOW -> R.string.draft_decision_trace_temporary_allow
        DecisionTraceInterpreter.Stage.WHITELIST -> R.string.draft_decision_trace_whitelist
        DecisionTraceInterpreter.Stage.TEMPORARY_EXACT_BLOCK -> R.string.draft_decision_trace_temporary_block
        DecisionTraceInterpreter.Stage.PERSISTENT_BLACKLIST -> R.string.draft_decision_trace_blacklist
        DecisionTraceInterpreter.Stage.LEGACY_BLACKLIST -> R.string.draft_decision_trace_legacy
        DecisionTraceInterpreter.Stage.OUTBOUND_CALLBACK_GRACE -> R.string.draft_decision_trace_outbound_grace
        DecisionTraceInterpreter.Stage.EMERGENCY_CALLBACK_GRACE -> R.string.draft_decision_trace_emergency_grace
        DecisionTraceInterpreter.Stage.SCHEDULE -> R.string.draft_decision_trace_schedule
        DecisionTraceInterpreter.Stage.TEMPORARY_FIREWALL -> R.string.draft_decision_trace_firewall
        DecisionTraceInterpreter.Stage.BROAD_POLICY -> R.string.draft_decision_trace_policy
        DecisionTraceInterpreter.Stage.REPUTATION_AND_RISK -> R.string.draft_decision_trace_risk
        DecisionTraceInterpreter.Stage.DEFAULT_ALLOW -> R.string.draft_decision_trace_default_allow
    }
)

@Composable
private fun decisionTraceStateLabel(state: DecisionTraceInterpreter.State): String = stringResource(
    when (state) {
        DecisionTraceInterpreter.State.PASSED -> R.string.draft_decision_trace_passed
        DecisionTraceInterpreter.State.DECISIVE -> R.string.draft_decision_trace_decisive
        DecisionTraceInterpreter.State.NOT_REACHED -> R.string.draft_decision_trace_not_reached
    }
)

private const val MAX_PREVIEW_REASONS = 4
