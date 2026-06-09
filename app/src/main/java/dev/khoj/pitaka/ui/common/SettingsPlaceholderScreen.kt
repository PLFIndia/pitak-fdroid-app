package dev.khoj.pitaka.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.khoj.pitaka.R

/**
 * Settings placeholder for Phase 3. Real Settings screen (D27) lands in
 * Phase 7. Until then, this surfaces the two Phase-3 affordances (Export
 * and Import) so the user has somewhere to reach them from the bottom nav.
 *
 * D26 in spirit: the screen isn't a terminal dead-end even as a placeholder.
 */
@Composable
fun SettingsPlaceholderScreen(
    onExport: () -> Unit,
    onImport: () -> Unit,
    onPublish: () -> Unit,
    onBackup: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.nav_settings),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.placeholder_settings),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.export_title))
            }
            OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.import_title))
            }
            OutlinedButton(onClick = onPublish, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.publish_title))
            }
            OutlinedButton(onClick = onBackup, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.backup_title))
            }
        }
    }
}
