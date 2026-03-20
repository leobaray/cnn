package com.lbwma.cnn.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.IOException

data class UploadState(
    val pending: Int = 0,
    val lastError: String? = null,
    // Uploads concluídos desde o último reset: lista de fileNames
    val completed: List<String> = emptyList()
)

object UploadManager {
    // Escopo próprio — sobrevive a navegação entre telas
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Máximo 3 uploads simultâneos — evita OOM e sobrecarga de rede
    private val semaphore = Semaphore(3)

    // Estado isolado por conversorName — um login não bloqueia o outro
    private val _states = MutableStateFlow<Map<String, UploadState>>(emptyMap())

    fun stateFor(conversorName: String): Flow<UploadState> =
        _states.map { it.getOrDefault(conversorName, UploadState()) }

    private fun updateState(conversorName: String, block: (UploadState) -> UploadState) {
        _states.update { map ->
            val current = map.getOrDefault(conversorName, UploadState())
            map + (conversorName to block(current))
        }
    }

    fun enqueue(conversorName: String, files: List<File>) {
        updateState(conversorName) { it.copy(pending = it.pending + files.size) }
        files.forEach { file ->
            scope.launch {
                val fileName = file.name
                uploadWithRetry(conversorName, file)
                updateState(conversorName) { s ->
                    s.copy(
                        pending = (s.pending - 1).coerceAtLeast(0),
                        completed = s.completed + fileName
                    )
                }
            }
        }
    }

    fun clearCompleted(conversorName: String) {
        updateState(conversorName) { it.copy(completed = emptyList()) }
    }

    fun clearError(conversorName: String) {
        updateState(conversorName) { it.copy(lastError = null) }
    }

    private suspend fun uploadWithRetry(conversorName: String, file: File) {
        semaphore.withPermit {
            val bytes = try {
                val b = file.readBytes()
                file.delete()
                b
            } catch (e: Exception) {
                updateState(conversorName) { it.copy(lastError = "Erro ao ler ${file.name}") }
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
                    updateState(conversorName) { it.copy(lastError = "Erro ao enviar ${file.name}") }
                    return
                }

                // Sem conexão — aguarda e tenta novamente
                delay(backoff)
                backoff = minOf(backoff * 2, 60_000L)
            }
        }
    }
}
