package net.helcel.cowspent.android.helper

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import net.helcel.cowspent.theme.ThemeUtils
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import net.helcel.cowspent.R
import net.helcel.cowspent.android.main.MainConstants
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class QrCodeScannerActivity : AppCompatActivity() {
    private lateinit var cameraExecutor: ExecutorService
    private var isScanning = true

    override fun onCreate(state: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(state)
        
        cameraExecutor = Executors.newSingleThreadExecutor()

        setContent {
            ThemeUtils.CowspentTheme {
                QrCodeScannerScreen(
                    onBack = { finish() },
                    onResult = { handleResult(it) },
                    cameraExecutor = cameraExecutor,
                    isScanning = isScanning,
                    onRequestPermission = {
                        ActivityCompat.requestPermissions(
                            this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
                        )
                    },
                    hasPermission = allPermissionsGranted()
                )
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                // Permissions granted, the UI will recompose and start camera
            } else {
                finish()
            }
        }
    }

    private fun handleResult(result: Result) {
        if (!isScanning) return
        isScanning = false
        
        Log.v(TAG, "QR result " + result.text)

        val intent = Intent()
        intent.putExtra(MainConstants.KEY_QR_CODE, result.text)
        setResult(RESULT_OK, intent)
        finish()
    }

    companion object {
        private val TAG = QrCodeScannerActivity::class.java.simpleName
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}

@Composable
fun QrCodeScannerScreen(
    onBack: () -> Unit,
    onResult: (Result) -> Unit,
    cameraExecutor: ExecutorService,
    isScanning: Boolean,
    onRequestPermission: () -> Unit,
    hasPermission: Boolean
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scan_qrcode)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                backgroundColor = MaterialTheme.colors.surface,
                elevation = 0.dp
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (hasPermission) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { previewView ->
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                        cameraProviderFuture.addListener({
                            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
                            val preview = androidx.camera.core.Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }

                            val imageAnalyzer = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also {
                                    it.setAnalyzer(cameraExecutor, QrCodeAnalyzer { result ->
                                        if (isScanning) {
                                            onResult(result)
                                        }
                                    })
                                }

                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageAnalyzer
                                )
                            } catch (exc: Exception) {
                                Log.e("QrCodeScannerScreen", "Use case binding failed", exc)
                            }
                        }, ContextCompat.getMainExecutor(context))
                    }
                )
            } else {
                LaunchedEffect(Unit) {
                    onRequestPermission()
                }
            }
        }
    }
}

private class QrCodeAnalyzer(private val onQrCodeScanned: (Result) -> Unit) : ImageAnalysis.Analyzer {
    private val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        val buffer = image.planes[0].buffer
        val data = ByteArray(buffer.remaining())
        buffer.get(data)
        
        val source = PlanarYUVLuminanceSource(
            data, image.width, image.height, 0, 0, image.width, image.height, false
        )
        val binarizer = HybridBinarizer(source)
        val binaryBitmap = BinaryBitmap(binarizer)

        try {
            val result = reader.decode(binaryBitmap)
            onQrCodeScanned(result)
        } catch (_: Exception) {
            // No QR code found
        } finally {
            image.close()
        }
    }
}

@Preview(showBackground = true)
@Composable
fun QrCodeScannerScreenPreview() {
    MaterialTheme {
        QrCodeScannerScreen(
            onBack = {},
            onResult = {},
            cameraExecutor = Executors.newSingleThreadExecutor(),
            isScanning = true,
            onRequestPermission = {},
            hasPermission = true
        )
    }
}
