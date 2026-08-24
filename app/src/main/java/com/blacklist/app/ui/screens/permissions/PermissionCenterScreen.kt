package com.blacklist.app.ui.screens.permissions

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
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.blacklist.app.R
import com.blacklist.app.di.ServiceLocator
import com.blacklist.app.domain.capability.CapabilityManager
import com.blacklist.app.domain.capability.CapabilityState
import com.blacklist.app.domain.capability.CapabilityCategory
import com.blacklist.app.domain.permission.PermissionDescriptor
import com.blacklist.app.domain.permission.PermissionManager
import com.blacklist.app.domain.permission.PermissionState
import com.blacklist.app.domain.capability.CapabilityDescriptor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionCenterScreen(nav: NavController) {
    val ctx = LocalContext.current
    val permManager = remember { ServiceLocator.providePermissionManager(ctx) }
    val capManager = remember { ServiceLocator.provideCapabilityManager(ctx) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_permission_center), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = { IconButton(onClick = { }) { Icon(Icons.Filled.Refresh, null) } }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Overall Health
            val health = 0

            ElevatedCard(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(24.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.VerifiedUser, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(48.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Protection Readiness", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("0% Ready", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                        }
                        CircularProgressIndicator(progress = 0f, modifier = Modifier.size(48.dp))
                    }
                    Text("Overall capability health based on required capabilities", style = MaterialTheme.typography.bodySmall)
                }
            }

            // Required Capabilities
            Text("Required Capabilities", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(emptyList<CapabilityDescriptor>()) { cap ->
                    CapabilityCard(
                        capability = cap,
                        state = CapabilityState.UNKNOWN,
                        onFix = { }
                    )
                }
            }

            // Optional Capabilities
            Text("Optional Capabilities", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(emptyList<CapabilityDescriptor>()) { cap ->
                    CapabilityCard(
                        capability = cap,
                        state = CapabilityState.UNKNOWN,
                        onFix = { }
                    )
                }
            }

            // Permission Details
            Text("Runtime Permissions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(emptyList<PermissionDescriptor>()) { perm ->
                    PermissionCard(
                        permission = perm,
                        state = PermissionState.UNKNOWN,
                        onFix = { }
                    )
                }
            }

            // Special Access
            Text("Special Access", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(emptyList<PermissionDescriptor>()) { perm ->
                    PermissionCard(
                        permission = perm,
                        state = PermissionState.UNKNOWN,
                        onFix = { }
                    )
                }
            }

            // Roles
            Text("Roles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(emptyList<PermissionDescriptor>()) { perm ->
                    PermissionCard(
                        permission = perm,
                        state = PermissionState.UNKNOWN,
                        onFix = { }
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun CapabilityCard(
    capability: com.blacklist.app.domain.capability.CapabilityDescriptor,
    state: CapabilityState,
    onFix: () -> Unit
) {
    val (color, icon) = when (state) {
        CapabilityState.AVAILABLE -> MaterialTheme.colorScheme.primary to Icons.Filled.CheckCircle
        CapabilityState.DEGRADED -> MaterialTheme.colorScheme.tertiary to Icons.Filled.Warning
        CapabilityState.BLOCKED -> MaterialTheme.colorScheme.error to Icons.Filled.Block
        CapabilityState.UNAVAILABLE -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) to Icons.Filled.RemoveCircleOutline
        CapabilityState.NOT_SUPPORTED -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f) to Icons.Filled.DoNotDisturb
        CapabilityState.REQUIRES_ACTION -> MaterialTheme.colorScheme.tertiary to Icons.Filled.Info
        CapabilityState.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) to Icons.Filled.HelpOutline
    }

    ElevatedCard(shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f)) {
                Text("Capability", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("Description", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Badge(containerColor = color.copy(alpha = 0.2f)) {
                Text("State", color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
            }
            if (state == CapabilityState.REQUIRES_ACTION || state == CapabilityState.BLOCKED || state == CapabilityState.UNAVAILABLE) {
                TextButton(onClick = { }) {
                    Text("Fix", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
fun PermissionCard(
    permission: PermissionDescriptor,
    state: PermissionState,
    onFix: () -> Unit
) {
    val (color, icon) = when (state) {
        PermissionState.GRANTED -> MaterialTheme.colorScheme.primary to Icons.Filled.CheckCircle
        PermissionState.DENIED_CAN_REQUEST -> MaterialTheme.colorScheme.tertiary to Icons.Filled.Warning
        PermissionState.DENIED_PERMANENT -> MaterialTheme.colorScheme.error to Icons.Filled.Block
        PermissionState.RESTRICTED -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) to Icons.Filled.Lock
        PermissionState.NOT_APPLICABLE -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f) to Icons.Filled.DoNotDisturb
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) to Icons.Filled.HelpOutline
    }

    ElevatedCard(shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f)) {
                Text("Permission", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("Description", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Category", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Badge(containerColor = color.copy(alpha = 0.2f)) {
                Text("State", color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
            }
        }
    }
}