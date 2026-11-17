package com.example.btcwidget.ui.social

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.btcwidget.ui.main.EInk
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.Result
import com.google.zxing.common.HybridBinarizer
import java.util.EnumMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** In-app QR scanner: CameraX supplies frames; ZXing core decodes them locally. */
object QrScanner {
    @Composable
    fun rememberScanner(onResult: (String?) -> Unit): () -> Unit {
        var open by remember { mutableStateOf(false) }
        val callback = androidx.compose.runtime.rememberUpdatedState(onResult)
        if (open) CameraScannerDialog(onDismiss = { open = false }, onResult = { value ->
            open = false
            callback.value(value)
        })
        return { open = true }
    }
}

@Composable
private fun CameraScannerDialog(onDismiss: () -> Unit, onResult: (String?) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var cameraError by remember { mutableStateOf(false) }
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val delivered = remember { AtomicBoolean(false) }
    val callback = androidx.compose.runtime.rememberUpdatedState(onResult)

    DisposableEffect(executor) { onDispose { executor.shutdown() } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Color(0xFF080A0D)).statusBarsPadding().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Scan QR code"), color = Color.White, fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text(tr("Keep the code inside the frame"), color = Color(0xFF9AA3AD), fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp))
                }
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable(onClickLabel = tr("Close"), onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) { Icon(Glyphs.CloseLine, contentDescription = tr("Close"), tint = Color.White, modifier = Modifier.size(22.dp)) }
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                if (hasPermission) {
                    CameraPreview(
                        executor = executor,
                        lifecycleOwner = lifecycleOwner,
                        onCameraError = { cameraError = true },
                    onDecoded = { text -> if (delivered.compareAndSet(false, true)) callback.value(text) }
                    )
                    ScannerFrame(Modifier.align(Alignment.Center))
                    if (cameraError) ScannerMessage(tr("Camera unavailable"), Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp))
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Surface(shape = CircleShape, color = Color(0xFF1A2028), modifier = Modifier.size(68.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Glyphs.Qr, contentDescription = null, tint = EInk.Accent, modifier = Modifier.size(30.dp)) }
                        }
                        Text(tr("Allow camera access to scan a QR code"), color = Color.White, fontSize = 15.sp)
                        SettingsButton(tr("Allow camera"), primary = true) { permissionRequest.launch(Manifest.permission.CAMERA) }
                    }
                }
            }
            Text(
                tr("QR codes are scanned on this device."), color = Color(0xFF9AA3AD), fontSize = 13.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(horizontal = 24.dp, vertical = 22.dp)
            )
        }
    }
}

@Composable
private fun CameraPreview(
    executor: ExecutorService,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    onCameraError: () -> Unit,
    onDecoded: (String) -> Unit
) {
    val context = LocalContext.current
    val latestResult = androidx.compose.runtime.rememberUpdatedState(onDecoded)
    val latestError = androidx.compose.runtime.rememberUpdatedState(onCameraError)
    AndroidView(
        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)),
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                runCatching {
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build().also { useCase ->
                            useCase.setAnalyzer(executor, QrFrameAnalyzer { decoded -> latestResult.value(decoded) })
                        }
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                    previewView.tag = arrayOf(preview, analysis)
                }.onFailure { latestError.value() }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        update = { view ->
            view.post {
                val cases = view.tag as? Array<*>
                if (cases == null && !view.isAttachedToWindow) return@post
            }
        },
        onRelease = { view ->
            (view.tag as? Array<*>)?.filterIsInstance<androidx.camera.core.UseCase>()?.let { cases ->
                runCatching { ProcessCameraProvider.getInstance(context).get().unbind(*cases.toTypedArray()) }
            }
        }
    )
}

@Composable
private fun ScannerFrame(modifier: Modifier = Modifier) {
    Box(modifier.size(260.dp).border(2.dp, EInk.Accent, RoundedCornerShape(26.dp)))
}

@Composable
private fun ScannerMessage(message: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = Color(0xDD111820)) {
        Text(message, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
    }
}

private class QrFrameAnalyzer(private val onDecoded: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val reader = MultiFormatReader().apply {
        setHints(EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
            put(DecodeHintType.POSSIBLE_FORMATS, listOf(BarcodeFormat.QR_CODE))
            put(DecodeHintType.TRY_HARDER, true)
        })
    }

    override fun analyze(image: ImageProxy) {
        try {
            val plane = image.planes.firstOrNull() ?: return
            val yBuffer = plane.buffer.duplicate()
            val width = image.width
            val height = image.height
            val bytes = ByteArray(width * height)
            val rowStride = plane.rowStride
            for (y in 0 until height) {
                yBuffer.position(y * rowStride)
                yBuffer.get(bytes, y * width, width)
            }
            val (upright, uprightWidth, uprightHeight) = rotateLuma(bytes, width, height, image.imageInfo.rotationDegrees)
            val source = PlanarYUVLuminanceSource(upright, uprightWidth, uprightHeight, 0, 0, uprightWidth, uprightHeight, false)
            val bitmap = BinaryBitmap(HybridBinarizer(source))
            val result: Result? = runCatching { reader.decodeWithState(bitmap) }
                .recoverCatching { reader.decodeWithState(BinaryBitmap(HybridBinarizer(source.invert()))) }
                .getOrNull()
            result?.text?.let(onDecoded)
        } catch (_: Exception) {
            // Most camera frames do not contain a readable QR code; keep scanning.
        } finally {
            reader.reset()
            image.close()
        }
    }

    private fun rotateLuma(data: ByteArray, width: Int, height: Int, degrees: Int): Triple<ByteArray, Int, Int> {
        val rotation = ((degrees % 360) + 360) % 360
        if (rotation == 0) return Triple(data, width, height)
        val outWidth = if (rotation == 180) width else height
        val outHeight = if (rotation == 180) height else width
        val out = ByteArray(data.size)
        for (y in 0 until height) for (x in 0 until width) {
            val (nx, ny) = when (rotation) {
                90 -> height - 1 - y to x
                180 -> width - 1 - x to height - 1 - y
                270 -> y to width - 1 - x
                else -> x to y
            }
            out[ny * outWidth + nx] = data[y * width + x]
        }
        return Triple(out, outWidth, outHeight)
    }
}

