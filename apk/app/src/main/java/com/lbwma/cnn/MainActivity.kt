package com.lbwma.cnn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.lbwma.cnn.model.FILTROS
import com.lbwma.cnn.model.RefinementStore
import com.lbwma.cnn.network.ApiClient
import com.lbwma.cnn.network.AppUpdater
import com.lbwma.cnn.network.UpdateState
import com.lbwma.cnn.network.ThumbnailCache
import com.lbwma.cnn.screen.CameraScreen
import com.lbwma.cnn.screen.ConvertersScreen
import com.lbwma.cnn.screen.FilterGridScreen
import com.lbwma.cnn.screen.FullPhotoScreen
import com.lbwma.cnn.screen.IdentifyScreen
import com.lbwma.cnn.screen.LoginScreen
import com.lbwma.cnn.screen.PhotosScreen
import com.lbwma.cnn.screen.ReviewScreen
import com.lbwma.cnn.ui.theme.CnnTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.io.File

private const val SESSION_TIMEOUT_MS = 10 * 60 * 1000L // 10 minutos

sealed class Screen {
    data object Login : Screen()
    data object Identify : Screen()
    data object Converters : Screen()
    data class Photos(val name: String, val filterPrefix: String? = null) : Screen()
    data class FilterGrid(val conversorName: String) : Screen()
    data class Camera(val conversorName: String, val iaMode: Boolean, val filtroId: Int? = null) : Screen()
    data class Review(val conversorName: String, val filtroId: Int, val serverCount: Int) : Screen()
    data class FullPhoto(val conversorName: String, val fotoName: String) : Screen()

    val depth: Int get() = when (this) {
        is Login -> 0
        is Identify -> 1
        is Converters -> 1
        is Photos -> 2
        is FilterGrid -> 2
        is Camera -> 3
        is Review -> 3
        is FullPhoto -> 3
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ThumbnailCache.init(this)
        RefinementStore.init(this)
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        setContent {
            CnnTheme {
                var screen by remember { mutableStateOf<Screen>(Screen.Login) }
                var previousDepth by remember { mutableStateOf(0) }
                var filesToUpload by remember { mutableStateOf<List<File>>(emptyList()) }
                var iaMode by remember { mutableStateOf(prefs.getBoolean("ia_mode", false)) }
                // Pós-captura IA: (conversorName, filtroId, files)
                var postCaptureState by remember { mutableStateOf<Triple<String, Int, List<File>>?>(null) }
                var sessionExpired by remember { mutableStateOf(false) }
                var lastActivityMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
                val updateState by AppUpdater.state.collectAsState()
                val updateScope = rememberCoroutineScope()
                val currentVersionCode = remember {
                    packageManager.getPackageInfo(packageName, 0).longVersionCode.toInt()
                }

                val imageLoader = remember {
                    ImageLoader.Builder(this@MainActivity)
                        .okHttpClient {
                            OkHttpClient.Builder()
                                .addInterceptor(ApiClient.getAuthInterceptor())
                                .dispatcher(Dispatcher().apply { maxRequestsPerHost = 4 })
                                .build()
                        }
                        .memoryCache {
                            MemoryCache.Builder(this@MainActivity)
                                .maxSizePercent(0.20)
                                .build()
                        }
                        .diskCache {
                            DiskCache.Builder()
                                .directory(cacheDir.resolve("image_cache"))
                                .maxSizePercent(0.05)
                                .build()
                        }
                        .build()
                }

                // Checa atualização ao abrir o app
                LaunchedEffect(Unit) { AppUpdater.check(currentVersionCode) }

                // Session timeout — reinicia o contador a cada mudança de tela
                LaunchedEffect(screen) {
                    if (screen is Screen.Login) return@LaunchedEffect
                    lastActivityMs = System.currentTimeMillis()
                    while (true) {
                        delay(60_000L)
                        if (System.currentTimeMillis() - lastActivityMs > SESSION_TIMEOUT_MS) {
                            ApiClient.logout()
                            sessionExpired = true
                            screen = Screen.Login
                            break
                        }
                    }
                }

                BackHandler(enabled = screen !is Screen.Login && screen !is Screen.Camera) {
                    screen = when (val s = screen) {
                        is Screen.FullPhoto -> Screen.Photos(s.conversorName)
                        is Screen.Photos -> if (s.filterPrefix != null) Screen.FilterGrid(s.name) else Screen.Converters
                        is Screen.FilterGrid -> Screen.Converters
                        is Screen.Review -> Screen.FilterGrid(s.conversorName)
                        is Screen.Converters -> Screen.Identify
                        is Screen.Identify -> Screen.Login
                        else -> screen
                    }
                }

                // Detecta qualquer toque para resetar o timer de inatividade
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent(PointerEventPass.Initial)
                                    lastActivityMs = System.currentTimeMillis()
                                }
                            }
                        }
                ) {
                    AnimatedContent(
                        targetState = screen,
                        label = "nav",
                        transitionSpec = {
                            val goingForward = targetState.depth >= previousDepth
                            val duration = 300
                            if (goingForward) {
                                (slideInHorizontally(tween(duration)) { it / 3 } + fadeIn(tween(duration)))
                                    .togetherWith(
                                        slideOutHorizontally(tween(duration)) { -it / 3 } + fadeOut(tween(duration / 2))
                                    )
                            } else {
                                (slideInHorizontally(tween(duration)) { -it / 3 } + fadeIn(tween(duration)))
                                    .togetherWith(
                                        slideOutHorizontally(tween(duration)) { it / 3 } + fadeOut(tween(duration / 2))
                                    )
                            }
                        }
                    ) { current ->
                        previousDepth = current.depth
                        when (current) {
                            Screen.Login -> LoginScreen(
                                onLoginSuccess = { sessionExpired = false; screen = Screen.Identify },
                                sessionExpired = sessionExpired
                            )
                            Screen.Identify -> IdentifyScreen(
                                onGoToTraining = { screen = Screen.Converters }
                            )
                            Screen.Converters -> ConvertersScreen(
                                onConversorClick = { name ->
                                    screen = if (iaMode) Screen.FilterGrid(name) else Screen.Photos(name)
                                },
                                iaMode = iaMode,
                                onIaModeChange = { iaMode = it; prefs.edit().putBoolean("ia_mode", it).apply() }
                            )
                            is Screen.Photos -> PhotosScreen(
                                conversorName = current.name,
                                imageLoader = imageLoader,
                                filesToUpload = filesToUpload,
                                filterPrefix = current.filterPrefix,
                                onFilesConsumed = { filesToUpload = emptyList() },
                                onOpenCamera = { screen = Screen.Camera(current.name, iaMode) },
                                onViewPhoto = { foto ->
                                    screen = Screen.FullPhoto(current.name, foto)
                                },
                                onBack = {
                                    screen = if (current.filterPrefix != null)
                                        Screen.FilterGrid(current.name)
                                    else Screen.Converters
                                }
                            )
                            is Screen.FilterGrid -> FilterGridScreen(
                                conversorName = current.conversorName,
                                onFilterClick = { filtroId ->
                                    screen = Screen.Camera(current.conversorName, iaMode = true, filtroId = filtroId)
                                },
                                onFilterLongClick = { filtroId ->
                                    val prefix = FILTROS.first { it.id == filtroId }.prefix
                                    screen = Screen.Photos(current.conversorName, filterPrefix = "${prefix}_")
                                },
                                onReviewClick = { filtroId ->
                                    // Need server count — passed via Screen.Review
                                    screen = Screen.Review(current.conversorName, filtroId, 0)
                                },
                                onBack = { screen = Screen.Converters }
                            )
                            is Screen.Camera -> CameraScreen(
                                iaMode = current.iaMode,
                                filtroId = current.filtroId,
                                onPhotosTaken = { files ->
                                    if (current.filtroId != null) {
                                        // IA mode: store for review dialog
                                        postCaptureState = Triple(current.conversorName, current.filtroId, files)
                                        screen = Screen.FilterGrid(current.conversorName)
                                    } else {
                                        filesToUpload = files
                                        screen = Screen.Photos(current.conversorName)
                                    }
                                },
                                onCancel = {
                                    screen = if (current.filtroId != null)
                                        Screen.FilterGrid(current.conversorName)
                                    else
                                        Screen.Photos(current.conversorName)
                                }
                            )
                            is Screen.Review -> ReviewScreen(
                                conversorName = current.conversorName,
                                filtroId = current.filtroId,
                                serverCount = current.serverCount,
                                onDone = { screen = Screen.FilterGrid(current.conversorName) },
                                onCancel = { screen = Screen.FilterGrid(current.conversorName) }
                            )
                            is Screen.FullPhoto -> FullPhotoScreen(
                                conversorName = current.conversorName,
                                fotoName = current.fotoName,
                                imageLoader = imageLoader,
                                onBack = { screen = Screen.Photos(current.conversorName) }
                            )
                        }
                    }

                    // Bloqueio de atualização obrigatória
                    when (val us = updateState) {
                        is UpdateState.Available -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(com.lbwma.cnn.ui.theme.Dark00.copy(alpha = 0.95f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(40.dp)
                                ) {
                                    Text(
                                        "Atualização disponível",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        "Uma nova versão do app está disponível. Atualize para continuar.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = com.lbwma.cnn.ui.theme.TextSecondary,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(Modifier.height(28.dp))
                                    TextButton(onClick = {
                                        updateScope.launch { AppUpdater.downloadAndInstall(this@MainActivity) }
                                    }) {
                                        Text(
                                            "Atualizar agora",
                                            color = com.lbwma.cnn.ui.theme.Cyan40,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                }
                            }
                        }
                        is UpdateState.Downloading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(com.lbwma.cnn.ui.theme.Dark00.copy(alpha = 0.95f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(40.dp)
                                ) {
                                    Text(
                                        "Baixando atualização…",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(20.dp))
                                    LinearProgressIndicator(
                                        progress = { us.progress },
                                        modifier = Modifier
                                            .fillMaxWidth(0.7f)
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = com.lbwma.cnn.ui.theme.Cyan40,
                                        trackColor = com.lbwma.cnn.ui.theme.Dark15,
                                        strokeCap = StrokeCap.Round
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "${(us.progress * 100).toInt()}%",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = com.lbwma.cnn.ui.theme.TextSecondary
                                    )
                                }
                            }
                        }
                        is UpdateState.Installing -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(com.lbwma.cnn.ui.theme.Dark00.copy(alpha = 0.95f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(
                                        color = com.lbwma.cnn.ui.theme.Cyan40,
                                        strokeWidth = 3.dp
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    Text("Instalando…", style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                        is UpdateState.Error -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(com.lbwma.cnn.ui.theme.Dark00.copy(alpha = 0.95f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(40.dp)
                                ) {
                                    Text(
                                        "Erro na atualização",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        us.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = com.lbwma.cnn.ui.theme.TextSecondary,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(Modifier.height(20.dp))
                                    TextButton(onClick = {
                                        updateScope.launch { AppUpdater.downloadAndInstall(this@MainActivity) }
                                    }) {
                                        Text(
                                            "Tentar novamente",
                                            color = com.lbwma.cnn.ui.theme.Cyan40,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                        else -> {} // Idle, Checking — não bloqueia
                    }

                    // Diálogo overlay pós-captura IA
                    postCaptureState?.let { (conversor, filtroId, files) ->
                        AlertDialog(
                            onDismissRequest = {},
                            containerColor = com.lbwma.cnn.ui.theme.Dark10,
                            shape = RoundedCornerShape(24.dp),
                            title = {
                                Text(
                                    "Revisar ${files.size} fotos?",
                                    style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            text = {
                                Text(
                                    "Deseja revisar agora para escolher quais manter?",
                                    color = com.lbwma.cnn.ui.theme.TextSecondary
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    RefinementStore.save(conversor, filtroId, files)
                                    postCaptureState = null
                                    screen = Screen.Review(conversor, filtroId, 0)
                                }) {
                                    Text(
                                        "Sim, revisar",
                                        color = com.lbwma.cnn.ui.theme.Cyan40,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    RefinementStore.save(conversor, filtroId, files)
                                    postCaptureState = null
                                }) {
                                    Text("Depois", color = com.lbwma.cnn.ui.theme.TextSecondary)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
