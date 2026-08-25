package com.blacklist.app.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blacklist.app.R
import com.blacklist.app.domain.model.ProtectionProfilePreset
import com.blacklist.app.domain.model.ProtectionProfiles

@Composable
fun ProtectionProfileCard(
    activeProfileId: String,
    onSelect: (ProtectionProfilePreset) -> Unit
) {
    ElevatedCard(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                stringResource(R.string.settings_protection_profiles),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                stringResource(R.string.settings_protection_profiles_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ProtectionProfiles.presets.forEach { profile ->
                FilterChip(
                    selected = activeProfileId == profile.id,
                    onClick = { onSelect(profile) },
                    label = { Text(profileLabel(profile.id)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (activeProfileId == ProtectionProfiles.CUSTOM) {
                Text(
                    stringResource(R.string.settings_protection_custom),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun profileLabel(id: String): String = when (id) {
    ProtectionProfiles.NORMAL -> stringResource(R.string.settings_profile_normal)
    ProtectionProfiles.FOCUS -> stringResource(R.string.settings_profile_focus)
    ProtectionProfiles.WHITELIST_ONLY -> stringResource(R.string.settings_profile_whitelist_only)
    else -> stringResource(R.string.settings_protection_custom)
}
