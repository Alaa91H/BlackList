package com.blacklist.app.ui.screens.security

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.blacklist.app.di.ServiceLocator
import com.blacklist.app.data.local.entity.SecurityEventEntity
import kotlinx.coroutines.launch
import java.text.DateFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityEventsScreen(nav: NavController) {
    val ctx = LocalContext.current
    var events by remember { mutableStateOf<List<SecurityEventEntity>>(emptyList()) }
    LaunchedEffect(Unit) {
        ServiceLocator.provideDatabase(ctx).securityEventDao().observeAll().collect { events = it }
    }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Security Events", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } })
        }
    ) { pad ->
        if (events.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Shield, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                    Text("No security events", style = MaterialTheme.typography.titleMedium)
                    Text("High-risk campaigns and critical blocks will appear here", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(events) { e ->
                    val color = when (e.severity) {
                        "CRITICAL" -> MaterialTheme.colorScheme.errorContainer
                        "HIGH" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                        "MEDIUM" -> MaterialTheme.colorScheme.tertiaryContainer
                        else -> MaterialTheme.colorScheme.secondaryContainer
                    }
                    ElevatedCard(shape = RoundedCornerShape(16.dp), colors = CardDefaults.elevatedCardColors(containerColor = color)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(when (e.severity) {
                                    "CRITICAL" -> Icons.Filled.Warning
                                    "HIGH" -> Icons.Filled.Error
                                    else -> Icons.Filled.Info
                                }, null)
                                Text(e.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                Badge { Text(e.severity) }
                            }
                            Text(e.description, style = MaterialTheme.typography.bodySmall)
                            e.relatedNumber?.let { Text("Number: $it", style = MaterialTheme.typography.labelSmall) }
                            e.riskScore?.let { Text("Risk: $it/100", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                            Text(DateFormat.getDateTimeInstance().format(e.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
