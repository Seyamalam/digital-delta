package com.example.digitaldelta.ui.scanner

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun QrScannerOverlay(
    purpose: QrScanPurpose,
    title: String,
    guidance: String,
    permissionRequired: String,
    wrongCode: String,
    closeLabel: String,
    retryLabel: String,
    onAccepted: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var rejection by remember { mutableStateOf<QrPayloadRejection?>(null) }
    val consumed = remember { AtomicBoolean(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionGranted = it
    }
    val scanner = rememberQrScanner()
    val cameraController = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
        }
    }

    LaunchedEffect(Unit) {
        if (!permissionGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    DisposableEffect(permissionGranted, lifecycleOwner, scanner) {
        if (permissionGranted) {
            val mainExecutor = ContextCompat.getMainExecutor(context)
            cameraController.setImageAnalysisAnalyzer(
                mainExecutor,
                MlKitAnalyzer(
                    listOf(scanner),
                    ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED,
                    mainExecutor,
                ) { result ->
                    val raw = result?.getValue(scanner)?.firstOrNull()?.rawValue
                    when (val gated = QrPayloadGate.accept(raw, purpose)) {
                        is QrPayloadResult.Accepted -> if (consumed.compareAndSet(false, true)) onAccepted(gated.value)
                        is QrPayloadResult.Rejected -> if (gated.reason != QrPayloadRejection.EMPTY) rejection = gated.reason
                    }
                },
            )
            cameraController.bindToLifecycle(lifecycleOwner)
        }
        onDispose {
            cameraController.clearImageAnalysisAnalyzer()
            cameraController.unbind()
        }
    }
    DisposableEffect(scanner) { onDispose { scanner.close() } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color(0xFF061F24))) {
            if (permissionGranted) {
                AndroidView(
                    factory = { previewContext ->
                        PreviewView(previewContext).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            controller = cameraController
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                ScannerFrame()
            }
            Surface(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                color = Color(0xE6073940),
                contentColor = Color.White,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.QrCodeScanner, null)
                    Spacer(Modifier.size(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        Text(guidance, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .76f))
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = closeLabel },
                    ) { Icon(Icons.Default.Close, null) }
                }
            }
            if (!permissionGranted) {
                Surface(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column(
                        Modifier.padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Default.QrCodeScanner, null, modifier = Modifier.size(42.dp))
                        Text(permissionRequired, style = MaterialTheme.typography.titleMedium)
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text(retryLabel) }
                    }
                }
            }
            if (rejection != null) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xF0FFF4EE),
                    contentColor = Color(0xFF8D2E2B),
                ) { Text(wrongCode, Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) }
            }
        }
    }
}

@Composable
private fun rememberQrScanner(): BarcodeScanner = remember {
    BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build(),
    )
}

@Composable
private fun ScannerFrame() {
    val transition = rememberInfiniteTransition(label = "qr-scan-line")
    val scanProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_700), RepeatMode.Reverse),
        label = "qr-scan-progress",
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(278.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(3.dp, Color(0xFF80D8D8), RoundedCornerShape(24.dp)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .offset(y = 18.dp + (236.dp * scanProgress))
                    .background(Color(0xFFFF786F)),
            )
        }
    }
}
