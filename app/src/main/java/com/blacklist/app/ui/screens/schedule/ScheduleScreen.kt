package com.blacklist.app.ui.screens.schedule

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
import com.blacklist.app.data.local.entity.ScheduleRuleEntity
import com.blacklist.app.di.ServiceLocator
import com.blacklist.app.di.ViewModelFactory
import com.blacklist.app.ui.components.EmptyState
import com.blacklist.app.util.ScheduleEvaluator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(nav: NavController) {
    val ctx = LocalContext.current
    val repo = remember { ServiceLocator.provideRepository(ctx) }
    val vm: ScheduleViewModel = viewModel(factory = ViewModelFactory(repo))
    val rules by vm.rules.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.schedule_title), fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }, actions = { IconButton(onClick = { showAdd = true }) { Icon(Icons.Filled.Add, null) } })
        },
        floatingActionButton = { FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Filled.Add, null) } }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.padding(16.dp).fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.Info, null)
                    Text(stringResource(R.string.schedule_desc), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (rules.isEmpty()) EmptyState(Icons.Filled.Schedule, stringResource(R.string.schedule_no_rules), stringResource(R.string.schedule_no_rules_desc))
            else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(rules, key = { it.id }) { rule ->
                    ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().animateItem()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Filled.Timer, null, tint = MaterialTheme.colorScheme.primary)
                                    Text("${ScheduleEvaluator.formatMinutes(rule.startMinutes)} — ${ScheduleEvaluator.formatMinutes(rule.endMinutes)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                }
                                Switch(checked = rule.isEnabled, onCheckedChange = { vm.toggle(rule) })
                            }
                            Text(
                                when (rule.daysOfWeek) {
                                    ScheduleRuleEntity.ALL_DAYS -> stringResource(R.string.schedule_every_day)
                                    ScheduleRuleEntity.WEEKDAYS -> stringResource(R.string.schedule_weekdays)
                                    ScheduleRuleEntity.WEEKEND -> stringResource(R.string.schedule_weekend)
                                    else -> ScheduleEvaluator.formatDays(rule.daysOfWeek)
                                }, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary
                            )
                            AssistChip(onClick = {}, label = { Text(modeLabel(rule.mode)) }, leadingIcon = { Icon(Icons.Filled.Shield, null, modifier = Modifier.size(16.dp)) })
                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) { TextButton(onClick = { vm.delete(rule) }) { Icon(Icons.Filled.Delete, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.schedule_delete)) } }
                        }
                    }
                }
            }
        }
    }
    if (showAdd) ScheduleAddDialog(onDismiss = { showAdd = false }, onSave = { vm.add(it); showAdd = false })
}
@Composable
private fun modeLabel(m: String) = when (m) {
    ScheduleRuleEntity.MODE_ALL -> "Block All Calls"
    ScheduleRuleEntity.MODE_ALL_EXCEPT_WHITELIST -> "Block All Except Whitelist"
    ScheduleRuleEntity.MODE_UNKNOWN_PRIVATE -> "Block Unknown & Private"
    ScheduleRuleEntity.MODE_BLACKLIST -> "Blacklist Only"
    else -> m
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun modeLabelLocalized(m: String): String {
    // This is for chips display - will be replaced via stringResource in caller if needed
    return m
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleAddDialog(onDismiss: () -> Unit, onSave: (ScheduleRuleEntity)->Unit) {
    var startH by remember { mutableIntStateOf(22) }
    var startM by remember { mutableIntStateOf(0) }
    var endH by remember { mutableIntStateOf(6) }
    var endM by remember { mutableIntStateOf(0) }
    var days by remember { mutableIntStateOf(ScheduleRuleEntity.ALL_DAYS) }
    var mode by remember { mutableStateOf(ScheduleRuleEntity.MODE_ALL_EXCEPT_WHITELIST) }
    var expanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(stringResource(R.string.schedule_add_rule)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = startH.toString(), onValueChange = { startH = it.toIntOrNull()?.coerceIn(0,23) ?: startH }, label = { Text(stringResource(R.string.schedule_start_h)) }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = startM.toString(), onValueChange = { startM = it.toIntOrNull()?.coerceIn(0,59) ?: startM }, label = { Text(stringResource(R.string.schedule_start_m)) }, modifier = Modifier.weight(1f), singleLine = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = endH.toString(), onValueChange = { endH = it.toIntOrNull()?.coerceIn(0,23) ?: endH }, label = { Text(stringResource(R.string.schedule_end_h)) }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = endM.toString(), onValueChange = { endM = it.toIntOrNull()?.coerceIn(0,59) ?: endM }, label = { Text(stringResource(R.string.schedule_end_m)) }, modifier = Modifier.weight(1f), singleLine = true)
                }
                Text(stringResource(R.string.schedule_days), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DayChip(stringResource(R.string.day_mon), days and ScheduleRuleEntity.MON !=0) { days = days xor ScheduleRuleEntity.MON }
                    DayChip(stringResource(R.string.day_tue), days and ScheduleRuleEntity.TUE !=0) { days = days xor ScheduleRuleEntity.TUE }
                    DayChip(stringResource(R.string.day_wed), days and ScheduleRuleEntity.WED !=0) { days = days xor ScheduleRuleEntity.WED }
                    DayChip(stringResource(R.string.day_thu), days and ScheduleRuleEntity.THU !=0) { days = days xor ScheduleRuleEntity.THU }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DayChip(stringResource(R.string.day_fri), days and ScheduleRuleEntity.FRI !=0) { days = days xor ScheduleRuleEntity.FRI }
                    DayChip(stringResource(R.string.day_sat), days and ScheduleRuleEntity.SAT !=0) { days = days xor ScheduleRuleEntity.SAT }
                    DayChip(stringResource(R.string.day_sun), days and ScheduleRuleEntity.SUN !=0) { days = days xor ScheduleRuleEntity.SUN }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    AssistChip(onClick = { days = ScheduleRuleEntity.ALL_DAYS }, label = { Text(stringResource(R.string.schedule_chip_all)) })
                    AssistChip(onClick = { days = ScheduleRuleEntity.WEEKDAYS }, label = { Text(stringResource(R.string.schedule_chip_weekdays)) })
                    AssistChip(onClick = { days = ScheduleRuleEntity.WEEKEND }, label = { Text(stringResource(R.string.schedule_chip_weekend)) })
                }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = when(mode){
                            ScheduleRuleEntity.MODE_ALL -> stringResource(R.string.schedule_block_all)
                            ScheduleRuleEntity.MODE_ALL_EXCEPT_WHITELIST -> stringResource(R.string.schedule_block_all_except_whitelist)
                            ScheduleRuleEntity.MODE_UNKNOWN_PRIVATE -> stringResource(R.string.schedule_block_unknown_private)
                            else -> stringResource(R.string.schedule_mode_blacklist)
                        },
                        onValueChange = {}, readOnly = true, label = { Text(stringResource(R.string.schedule_mode)) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.schedule_block_all)) }, onClick = { mode = ScheduleRuleEntity.MODE_ALL; expanded=false })
                        DropdownMenuItem(text = { Text(stringResource(R.string.schedule_block_all_except_whitelist)) }, onClick = { mode = ScheduleRuleEntity.MODE_ALL_EXCEPT_WHITELIST; expanded=false })
                        DropdownMenuItem(text = { Text(stringResource(R.string.schedule_block_unknown_private)) }, onClick = { mode = ScheduleRuleEntity.MODE_UNKNOWN_PRIVATE; expanded=false })
                        DropdownMenuItem(text = { Text(stringResource(R.string.schedule_mode_blacklist)) }, onClick = { mode = ScheduleRuleEntity.MODE_BLACKLIST; expanded=false })
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(ScheduleRuleEntity(startMinutes = startH*60+startM, endMinutes = endH*60+endM, daysOfWeek = if(days==0) ScheduleRuleEntity.ALL_DAYS else days, mode = mode)) }) { Text(stringResource(R.string.schedule_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } })
}
@Composable private fun DayChip(label: String, selected: Boolean, onClick: ()->Unit) { FilterChip(selected = selected, onClick = onClick, label = { Text(label, style = MaterialTheme.typography.labelSmall) }) }
