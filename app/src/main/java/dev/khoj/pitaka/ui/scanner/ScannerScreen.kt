package dev.khoj.pitaka.ui.scanner

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import dev.khoj.pitaka.R
import dev.khoj.pitaka.data.scanner.ZxingBarcodeAnalyzer
import dev.khoj.pitaka.ui.contribute.LocalizedText
import java.util.concurrent.Executors

/**
 * CameraX + ML Kit barcode scanner.
 *
 * Permission flow (D26b's "no terminal dead-ends" applied to permissions):
 *  - Granted → live preview, frames analysed for EAN-13.
 *  - Denied  → rationale + grant button.
 *  - Permanently denied → rationale + Settings deep link + a "Enter ISBN
 *                         manually instead" escape hatch (Phase 2 design call).
 *
 * Hands off the accepted ISBN via [onIsbnScanned]; the host route navigates to
 * AddBook with the ISBN pre-filled and Lookup auto-triggered.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ScannerScreen(
    onBack: () -> Unit,
    onIsbnScanned: (String) -> Unit,
    onManualEntry: () -> Unit,
    viewModel: ScannerViewModel = hiltViewModel(),
) {
    val ctx = LocalContext.current
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ScannerEvent.IsbnScanned -> onIsbnScanned(event.isbn)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { LocalizedText(R.string.scanner_title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        when (val status = cameraPermission.status) {
            PermissionStatus.Granted -> {
                CameraPreview(
                    onIsbn = viewModel::onIsbnDetected,
                    onManualEntry = onManualEntry,
                    contentPadding = padding,
                )
            }
            is PermissionStatus.Denied -> {
                PermissionGate(
                    isPermanentlyDenied = !status.shouldShowRationale,
                    onRequest = { cameraPermission.launchPermissionRequest() },
                    onOpenSettings = {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", ctx.packageName, null),
                        )
                        ctx.startActivity(intent)
                    },
                    onManualEntry = onManualEntry,
                    contentPadding = padding,
                )
            }
        }
    }
}

@Composable
private fun CameraPreview(
    onIsbn: (String) -> Unit,
    onManualEntry: () -> Unit,
    contentPadding: PaddingValues,
) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel: ScannerViewModel = hiltViewModel()

    val previewView = remember {
        PreviewView(ctx).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val executor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(
                executor,
                ZxingBarcodeAnalyzer(
                    analyzer = viewModel.analyzer,
                    onIsbnDetected = onIsbn,
                ),
            )

            provider.unbindAll()
            try {
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            } catch (t: Throwable) {
                // §1.1: degrade gracefully. The screen still shows the empty
                // preview surface plus the manual-entry fallback below.
            }
        }, ContextCompat.getMainExecutor(ctx))

        onDispose {
            cameraProviderFuture.get().unbindAll()
            executor.shutdown()
        }
    }

    Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LocalizedText(
                R.string.scanner_aim_hint,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onManualEntry) {
                LocalizedText(
                    R.string.scanner_manual_entry,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun PermissionGate(
    isPermanentlyDenied: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    onManualEntry: () -> Unit,
    contentPadding: PaddingValues,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LocalizedText(
                R.string.scanner_permission_rationale,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            if (isPermanentlyDenied) {
                LocalizedText(
                    R.string.scanner_permission_permanent_denied,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = onOpenSettings) {
                    LocalizedText(R.string.scanner_permission_open_settings)
                }
            } else {
                Button(onClick = onRequest) {
                    LocalizedText(R.string.scanner_permission_grant)
                }
            }
            TextButton(onClick = onManualEntry) {
                LocalizedText(R.string.scanner_manual_entry)
            }
        }
    }
}
