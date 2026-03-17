package com.lbwma.cnn.network

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

sealed class UpdateState {
    data object Idle : UpdateState()
    data object Checking : UpdateState()
    data class Available(val serverVersion: Int) : UpdateState()
    data class Downloading(val progress: Float) : UpdateState()
    data object Installing : UpdateState()
    data class Error(val message: String) : UpdateState()
}

object AppUpdater {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .build()

    suspend fun check(currentVersionCode: Int) {
        _state.value = UpdateState.Checking
        ApiClient.checkUpdate()
            .onSuccess { serverVersion ->
                _state.value = if (serverVersion > currentVersionCode) {
                    UpdateState.Available(serverVersion)
                } else {
                    UpdateState.Idle
                }
            }
            .onFailure {
                // Não bloqueia se falhar ao checar — pode ser que o endpoint ainda não exista
                _state.value = UpdateState.Idle
            }
    }

    suspend fun downloadAndInstall(context: Context) {
        _state.value = UpdateState.Downloading(0f)
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(ApiClient.getApkDownloadUrl())
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        _state.value = UpdateState.Error("Erro ao baixar (${response.code})")
                        return@withContext
                    }

                    val body = response.body ?: run {
                        _state.value = UpdateState.Error("Resposta vazia")
                        return@withContext
                    }

                    val totalBytes = body.contentLength()
                    val apkFile = File(context.cacheDir, "update.apk")

                    apkFile.outputStream().use { output ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(8192)
                            var downloaded = 0L
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                downloaded += read
                                if (totalBytes > 0) {
                                    _state.value = UpdateState.Downloading(
                                        (downloaded.toFloat() / totalBytes).coerceIn(0f, 1f)
                                    )
                                }
                            }
                        }
                    }

                    _state.value = UpdateState.Installing

                    withContext(Dispatchers.Main) {
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            apkFile
                        )
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/vnd.android.package-archive")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                }
            } catch (e: Exception) {
                _state.value = UpdateState.Error("Falha: ${e.message}")
            }
        }
    }

    fun reset() {
        _state.value = UpdateState.Idle
    }
}
