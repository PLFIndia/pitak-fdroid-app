package dev.khoj.pitaka.ui.merge

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import dev.khoj.pitaka.R
import dev.khoj.pitaka.data.scanner.ZxingQrAnalyzer
import java.util.concurrent.Executors

/**
 * Camera screen that scans another Pitak app's library QR (PLAN-merge.md D40).
 * Reuses the CameraX + ML Kit + permission pattern from the ISBN ScannerScreen,
 * but configured for QR_CODE and validated by [QrLibraryIdAnalyzer] so only a
 * genuine `pitaka-lib:<id>` code is accepted. Hands the validated ID back via
 * [onLibraryIdScanned]; an arbitrary QR is silently ignored.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun QrScanScreen(
    onBack: () -> Unit,
    onLibraryIdScanned: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.merge_scan_qr_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        when (val status = cameraPermission.status) {
            PermissionStatus.Granted -> QrCameraPreview(
                onScanned = onLibraryIdScanned,
                contentPadding = padding,
            )
            is PermissionStatus.Denied -> Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        stringResource(R.string.merge_scan_qr_permission_rationale),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    if (!status.shouldShowRationale) {
                        Button(onClick = {
                            ctx.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", ctx.packageName, null),
                                )
                            )
                        }) { Text(stringResource(R.string.scanner_permission_open_settings)) }
                    } else {
                        Button(onClick = { cameraPermission.launchPermissionRequest() }) {
                            Text(stringResource(R.string.scanner_permission_grant))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QrCameraPreview(
    onScanned: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember {
        PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
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
                ZxingQrAnalyzer(onLibraryIdScanned = onScanned),
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
                // §1.1: degrade gracefully — empty preview, user can back out.
            }
        }, ContextCompat.getMainExecutor(ctx))

        onDispose {
            cameraProviderFuture.get().unbindAll()
            executor.shutdown()
        }
    }

    Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.merge_scan_qr_hint),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

