package com.blacklist.app.ui.screens.permissions

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.blacklist.app.di.ServiceLocator
import com.blacklist.app.domain.capability.CapabilityDescriptor
import com.blacklist.app.domain.capability.CapabilityState
import com.blacklist.app.domain.permission.PermissionDescriptor
import com.blacklist.app.domain.permission.PermissionCategory
import com.blacklist.app.domain.permission.PermissionState
import kotlinx.coroutines.launch

private fun CapabilityState.label(): String = when (this) {
    CapabilityState.AVAILABLE -> "Available"
    CapabilityState.DEGRADED -> "Degraded"
    CapabilityState.BLOCKED -> "Blocked"
    CapabilityState.UNAVAILABLE -> "Unavailable"
    CapabilityState.NOT_SUPPORTED -> "Not Supported"
    CapabilityState.REQUIRES_ACTION -> "Action Needed"
    CapabilityState.UNKNOWN -> "Unknown"
}

private fun PermissionState.label(): String = when (this) {
    PermissionState.GRANTED -> "Granted"
    PermissionState.DENIED_CAN_REQUEST -> "Can Request"
    PermissionState.DENIED_PERMANENT -> "Permanently Denied"
    PermissionState.RESTRICTED -> "Restricted"
    PermissionState.NOT_APPLICABLE -> "N/A"
    PermissionState.AVAILABLE -> "Available"
    PermissionState.REQUIRES_ACTION -> "Action Needed"
    else -> "Unknown"
}

@Composable
fun stateColor(state: Any): Color = when (state) {
    is CapabilityState -> when (state) {
        CapabilityState.AVAILABLE -> MaterialTheme.colorScheme.primary
        CapabilityState.DEGRADED, CapabilityState.REQUIRES_ACTION -> MaterialTheme.colorScheme.tertiary
        CapabilityState.BLOCKED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    }
    is PermissionState -> when (state) {
        PermissionState.GRANTED -> MaterialTheme.colorScheme.primary
        PermissionState.DENIED_CAN_REQUEST, PermissionState.REQUIRES_ACTION -> MaterialTheme.colorScheme.tertiary
        PermissionState.DENIED_PERMANENT -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    }
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionCenterScreen(nav: NavController) {
    val ctx = LocalContext.current
    val permManager = remember { ServiceLocator.providePermissionManager(ctx) }
    val capManager = remember { ServiceLocator.provideCapabilityManager(ctx) }

    val permStates by permManager.permissionStates.collectAsState()
    val capStates by capManager.capabilityStates.collectAsState()
    val scope = rememberCoroutineScope()
    var refreshTick by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var pendingRuntimePermission by remember { mutableStateOf<PermissionDescriptor?>(null) }

    fun refreshAll() {
        scope.launch {
            busy = true
            try {
                permManager.refresh()
                capManager.refresh()
                refreshTick++
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(Unit) { refreshAll() }

    val runtimeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refreshAll() }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refreshAll() }

    fun fixPermission(desc: PermissionDescriptor) {
        when {
            desc.role -> {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val rm = ctx.getSystemService(RoleManager::class.java)
                        roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
                    } else {
                        permManager.openSettings(desc.id, ctx)
                    }
                } catch (_: Exception) {
                    permManager.openSettings(desc.id, ctx)
                    refreshAll()
                }
            }
            desc.runtime -> pendingRuntimePermission = desc
            else -> {
                permManager.openSettings(desc.id, ctx)
                refreshAll()
            }
        }
    }

    pendingRuntimePermission?.let { permission ->
        AlertDialog(
            onDismissRequest = { pendingRuntimePermission = null },
            title = { Text("Grant ${permission.name}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(permission.rationale)
                    Text("This is optional. Call blocking continues if you decline.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(onClick = {
                    pendingRuntimePermission = null
                    runtimeLauncher.launch(arrayOf(permission.id))
                }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { pendingRuntimePermission = null }) { Text("Not now") } }
        )
    }

    val capabilities = capManager.descriptors
    val requiredCaps = capabilities.filter { it.required && !it.optional }
    val optionalCaps = capabilities.filter { !it.required || it.optional }
    val health = remember(refreshTick) { capManager.getHealthPercentage() }

    val perms = permManager.descriptors
    val runtimePerms = perms.filter { it.runtime }
    val rolePerms = perms.filter { it.role }
    val specialPerms = perms.filter { it.specialAccess }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Permission Center", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { refreshAll() }, enabled = !busy) {
                        Icon(Icons.Filled.Refresh, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "health") {
                ElevatedCard(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = if (health >= 100) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Row(Modifier.padding(24.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.VerifiedUser, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(48.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Protection Readiness", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("$health% Ready", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                        }
                        CircularProgressIndicator(progress = health / 100f, modifier = Modifier.size(48.dp))
                    }
                }
            }

            if (requiredCaps.isNotEmpty()) {
                item(key = "hdr_required") { SectionHeader("Required Capabilities") }
                items(requiredCaps, key = { "cap_${it.id}" }) { cap ->
                    CapabilityCard(
                        capability = cap,
                        state = capStates[cap.id] ?: CapabilityState.UNKNOWN,
                        onFix = {
                            scope.launch {
                                try { capManager.openSettings(cap.id, ctx) } catch (_: Exception) {}
                                refreshAll()
                            }
                        }
                    )
                }
            }

            if (rolePerms.isNotEmpty()) {
                item(key = "hdr_roles") { SectionHeader("Roles") }
                items(rolePerms, key = { "perm_${it.id}" }) { perm ->
                    PermissionCard(permission = perm, state = permStates[perm.id] ?: PermissionState.UNKNOWN, onFix = { fixPermission(perm) })
                }
            }

            if (runtimePerms.isNotEmpty()) {
                item(key = "hdr_runtime") { SectionHeader("Runtime Permissions") }
                items(runtimePerms, key = { "perm_${it.id}" }) { perm ->
                    PermissionCard(permission = perm, state = permStates[perm.id] ?: PermissionState.UNKNOWN, onFix = { fixPermission(perm) })
                }
            }

            if (specialPerms.isNotEmpty()) {
                item(key = "hdr_special") { SectionHeader("Special Access") }
                items(specialPerms, key = { "perm_${it.id}" }) { perm ->
                    PermissionCard(permission = perm, state = permStates[perm.id] ?: PermissionState.UNKNOWN, onFix = { fixPermission(perm) })
                }
            }

            if (optionalCaps.isNotEmpty()) {
                item(key = "hdr_optional") { SectionHeader("Optional Capabilities") }
                items(optionalCaps, key = { "cap_${it.id}" }) { cap ->
                    CapabilityCard(
                        capability = cap,
                        state = capStates[cap.id] ?: CapabilityState.UNKNOWN,
                        onFix = {
                            scope.launch {
                                try { capManager.openSettings(cap.id, ctx) } catch (_: Exception) {}
                                refreshAll()
                            }
                        }
                    )
                }
            }

            item(key = "spacer") { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
fun CapabilityCard(
    capability: CapabilityDescriptor,
    state: CapabilityState,
    onFix: () -> Unit
) {
    val color = stateColor(state)
    val icon = when (state) {
        CapabilityState.AVAILABLE -> Icons.Filled.CheckCircle
        CapabilityState.DEGRADED -> Icons.Filled.Warning
        CapabilityState.BLOCKED -> Icons.Filled.Block
        CapabilityState.UNAVAILABLE -> Icons.Filled.RemoveCircleOutline
        CapabilityState.NOT_SUPPORTED -> Icons.Filled.DoNotDisturb
        CapabilityState.REQUIRES_ACTION -> Icons.Filled.Info
        CapabilityState.UNKNOWN -> Icons.Filled.HelpOutline
    }
    val needsFix = state == CapabilityState.REQUIRES_ACTION || state == CapabilityState.BLOCKED ||
        state == CapabilityState.UNAVAILABLE || state == CapabilityState.NOT_SUPPORTED

    ElevatedCard(shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f)) {
                Text(capability.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (capability.description.isNotBlank()) {
                    Text(capability.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!capability.remediation.isBlank() && needsFix) {
                    Text(capability.remediation, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                }
            }
            Badge(containerColor = color.copy(alpha = 0.2f)) {
                Text(state.label(), color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
            }
            if (needsFix) {
                TextButton(onClick = onFix) { Text("Fix", style = MaterialTheme.typography.labelMedium) }
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
    val color = stateColor(state)
    val icon = when (state) {
        PermissionState.GRANTED -> Icons.Filled.CheckCircle
        PermissionState.DENIED_CAN_REQUEST -> Icons.Filled.Warning
        PermissionState.DENIED_PERMANENT -> Icons.Filled.Block
        PermissionState.RESTRICTED -> Icons.Filled.Lock
        PermissionState.NOT_APPLICABLE -> Icons.Filled.DoNotDisturb
        else -> Icons.Filled.HelpOutline
    }
    val needsFix = state == PermissionState.DENIED_CAN_REQUEST || state == PermissionState.REQUIRES_ACTION ||
        state == PermissionState.DENIED_PERMANENT

    ElevatedCard(shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f)) {
                Text(permission.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(permission.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (permission.required) {
                    Text("Required", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                } else if (permission.optional) {
                    Text("Optional", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Badge(containerColor = color.copy(alpha = 0.2f)) {
                Text(state.label(), color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
            }
            if (needsFix) {
                TextButton(onClick = onFix) {
                    Text(if (state == PermissionState.DENIED_PERMANENT) "Settings" else "Grant", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
