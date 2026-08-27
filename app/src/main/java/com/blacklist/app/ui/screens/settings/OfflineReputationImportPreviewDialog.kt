package com.blacklist.app.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.blacklist.app.R

@Composable
fun OfflineReputationImportPreviewDialog(
    pending: SettingsViewModel.PendingOfflineReputationImport,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val preview = pending.preview
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.offline_reputation_preview_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.offline_reputation_preview_desc), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.offline_reputation_source_value, preview.sourceName), style = MaterialTheme.typography.bodyMedium)
                preview.sourceVersion?.let { Text(stringResource(R.string.offline_reputation_version_value, it), style = MaterialTheme.typography.bodySmall) }
                preview.sourceUrl?.let { Text(stringResource(R.string.offline_reputation_url_value, it), style = MaterialTheme.typography.bodySmall) }
                Text(
                    stringResource(R.string.offline_reputation_fingerprint_value, preview.fingerprintSha256.take(FINGERPRINT_PREFIX_LENGTH)),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.offline_reputation_preview_counts, preview.rows.size, preview.highRiskRows, preview.duplicateRows, preview.invalidRows))
                preview.rows.take(SAMPLE_SIZE).forEach { row ->
                    Text("${row.rawNumber} · ${row.riskScore}${row.category?.let { " · $it" }.orEmpty()}", modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = preview.rows.isNotEmpty()) {
                Text(stringResource(R.string.offline_reputation_import_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

private const val SAMPLE_SIZE = 5
private const val FINGERPRINT_PREFIX_LENGTH = 16
