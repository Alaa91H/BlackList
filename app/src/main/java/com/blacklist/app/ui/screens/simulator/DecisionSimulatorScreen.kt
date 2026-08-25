package com.blacklist.app.ui.screens.simulator

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.blacklist.app.R
import com.blacklist.app.di.ServiceLocator
import com.blacklist.app.di.ViewModelFactory
import com.blacklist.app.domain.model.Decision
import com.blacklist.app.domain.model.EnforcementDecision

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecisionSimulatorScreen(navController: NavHostController) {
    val context = LocalContext.current
    val viewModel: DecisionSimulatorViewModel = viewModel(
        factory = ViewModelFactory(ServiceLocator.provideRepository(context), context)
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var number by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.simulator_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                stringResource(R.string.simulator_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = number,
                onValueChange = { number = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.simulator_number_label)) },
                placeholder = { Text(stringResource(R.string.simulator_number_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.simulate(number) }),
                supportingText = { Text(stringResource(R.string.simulator_number_hint)) }
            )
            Button(
                onClick = { viewModel.simulate(number) },
                enabled = number.isNotBlank() && !state.isEvaluating,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isEvaluating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                } else {
                    Icon(Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.simulator_run))
            }
            state.error?.let { error ->
                AssistChip(
                    onClick = viewModel::clear,
                    label = { Text(error) },
                    colors = AssistChipDefaults.assistChipColors(
                        labelColor = MaterialTheme.colorScheme.onErrorContainer,
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                )
            }
            state.result?.let { result -> DecisionResultCard(result) }
        }
    }
}

@Composable
private fun DecisionResultCard(result: EnforcementDecision) {
    val blocked = result.decision != Decision.ALLOW
    val container = if (blocked) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    val content = if (blocked) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
    Card(colors = CardDefaults.cardColors(containerColor = container), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(if (blocked) R.string.simulator_result_block else R.string.simulator_result_allow),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = content
            )
            Text(result.explainable.summary, style = MaterialTheme.typography.bodyMedium, color = content)
            HorizontalDivider(color = content.copy(alpha = 0.25f))
            Text(stringResource(R.string.simulator_reasons), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = content)
            result.explainable.details.forEach { reason ->
                Text("• $reason", style = MaterialTheme.typography.bodyMedium, color = content)
            }
            if (result.explainable.matchedRuleIds.isNotEmpty()) {
                Text(
                    stringResource(R.string.simulator_matched_rules, result.explainable.matchedRuleIds.joinToString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = content
                )
            }
            Text(
                stringResource(R.string.simulator_read_only),
                style = MaterialTheme.typography.labelMedium,
                color = content
            )
        }
    }
}
