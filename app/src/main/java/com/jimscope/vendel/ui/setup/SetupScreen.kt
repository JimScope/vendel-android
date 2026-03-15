package com.jimscope.vendel.ui.setup

import android.Manifest
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.jimscope.vendel.ui.theme.VendelBrand
import java.util.concurrent.Executors

@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var cameraPermissionGranted by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraPermissionGranted = granted
    }

    LaunchedEffect(uiState.isConnected) {
        if (uiState.isConnected) {
            onSetupComplete()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "Vendel Gateway",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Conecta tu dispositivo para enviar SMS",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Escanear QR") },
                icon = { Icon(Icons.Default.CameraAlt, contentDescription = null) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Manual") },
                icon = { Icon(Icons.Default.Edit, contentDescription = null) }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        when (selectedTab) {
            0 -> QrScanTab(
                cameraPermissionGranted = cameraPermissionGranted,
                onRequestPermission = {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                },
                onQrScanned = { viewModel.onQrScanned(it) }
            )
            1 -> ManualInputTab(
                serverUrl = uiState.serverUrl,
                apiKey = uiState.apiKey,
                onServerUrlChange = { viewModel.updateServerUrl(it) },
                onApiKeyChange = { viewModel.updateApiKey(it) },
                onConnect = { viewModel.connect() },
                isLoading = uiState.isLoading
            )
        }

        if (uiState.error != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = uiState.error!!,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (uiState.isLoading) {
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator(color = VendelBrand)
        }
    }
}

@Composable
private fun QrScanTab(
    cameraPermissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    onQrScanned: (String) -> Unit
) {
    if (!cameraPermissionGranted) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Se necesita permiso de cámara para escanear el código QR",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(containerColor = VendelBrand)
            ) {
                Text("Permitir cámara")
            }
        }
    } else {
        QrCameraPreview(onQrScanned = onQrScanned)
    }
}

@Composable
private fun QrCameraPreview(onQrScanned: (String) -> Unit) {
    val context = LocalContext.current
    var scanned by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .clip(RoundedCornerShape(16.dp))
    ) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                val executor = Executors.newSingleThreadExecutor()

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(executor) { imageProxy ->
                                if (scanned) {
                                    imageProxy.close()
                                    return@setAnalyzer
                                }

                                val mediaImage = imageProxy.image
                                if (mediaImage != null) {
                                    val inputImage = InputImage.fromMediaImage(
                                        mediaImage,
                                        imageProxy.imageInfo.rotationDegrees
                                    )
                                    val scanner = BarcodeScanning.getClient()
                                    scanner.process(inputImage)
                                        .addOnSuccessListener { barcodes ->
                                            for (barcode in barcodes) {
                                                if (barcode.valueType == Barcode.TYPE_TEXT) {
                                                    barcode.rawValue?.let { raw ->
                                                        if (!scanned) {
                                                            scanned = true
                                                            onQrScanned(raw)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        .addOnCompleteListener {
                                            imageProxy.close()
                                        }
                                } else {
                                    imageProxy.close()
                                }
                            }
                        }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            ctx as androidx.lifecycle.LifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                    } catch (e: Exception) {
                        Log.e("QrCamera", "Camera bind failed", e)
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    Text(
        text = "Escanea el código QR del panel de Vendel",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ManualInputTab(
    serverUrl: String,
    apiKey: String,
    onServerUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onConnect: () -> Unit,
    isLoading: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = serverUrl,
            onValueChange = onServerUrlChange,
            label = { Text("URL del servidor") },
            placeholder = { Text("https://api.vendel.cc") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = { Text("API Key") },
            placeholder = { Text("Tu clave de API del dispositivo") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Button(
            onClick = onConnect,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading && serverUrl.isNotBlank() && apiKey.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = VendelBrand)
        ) {
            Text(
                text = if (isLoading) "Conectando..." else "Conectar",
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}
