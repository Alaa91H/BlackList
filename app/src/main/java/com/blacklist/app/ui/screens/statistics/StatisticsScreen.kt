package com.blacklist.app.ui.screens.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.blacklist.app.di.ServiceLocator
import com.blacklist.app.domain.analytics.FirewallStatistics
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(nav: NavController) {
    val ctx = LocalContext.current
    var stats by remember { mutableStateOf(FirewallStatistics()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        scope.launch {
            ServiceLocator.provideStatisticsEngine(ctx).observe().collect { stats = it }
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Statistics", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } })
        }
    ) { pad ->
        LazyColumn(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                ElevatedCard(shape = RoundedCornerShape(24.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Protection: ${stats.protectionScore}%", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        LinearProgressIndicator(progress = { stats.protectionScore / 100f }, modifier = Modifier.fillMaxWidth())
                        Text("Block rate ${(stats.blockRate * 100).toInt()}% • High risk ${stats.highRisk}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Stat("Blocked", "${stats.blocked}", Icons.Filled.Block, Modifier.weight(1f))
                    Stat("Allowed", "${stats.allowed}", Icons.Filled.Check, Modifier.weight(1f))
                    Stat("Spam", "${stats.spam}", Icons.Filled.Warning, Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Stat("Unknown", "${stats.unknown}", Icons.Filled.PersonOff, Modifier.weight(1f))
                    Stat("Hidden", "${stats.hidden}", Icons.Filled.VisibilityOff, Modifier.weight(1f))
                    Stat("High Risk", "${stats.highRisk}", Icons.Filled.Shield, Modifier.weight(1f))
                }
            }
            item {
                Text("Top Blocked Prefixes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (stats.topBlockedPrefixes.isEmpty()) Text("No data yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    stats.topBlockedPrefixes.forEach { (p, c) -> Text("$p — $c blocks", style = MaterialTheme.typography.bodyMedium) }
                }
            }
            item {
                Text("Top Blocked Numbers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (stats.topBlockedNumbers.isEmpty()) Text("No data yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    stats.topBlockedNumbers.forEach { (n, c) -> Text("$n — $c", style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}

@Composable private fun Stat(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = modifier) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.labelSmall)
        }
    }
}
