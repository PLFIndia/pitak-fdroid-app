package dev.khoj.pitaka.ui.backup

import android.os.Process
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.khoj.pitaka.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var showRestart by remember { mutableStateOf(false) }

    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri -> uri?.let { viewModel.onBackupDestinationPicked(it) } }

    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.onRestoreSourcePicked(it) } }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                BackupEvent.BackupSaved ->
                    Toast.makeText(ctx, ctx.getString(R.string.backup_saved), Toast.LENGTH_SHORT).show()
                BackupEvent.WrongPassphrase ->
                    Toast.makeText(ctx, ctx.getString(R.string.restore_wrong_passphrase), Toast.LENGTH_LONG).show()
                BackupEvent.RestoreSucceeded -> showRestart = true
                is BackupEvent.Error ->
                    Toast.makeText(ctx, event.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.backup_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            StalenessBlock(state)

            Button(
                onClick = {
                    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                    val filename = ctx.getString(R.string.backup_default_filename, stamp)
                    createDocLauncher.launch(filename)
                },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.busy) CircularProgressIndicator(strokeWidth = 2.dp,
                    modifier = Modifier.height(16.dp))
                else Text(stringResource(R.string.backup_now))
            }

            HorizontalDivider()
            Text(stringResource(R.string.restore_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.restore_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error)
            OutlinedButton(
                onClick = {
                    openDocLauncher.launch(arrayOf("application/octet-stream", "application/zip", "*/*"))
                },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.restore_pick)) }
        }
    }

    if (state.pendingRestoreUri != null) {
        RestorePassphraseDialog(
            onCancel = { viewModel.onCancelRestore() },
            onSubmit = { p -> viewModel.onRestoreWithPassphrase(p) },
        )
    }

    if (showRestart) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.restore_succeeded_title)) },
            text = { Text(stringResource(R.string.restore_succeeded_body)) },
            confirmButton = {
                TextButton(onClick = {
                    // Per design plan: restart-via-dialog-then-kill so Room/Hilt
                    // pick up the swapped DBs cleanly.
                    Process.killProcess(Process.myPid())
                    exitProcess(0)
                }) { Text(stringResource(R.string.restore_succeeded_button)) }
            },
        )
    }
}

@Composable
private fun StalenessBlock(state: BackupUiState) {
    val now = remember { System.currentTimeMillis() }
    if (state.lastBackupAtMs == 0L) {
        Text(stringResource(R.string.backup_last_never), style = MaterialTheme.typography.bodyMedium)
        return
    }
    val ageMs = now - state.lastBackupAtMs
    val ageDays = (ageMs / (24 * 60 * 60 * 1000)).toInt().coerceAtLeast(0)
    val thresholdDays = (state.stalenessThresholdMs / (24 * 60 * 60 * 1000)).toInt()
    if (ageMs > state.stalenessThresholdMs) {
        Text(stringResource(R.string.backup_stale_warning, thresholdDays),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error)
        return
    }
    val text = if (ageDays == 0) stringResource(R.string.backup_last_today)
               else stringResource(R.string.backup_last_n_days_ago, ageDays)
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun RestorePassphraseDialog(
    onCancel: () -> Unit,
    onSubmit: (CharArray) -> Unit,
) {
    var pass by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.restore_passphrase_title)) },
        text = {
            Column {
                Text(stringResource(R.string.restore_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text(stringResource(R.string.restore_passphrase_field)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(pass.toCharArray()); pass = "" },
                enabled = pass.isNotEmpty(),
            ) { Text(stringResource(R.string.restore_passphrase_continue)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.add_book_cancel))
            }
        },
    )
}
