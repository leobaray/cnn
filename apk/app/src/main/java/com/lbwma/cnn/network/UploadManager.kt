package com.lbwma.cnn.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.IOException

data class UploadState(
    val pending: Int = 0,
    val lastError: String? = null
)

object UploadManager {
    // Escopo próprio — sobrevive a navegação entre telas
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Máximo 3 uploads simultâneos — evita OOM e sobrecarga de rede
    private val semaphore = Semaphore(3)

    private val _state = MutableStateFlow(UploadState())
    val state: StateFlow<UploadState> = _state.asStateFlow()

    fun enqueue(conversorName: String, files: List<File>) {
        _state.value = _state.value.copy(pending = _state.value.pending + files.size)
        files.forEach { file ->
            scope.launch {
                uploadWithRetry(conversorName, file)
                _state.value = _state.value.copy(
                    pending = (_state.value.pending - 1).coerceAtLeast(0)
                )
            }
        }
    }

    private suspend fun uploadWithRetry(conversorName: String, file: File) {
        semaphore.withPermit {
            val bytes = try {
                val b = file.readBytes()
                file.delete()
                b
            } catch (e: Exception) {
                _state.value = _state.value.copy(lastError = "Erro ao ler ${file.name}")
                return
            }

            ThumbnailCache.generateFromBytes(conversorName, file.name, bytes)

            var backoff = 3_000L
            while (true) {
                val result = ApiClient.uploadFoto(conversorName, file.name, bytes)
                if (result.isSuccess) return

                val e = result.exceptionOrNull()
                if (e !is IOException) {
                    // Erro HTTP (não retentável)
                    _state.value = _state.value.copy(lastError = "Erro ao enviar ${file.name}")
                    return
                }

                // Sem conexão — aguarda e tenta novamente
                delay(backoff)
                backoff = minOf(backoff * 2, 60_000L)
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(lastError = null)
    }
}
