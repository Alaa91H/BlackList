package com.blacklist.app.ui.screens.sharednumber

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.blacklist.app.domain.engine.TemporaryExactBlockPolicy
import kotlinx.coroutines.flow.collect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedNumberScreen(
    nav: NavController,
    sharedText: CharSequence?,
    onSharedTextConsumed: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { ServiceLocator.provideRepository(context) }
    val vm: SharedNumberViewModel = viewModel(factory = ViewModelFactory(repo, context.applicationContext))
    val candidates by vm.candidates.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedNumber by remember { mutableStateOf<String?>(null) }
    var action by remember { mutableStateOf(SharedNumberAction.BLOCK) }
    var temporaryDuration by remember { mutableLongStateOf(TemporaryExactBlockPolicy.HOUR_1) }

    LaunchedEffect(sharedText) {
        vm.loadSharedText(sharedText)
    }
    LaunchedEffect(candidates) {
        if (selectedNumber !in candidates) selectedNumber = candidates.firstOrNull()
    }
    LaunchedEffect(vm) {
        vm.events.collect { event ->
            val message = when (event) {
                SharedNumberEvent.Applied -> R.string.shared_number_applied
                SharedNumberEvent.InvalidSelection -> R.string.shared_number_invalid_selection
                SharedNumberEvent.Failed -> R.string.shared_number_failed
            }
            snackbarHostState.showSnackbar(context.getString(message))
            if (event is SharedNumberEvent.Applied) {
                onSharedTextConsumed()
                nav.popBackStack()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.shared_number_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        onSharedTextConsumed()
                        nav.popBackStack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_cancel))
                    }
                }
            )
        }
    ) { padding ->
        if (candidates.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Filled.IosShare, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.shared_number_empty_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.shared_number_empty_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = {
                    onSharedTextConsumed()
                    nav.popBackStack()
                }) { Text(stringResource(R.string.action_cancel)) }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.shared_number_privacy_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.shared_number_privacy_description), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item { Text(stringResource(R.string.shared_number_choose_number), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                items(candidates, key = { it }) { number ->
                    AssistChip(
                        onClick = { selectedNumber = number },
                        label = { Text(number) },
                        leadingIcon = {
                            Icon(
                                if (selectedNumber == number) Icons.Filled.Check else Icons.Filled.Phone,
                                null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.shared_number_choose_action), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        SharedAction.values().forEach { choice ->
                            FilterChip(
                                selected = action == choice.action,
                                onClick = { action = choice.action },
                                label = { Text(stringResource(choice.labelRes)) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                if (action == SharedNumberAction.TEMPORARY_BLOCK) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.temporary_exact_block_duration), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            TemporaryExactBlockPolicy.supportedDurationsMs.forEach { duration ->
                                FilterChip(
                                    selected = temporaryDuration == duration,
                                    onClick = { temporaryDuration = duration },
                                    label = { Text(temporaryExactBlockDurationLabel(duration)) }
                                )
                            }
                        }
                    }
                }
                item {
                    Button(
                        onClick = { selectedNumber?.let { vm.apply(it, action, temporaryDuration) } },
                        enabled = selectedNumber != null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.shared_number_confirm))
                    }
                }
            }
        }
    }
}

private enum class SharedAction(val action: SharedNumberAction, val labelRes: Int) {
    BLOCK(SharedNumberAction.BLOCK, R.string.shared_number_action_block),
    ALLOW(SharedNumberAction.ALLOW, R.string.shared_number_action_allow),
    TEMPORARY_BLOCK(SharedNumberAction.TEMPORARY_BLOCK, R.string.shared_number_action_temporary_block)
}

@Composable
private fun temporaryExactBlockDurationLabel(durationMs: Long): String = when (durationMs) {
    TemporaryExactBlockPolicy.HOUR_1 -> stringResource(R.string.temporary_exact_block_duration_hour)
    TemporaryExactBlockPolicy.DAY_1 -> stringResource(R.string.temporary_exact_block_duration_day)
    TemporaryExactBlockPolicy.DAYS_7 -> stringResource(R.string.temporary_exact_block_duration_week)
    TemporaryExactBlockPolicy.DAYS_30 -> stringResource(R.string.temporary_exact_block_duration_month)
    else -> ""
}
