package com.blacklist.app.ui.screens.about

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.blacklist.app.R
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(nav: NavController) {
    val ctx = LocalContext.current
    val infinite = rememberInfiniteTransition(label = "about")
    val shimmer by infinite.animateFloat(initialValue = 0f, targetValue = 1f, animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Reverse), label = "shimmer")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Hero
            ElevatedCard(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(96.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Shield, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Text("BlackList", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.home_subtitle), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    AssistChip(onClick = {}, label = { Text(stringResource(R.string.about_offline_badge, "1.0.3")) }, leadingIcon = { Icon(Icons.Filled.Verified, null, modifier = Modifier.size(16.dp)) })
                    Text(stringResource(R.string.about_app_desc), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Developer card - luxurious
            ElevatedCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.Person, null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.about_developer), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp)) {
                                Box(contentAlignment = Alignment.Center) { Text("A", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold) }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.about_developer_name), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.about_developer_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    // GitHub card clickable - elegant
                    Card(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Alaa91H/Alaa91H"))
                            ctx.startActivity(intent)
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Filled.Code, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.about_github_label), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.about_github_handle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                            }
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, null)
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Alaa91H/Alaa91H"))
                            ctx.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Link, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.about_github))
                    }
                }
            }

            // Privacy commitment
            ElevatedCard(shape = RoundedCornerShape(24.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Lock, null)
                        Text(stringResource(R.string.about_privacy_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Text(stringResource(R.string.about_privacy_text), style = MaterialTheme.typography.bodySmall)
                }
            }

            // Version & License
            ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.about_version), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("1.0.3", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.about_license), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("MIT", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                    Text(stringResource(R.string.about_license_value), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Text(stringResource(R.string.about_made_with), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            Spacer(Modifier.height(16.dp))
        }
    }
}
