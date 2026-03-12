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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.lbwma.cnn.network.ApiClient
import com.lbwma.cnn.network.ThumbnailCache
import com.lbwma.cnn.screen.CameraScreen
import com.lbwma.cnn.screen.ConvertersScreen
import com.lbwma.cnn.screen.FullPhotoScreen
import com.lbwma.cnn.screen.LoginScreen
import com.lbwma.cnn.screen.PhotosScreen
import com.lbwma.cnn.ui.theme.CnnTheme
import kotlinx.coroutines.delay
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.io.File

private const val SESSION_TIMEOUT_MS = 10 * 60 * 1000L // 10 minutos

sealed class Screen {
    data object Login : Screen()
    data object Converters : Screen()
    data class Photos(val name: String) : Screen()
    data class Camera(val conversorName: String) : Screen()
    data class FullPhoto(val conversorName: String, val fotoName: String) : Screen()

    val depth: Int get() = when (this) {
        is Login -> 0
        is Converters -> 1
        is Photos -> 2
        is Camera -> 3
        is FullPhoto -> 3
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ThumbnailCache.init(this)
        setContent {
            CnnTheme {
                var screen by remember { mutableStateOf<Screen>(Screen.Login) }
                var previousDepth by remember { mutableStateOf(0) }
                var filesToUpload by remember { mutableStateOf<List<File>>(emptyList()) }
                var sessionExpired by remember { mutableStateOf(false) }
                var lastActivityMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

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
                        is Screen.Photos -> Screen.Converters
                        is Screen.Converters -> Screen.Login
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
                                onLoginSuccess = { sessionExpired = false; screen = Screen.Converters },
                                sessionExpired = sessionExpired
                            )
                            Screen.Converters -> ConvertersScreen(
                                onConversorClick = { screen = Screen.Photos(it) }
                            )
                            is Screen.Photos -> PhotosScreen(
                                conversorName = current.name,
                                imageLoader = imageLoader,
                                filesToUpload = filesToUpload,
                                onFilesConsumed = { filesToUpload = emptyList() },
                                onOpenCamera = { screen = Screen.Camera(current.name) },
                                onViewPhoto = { foto ->
                                    screen = Screen.FullPhoto(current.name, foto)
                                },
                                onBack = { screen = Screen.Converters }
                            )
                            is Screen.Camera -> CameraScreen(
                                onPhotosTaken = { files ->
                                    filesToUpload = files
                                    screen = Screen.Photos(current.conversorName)
                                },
                                onCancel = { screen = Screen.Photos(current.conversorName) }
                            )
                            is Screen.FullPhoto -> FullPhotoScreen(
                                conversorName = current.conversorName,
                                fotoName = current.fotoName,
                                imageLoader = imageLoader,
                                onBack = { screen = Screen.Photos(current.conversorName) }
                            )
                        }
                    }
                }
            }
        }
    }
}
