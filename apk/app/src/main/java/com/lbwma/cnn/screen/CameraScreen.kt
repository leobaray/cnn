package com.lbwma.cnn.screen

import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lbwma.cnn.ui.theme.Cyan40
import com.lbwma.cnn.ui.theme.Dark10
import com.lbwma.cnn.ui.theme.Dark15
import com.lbwma.cnn.ui.theme.GlassBorder
import com.lbwma.cnn.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import java.io.File
import java.util.concurrent.Executors

@Composable
fun CameraScreen(
    onPhotosTaken: (List<File>) -> Unit,
    onCancel: () -> Unit
) {
    // ---- Configuração do modo rajada (arrastar para cima para ativar) ----
    val burstIntervalMs = 550L   // intervalo entre fotos no modo rajada (ms)
    // -----------------------------------------------------------------------

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var photoCount by remember { mutableIntStateOf(0) }
    val capturedFiles = remember { mutableListOf<File>() }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    var showFlash by remember { mutableStateOf(false) }
    var flashTrigger by remember { mutableIntStateOf(0) }
    var isLocked by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    val vibrator = remember { context.getSystemService<Vibrator>() }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    fun discardAndCancel() {
        capturedFiles.forEach { it.delete() }
        capturedFiles.clear()
        onCancel()
    }

    fun tryCancel() {
        if (photoCount > 0) showDiscardDialog = true else discardAndCancel()
    }

    BackHandler { tryCancel() }

    LaunchedEffect(flashTrigger) {
        if (flashTrigger > 0) {
            showFlash = true
            delay(80)
            showFlash = false
        }
    }

    DisposableEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Log.e("CameraScreen", "Erro ao abrir câmera", e)
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            try { cameraProviderFuture.get().unbindAll() } catch (_: Exception) {}
            executor.shutdown()
        }
    }

    fun capturePhoto() {
        try {
            vibrator?.vibrate(
                VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } catch (_: Exception) {}

        val file = File(context.cacheDir, "batch_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

        imageCapture.takePicture(
            outputOptions,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    capturedFiles.add(file)
                    mainHandler.post {
                        photoCount = capturedFiles.size
                        flashTrigger++ // sincronizado com a confirmação real da foto
                    }
                }
                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraScreen", "Erro ao capturar foto", exception)
                    file.delete()
                }
            }
        )
    }

    // Modo rajada: ativo enquanto isLocked, para ao clicar novamente
    LaunchedEffect(isLocked) {
        while (isLocked) {
            capturePhoto()
            delay(burstIntervalMs)
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Camera preview
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // Flash overlay
        AnimatedVisibility(
            visible = showFlash,
            enter = fadeIn(tween(40)),
            exit = fadeOut(tween(60)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.45f)))
        }

        // Top gradient overlay
        Box(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.7f),
                            Color.Black.copy(alpha = 0.2f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { tryCancel() },
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Dark15.copy(alpha = 0.7f))
                    .border(1.dp, GlassBorder, CircleShape)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    "Voltar",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.weight(1f))

            // Photo counter badge
            AnimatedVisibility(
                visible = photoCount > 0,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Cyan40)
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    Text(
                        "$photoCount foto${if (photoCount != 1) "s" else ""}",
                        color = Color(0xFF00131E),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Bottom gradient overlay
        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.4f),
                            Color.Black.copy(alpha = 0.8f)
                        )
                    )
                )
        )

        // Bottom controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Capture button
            val ringColor = if (isLocked) Cyan40 else Color.White
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(82.dp)
            ) {
                // Outer ring
                Box(
                    Modifier
                        .size(82.dp)
                        .border(
                            3.dp,
                            Brush.sweepGradient(
                                colors = listOf(
                                    ringColor,
                                    ringColor.copy(alpha = 0.6f),
                                    ringColor,
                                    ringColor.copy(alpha = 0.6f),
                                    ringColor
                                )
                            ),
                            CircleShape
                        )
                )
                // Inner fill — arrastar para cima ativa rajada, toque para/dispara
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(66.dp)
                        .clip(CircleShape)
                        .background(if (isLocked) Cyan40 else Color.White)
                        .pointerInput(isLocked) {
                            val dragThresholdPx = 50.dp.toPx()
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                down.consume()
                                val startY = down.position.y
                                var didDragUp = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    if (!change.pressed) {
                                        if (!didDragUp) {
                                            if (isLocked) isLocked = false
                                            else capturePhoto()
                                        }
                                        break
                                    }
                                    if (change.position.y - startY < -dragThresholdPx) {
                                        change.consume()
                                        isLocked = true
                                        didDragUp = true
                                        break
                                    }
                                }
                            }
                        }
                ) {
                    if (isLocked) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Parar rajada",
                            tint = Color(0xFF00131E),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            // Send button
            AnimatedVisibility(
                visible = photoCount > 0,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Cyan40, Cyan40.copy(alpha = 0.85f))
                            )
                        )
                        .clickable { onPhotosTaken(capturedFiles.toList()) }
                        .padding(horizontal = 32.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color(0xFF00131E),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Enviar $photoCount foto${if (photoCount != 1) "s" else ""}",
                        color = Color(0xFF00131E),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // Discard confirmation dialog
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            containerColor = Dark10,
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    "Descartar fotos?",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Você tirou $photoCount foto${if (photoCount != 1) "s" else ""} que ainda não ${if (photoCount != 1) "foram enviadas" else "foi enviada"}. Deseja descartar?",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    discardAndCancel()
                }) {
                    Text("Descartar", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Continuar", color = TextSecondary)
                }
            }
        )
    }
}
