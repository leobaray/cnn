package com.lbwma.cnn.screen

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ModelTraining
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lbwma.cnn.model.IdentificationHistory
import com.lbwma.cnn.network.ApiClient
import com.lbwma.cnn.ui.theme.Amber
import com.lbwma.cnn.ui.theme.Cyan40
import com.lbwma.cnn.ui.theme.Cyan60
import com.lbwma.cnn.ui.theme.Dark00
import com.lbwma.cnn.ui.theme.Dark05
import com.lbwma.cnn.ui.theme.Dark10
import com.lbwma.cnn.ui.theme.Dark15
import com.lbwma.cnn.ui.theme.Dark20
import com.lbwma.cnn.ui.theme.GlassBorder
import com.lbwma.cnn.ui.theme.Success
import com.lbwma.cnn.ui.theme.TextPrimary
import com.lbwma.cnn.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

private val barColors = listOf(
    Cyan40,
    Color(0xFFFF7B72),
    Color(0xFF3FB950),
    Color(0xFFD2A8FF),
    Color(0xFFFFD700)
)

@Composable
fun IdentifyScreen(
    onGoToTraining: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val vibrator = remember { context.getSystemService<Vibrator>() }

    var cameraOpen by remember { mutableStateOf(false) }
    var cameraReady by remember { mutableStateOf(false) }
    var analyzing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ApiClient.InferResult?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var modelReady by remember { mutableStateOf<Boolean?>(null) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var permanentlyDenied by remember { mutableStateOf(false) }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraOpen = true
        } else {
            permanentlyDenied = !ActivityCompat.shouldShowRequestPermissionRationale(
                activity, Manifest.permission.CAMERA
            )
            showPermissionDialog = true
        }
    }

    fun requestCameraAndOpen() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            cameraOpen = true
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Check model status on entry
    LaunchedEffect(Unit) {
        ApiClient.inferStatus().onSuccess { modelReady = it.ready }.onFailure { modelReady = false }
    }

    // Camera setup — only when cameraOpen
    DisposableEffect(cameraOpen) {
        if (!cameraOpen) {
            cameraReady = false
            onDispose {}
        } else {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview
                    )
                    cameraReady = true
                } catch (e: Exception) {
                    Log.e("IdentifyScreen", "Erro ao abrir camera", e)
                }
            }, ContextCompat.getMainExecutor(context))

            onDispose {
                try { cameraProviderFuture.get().unbindAll() } catch (_: Exception) {}
                cameraReady = false
            }
        }
    }

    fun captureAndInfer() {
        if (analyzing) return
        val bitmap = previewView.bitmap ?: return

        try {
            vibrator?.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {}

        analyzing = true
        result = null
        errorMsg = null

        scope.launch {
            try {
                val decoded = if (bitmap.config == Bitmap.Config.HARDWARE) {
                    bitmap.copy(Bitmap.Config.ARGB_8888, false).also { bitmap.recycle() }
                } else bitmap

                val size = minOf(decoded.width, decoded.height)
                val x = (decoded.width - size) / 2
                val y = (decoded.height - size) / 2
                val square = Bitmap.createBitmap(decoded, x, y, size, size)
                decoded.recycle()
                val scaled = Bitmap.createScaledBitmap(square, 512, 512, true)
                square.recycle()

                val baos = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 90, baos)
                scaled.recycle()
                val bytes = baos.toByteArray()

                ApiClient.infer(bytes, "identify_${System.currentTimeMillis()}.jpg", tta = true)
                    .onSuccess {
                        result = it
                        IdentificationHistory.add(it.classe, it.confianca)
                        try {
                            vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                        } catch (_: Exception) {}
                    }
                    .onFailure { errorMsg = it.message ?: "Erro desconhecido" }
            } catch (e: Exception) {
                errorMsg = e.message ?: "Erro ao processar imagem"
            } finally {
                analyzing = false
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Dark00)) {
        if (cameraOpen) {
            // ===== CAMERA MODE =====
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

            // Top gradient
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.8f),
                                Color.Black.copy(alpha = 0.3f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Top bar (camera mode)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Identificar",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        "Aponte para um conversor",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
                Spacer(Modifier.weight(1f))
                // Close camera button
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Dark15.copy(alpha = 0.8f))
                        .border(1.dp, GlassBorder, CircleShape)
                        .clickable {
                            cameraOpen = false
                            result = null
                            errorMsg = null
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Fechar camera",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Crosshair
            if (!analyzing && result == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .drawBehind {
                                val s = size.width
                                val corner = s * 0.2f
                                val strokeW = 3.dp.toPx()
                                val color = Cyan40.copy(alpha = 0.6f)
                                drawLine(color, Offset(0f, 0f), Offset(corner, 0f), strokeW)
                                drawLine(color, Offset(0f, 0f), Offset(0f, corner), strokeW)
                                drawLine(color, Offset(s, 0f), Offset(s - corner, 0f), strokeW)
                                drawLine(color, Offset(s, 0f), Offset(s, corner), strokeW)
                                drawLine(color, Offset(0f, s), Offset(corner, s), strokeW)
                                drawLine(color, Offset(0f, s), Offset(0f, s - corner), strokeW)
                                drawLine(color, Offset(s, s), Offset(s - corner, s), strokeW)
                                drawLine(color, Offset(s, s), Offset(s, s - corner), strokeW)
                            }
                    )
                }
            }

            // Bottom gradient
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.5f),
                                Color.Black.copy(alpha = 0.9f),
                                Color.Black.copy(alpha = 0.95f)
                            )
                        )
                    )
            )

            // Bottom content (camera mode)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Results
                AnimatedVisibility(
                    visible = result != null,
                    enter = slideInVertically(tween(400)) { it / 2 } + fadeIn(tween(400)),
                    exit = slideOutVertically(tween(200)) { it / 2 } + fadeOut(tween(200))
                ) {
                    result?.let { res ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .padding(bottom = 16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Dark15.copy(alpha = 0.97f),
                                                Dark10.copy(alpha = 0.97f),
                                                Dark05.copy(alpha = 0.97f)
                                            )
                                        )
                                    )
                                    .border(
                                        1.dp,
                                        Brush.linearGradient(
                                            listOf(
                                                Cyan40.copy(alpha = 0.4f),
                                                GlassBorder,
                                                com.lbwma.cnn.ui.theme.Violet40.copy(alpha = 0.3f)
                                            )
                                        ),
                                        RoundedCornerShape(24.dp)
                                    )
                                    .padding(20.dp)
                            ) {
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(com.lbwma.cnn.ui.heroGradient())
                                        ) {
                                            Icon(
                                                Icons.Default.Psychology,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                "IDENTIFICADO",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Cyan40,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.5.sp
                                            )
                                        }
                                        // Confianca badge
                                        val confColor = if (res.confianca >= 80f) Success else Cyan60
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(confColor.copy(alpha = 0.15f))
                                                .border(1.dp, confColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                "${res.confianca.toInt()}%",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = confColor,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(14.dp))
                                    Text(
                                        res.classe,
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            brush = com.lbwma.cnn.ui.heroGradient()
                                        ),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(18.dp))
                                    res.top5.forEachIndexed { idx, (name, conf) ->
                                        ConfidenceBar(
                                            name = name,
                                            confidence = conf,
                                            color = barColors[idx % barColors.size],
                                            isTop = idx == 0,
                                            delayMs = idx * 80
                                        )
                                        if (idx < res.top5.size - 1) Spacer(Modifier.height(8.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // Error
                AnimatedVisibility(visible = errorMsg != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Dark10.copy(alpha = 0.95f))
                            .border(1.dp, Color(0x33EF5350), RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Text(
                            errorMsg ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFEF5350),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Capture / analyzing
                if (analyzing) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(88.dp)) {
                        CircularProgressIndicator(
                            color = Cyan40,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(88.dp)
                        )
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(com.lbwma.cnn.ui.heroGradient())
                        ) {
                            Icon(Icons.Default.Psychology, null, tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                    }
                } else {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(88.dp)) {
                        // Halo glow
                        Box(
                            Modifier
                                .size(110.dp)
                                .background(
                                    Brush.radialGradient(listOf(Cyan40.copy(alpha = 0.35f), Color.Transparent)),
                                    CircleShape
                                )
                        )
                        // Sweep ring
                        Box(
                            Modifier
                                .size(88.dp)
                                .border(
                                    3.dp,
                                    Brush.sweepGradient(
                                        listOf(
                                            Cyan40,
                                            com.lbwma.cnn.ui.theme.Violet40,
                                            com.lbwma.cnn.ui.theme.Magenta40,
                                            Cyan40
                                        )
                                    ),
                                    CircleShape
                                )
                        )
                        // Inner button
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(70.dp)
                                .clip(CircleShape)
                                .background(com.lbwma.cnn.ui.primaryGradient())
                                .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                                .clickable { captureAndInfer() }
                        ) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = "Identificar",
                                tint = com.lbwma.cnn.ui.theme.OnPrimaryDark,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                }

                // Clear result
                AnimatedVisibility(visible = result != null && !analyzing) {
                    Row(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Dark15.copy(alpha = 0.8f))
                            .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
                            .clickable { result = null; errorMsg = null }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Close, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Limpar", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        } else {
            // ===== LANDING PAGE — mesh ambient =====
            com.lbwma.cnn.ui.MeshAmbientBackground(modifier = Modifier.fillMaxSize())

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(24.dp))

                // Top bar — settings + training
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Dark15)
                            .border(1.dp, GlassBorder, CircleShape)
                            .clickable { onOpenSettings() }
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Configurações",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(Dark15)
                            .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
                            .clickable { onGoToTraining() }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.ModelTraining,
                            contentDescription = null,
                            tint = Cyan40,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Area de Treino",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = Cyan40
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                // Main icon with pulsing ring
                val pulseTransition = rememberInfiniteTransition(label = "pulse")
                val pulseAlpha by pulseTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 0.08f,
                    animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse),
                    label = "pulseAlpha"
                )
                val pulseScale by pulseTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.3f,
                    animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse),
                    label = "pulseScale"
                )

                Box(contentAlignment = Alignment.Center) {
                    // Outer pulse ring (mais largo, gradient)
                    Box(
                        Modifier
                            .size((150 * pulseScale).dp)
                            .border(
                                1.5.dp,
                                Brush.sweepGradient(
                                    listOf(
                                        Cyan40.copy(alpha = pulseAlpha),
                                        com.lbwma.cnn.ui.theme.Violet40.copy(alpha = pulseAlpha),
                                        com.lbwma.cnn.ui.theme.Magenta40.copy(alpha = pulseAlpha * 0.5f),
                                        Cyan40.copy(alpha = pulseAlpha)
                                    )
                                ),
                                CircleShape
                            )
                    )
                    // Inner pulse ring
                    Box(
                        Modifier
                            .size((125 * pulseScale).dp)
                            .border(
                                1.dp,
                                Cyan40.copy(alpha = pulseAlpha * 0.6f),
                                CircleShape
                            )
                    )
                    // Halo de fundo
                    Box(
                        Modifier
                            .size(180.dp)
                            .background(
                                Brush.radialGradient(
                                    listOf(Cyan40.copy(alpha = 0.18f), Color.Transparent)
                                ),
                                CircleShape
                            )
                    )
                    // Icon circle premium
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape)
                            .background(com.lbwma.cnn.ui.heroGradient())
                            .border(1.5.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Psychology,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(54.dp)
                        )
                    }
                }

                Spacer(Modifier.height(36.dp))

                Text(
                    "Identificar",
                    style = MaterialTheme.typography.displayMedium.copy(
                        brush = com.lbwma.cnn.ui.heroGradient()
                    ),
                    textAlign = TextAlign.Center
                )
                Text(
                    "Conversor",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Tire uma foto e a IA identifica o modelo",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(0.8f)
                )

                // Model status
                Spacer(Modifier.height(20.dp))
                when (modelReady) {
                    true -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Success)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Modelo pronto",
                            style = MaterialTheme.typography.bodySmall,
                            color = Success
                        )
                    }
                    false -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Amber)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Modelo nao treinado",
                            style = MaterialTheme.typography.bodySmall,
                            color = Amber
                        )
                    }
                    null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                            color = TextSecondary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Verificando modelo...",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Histórico de identificações recentes
                val history by IdentificationHistory.entries.collectAsState()
                if (history.isNotEmpty()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Recentes",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Limpar",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { IdentificationHistory.clear() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)
                        ) {
                            items(history.take(10), key = { it.timestamp }) { entry ->
                                Column(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Dark15.copy(alpha = 0.7f))
                                        .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        entry.classe,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    val color = if (entry.confianca >= 80f) Success else Cyan60
                                    Text(
                                        "${entry.confianca.toInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = color
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                // Open camera button — premium hero gradient
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .padding(bottom = 40.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(com.lbwma.cnn.ui.primaryGradient())
                        .border(
                            1.dp,
                            Color.White.copy(alpha = 0.15f),
                            RoundedCornerShape(32.dp)
                        )
                        .clickable { requestCameraAndOpen() }
                        .padding(horizontal = 48.dp, vertical = 20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = com.lbwma.cnn.ui.theme.OnPrimaryDark,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(
                            "Abrir Câmera",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            color = com.lbwma.cnn.ui.theme.OnPrimaryDark
                        )
                    }
                }
            }
        }
    }

    // Permission dialog
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            containerColor = Dark15,
            shape = RoundedCornerShape(28.dp),
            title = {
                Text(
                    "Permissao necessaria",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    if (permanentlyDenied)
                        "A permissao da camera foi negada permanentemente. Abra as configuracoes do app para ativar."
                    else
                        "A camera e necessaria para identificar conversores. Permita o acesso para continuar.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    if (permanentlyDenied) {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }) {
                    Text(
                        if (permanentlyDenied) "Abrir Configuracoes" else "Tentar novamente",
                        color = Cyan40,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun ConfidenceBar(
    name: String,
    confidence: Float,
    color: Color,
    isTop: Boolean,
    delayMs: Int
) {
    val animatedWidth = remember { Animatable(0f) }

    LaunchedEffect(confidence) {
        animatedWidth.snapTo(0f)
        kotlinx.coroutines.delay(delayMs.toLong())
        animatedWidth.animateTo(
            targetValue = confidence / 100f,
            animationSpec = tween(600, easing = FastOutSlowInEasing)
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isTop) FontWeight.Bold else FontWeight.Normal,
            color = if (isTop) color else TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(100.dp)
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Dark20)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedWidth.value)
                    .height(20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        Brush.horizontalGradient(listOf(color, color.copy(alpha = 0.7f)))
                    )
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "${confidence}%",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isTop) FontWeight.Bold else FontWeight.Normal,
            color = if (isTop) color else TextSecondary,
            modifier = Modifier.width(48.dp),
            textAlign = TextAlign.End
        )
    }
}
