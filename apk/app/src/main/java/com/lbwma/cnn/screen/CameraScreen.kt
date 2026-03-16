package com.lbwma.cnn.screen

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import java.io.FileOutputStream
import java.util.concurrent.Executors

@Composable
fun CameraScreen(
    onPhotosTaken: (List<File>) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var photoCount by remember { mutableIntStateOf(0) }
    val capturedFiles = remember { mutableListOf<File>() }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var showFlash by remember { mutableStateOf(false) }
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

    LaunchedEffect(showFlash) {
        if (showFlash) {
            delay(80)
            showFlash = false
        }
    }

    DisposableEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val resolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(1920, 1080),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                    )
                )
                .build()

            val preview = Preview.Builder()
                .setResolutionSelector(resolutionSelector)
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview
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
        val original = previewView.bitmap ?: return
        val bitmap = if (original.config == Bitmap.Config.HARDWARE) {
            original.copy(Bitmap.Config.ARGB_8888, false).also { original.recycle() }
        } else {
            original
        }

        showFlash = true
        try {
            vibrator?.vibrate(
                VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } catch (_: Exception) {}

        executor.execute {
            try {
                val file = File(context.cacheDir, "batch_${System.currentTimeMillis()}.jpg")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                bitmap.recycle()
                capturedFiles.add(file)
                mainHandler.post { photoCount = capturedFiles.size }
            } catch (e: Exception) {
                Log.e("CameraScreen", "Erro ao salvar foto", e)
                bitmap.recycle()
            }
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
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val buttonScale by animateFloatAsState(
                targetValue = if (isPressed) 0.88f else 1f,
                animationSpec = tween(100),
                label = "captureScale"
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(82.dp)
                    .scale(buttonScale)
            ) {
                // Outer ring with gradient
                Box(
                    Modifier
                        .size(82.dp)
                        .border(
                            3.dp,
                            Brush.sweepGradient(
                                colors = listOf(
                                    Color.White,
                                    Color.White.copy(alpha = 0.6f),
                                    Color.White,
                                    Color.White.copy(alpha = 0.6f),
                                    Color.White
                                )
                            ),
                            CircleShape
                        )
                )
                // Inner fill
                Box(
                    Modifier
                        .size(66.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) { capturePhoto() }
                )
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
