package com.blacklist.app.ui.screens.diagnostics

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
import androidx.navigation.NavController
import com.blacklist.app.R
import com.blacklist.app.di.ServiceLocator
import com.blacklist.app.domain.diagnostics.DiagnosticResult
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var results by remember { mutableStateOf<List<DiagnosticResult>>(emptyList()) }
    var running by remember { mutableStateOf(false) }

    fun run() {
        running = true
        scope.launch {
            results = ServiceLocator.provideDiagnosticsService(ctx).runDiagnostics()
            running = false
        }
    }
    LaunchedEffect(Unit) { run() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostics", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = { IconButton(onClick = { run() }) { Icon(Icons.Filled.Refresh, null) } }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ElevatedCard(shape = RoundedCornerShape(20.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.VerifiedUser, null)
                        Text("Run Call Firewall Diagnostics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text("Checks Call Screening, permissions, battery, database, rules, backend and OEM compatibility.", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { run() }, enabled = !running, modifier = Modifier.fillMaxWidth()) {
                        if (running) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (running) "Running..." else "Run Diagnostics")
                    }
                }
            }
            val pass = results.count { it.status == DiagnosticResult.Status.PASS }
            if (results.isNotEmpty()) {
                Text("Protection Health: ${if (results.isEmpty()) 0 else pass * 100 / results.size}%  •  $pass/${results.size} PASS", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                items(results) { r ->
                    ElevatedCard(shape = RoundedCornerShape(16.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(
                                when (r.status) {
                                    DiagnosticResult.Status.PASS -> Icons.Filled.CheckCircle
                                    DiagnosticResult.Status.WARNING -> Icons.Filled.Warning
                                    DiagnosticResult.Status.FAIL -> Icons.Filled.Error
                                },
                                null,
                                tint = when (r.status) {
                                    DiagnosticResult.Status.PASS -> MaterialTheme.colorScheme.primary
                                    DiagnosticResult.Status.WARNING -> MaterialTheme.colorScheme.tertiary
                                    DiagnosticResult.Status.FAIL -> MaterialTheme.colorScheme.error
                                }
                            )
                            Column(Modifier.weight(1f)) {
                                Text(r.check, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(r.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                r.fix?.let { Text("Fix: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                            }
                            Badge(containerColor = when (r.status) {
                                DiagnosticResult.Status.PASS -> MaterialTheme.colorScheme.primaryContainer
                                DiagnosticResult.Status.WARNING -> MaterialTheme.colorScheme.tertiaryContainer
                                DiagnosticResult.Status.FAIL -> MaterialTheme.colorScheme.errorContainer
                            }) { Text(r.status.name) }
                        }
                    }
                }
            }
        }
    }
}
