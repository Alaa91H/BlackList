package com.blacklist.app.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blacklist.app.R
import com.blacklist.app.ui.screens.settings.SettingsViewModel.PendingCsvImport

@Composable
fun CsvImportPreviewDialog(
    pending: PendingCsvImport,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val preview = pending.preview
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.csv_import_preview_title)) },
        text = {
            Column {
                Text(
                    stringResource(
                        R.string.csv_import_preview_summary,
                        preview.rows.size,
                        preview.duplicateRows,
                        preview.invalidRows
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    stringResource(R.string.csv_import_preview_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                )
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)) {
                    items(preview.rows.take(20), key = { "${it.number}-${it.displayName}" }) { row ->
                        Column(Modifier.padding(vertical = 4.dp)) {
                            row.displayName?.let { Text(it, fontWeight = FontWeight.SemiBold) }
                            Text(row.number, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = preview.rows.isNotEmpty()) {
                Text(stringResource(R.string.csv_import_confirm, preview.rows.size))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
