package com.lbwma.cnn.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import java.util.concurrent.ConcurrentHashMap

data class UploadState(
    val pending: Int = 0,
    val total: Int = 0,
    val lastError: String? = null,
    val completed: List<String> = emptyList(),
    val retryingFiles: Set<String> = emptySet()
) {
    val progress: Float get() = if (total == 0) 0f else (total - pending).toFloat() / total
    val isActive: Boolean get() = pending > 0
}

object UploadManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val semaphore = Semaphore(3)

    private val _states = MutableStateFlow<Map<String, UploadState>>(emptyMap())

    // Cada upload em andamento — permite cancelamento individual e por conversor
    private val jobs = ConcurrentHashMap<String, MutableList<Job>>()

    fun stateFor(conversorName: String): Flow<UploadState> =
        _states.map { it.getOrDefault(conversorName, UploadState()) }

    private fun updateState(conversorName: String, block: (UploadState) -> UploadState) {
        _states.update { map ->
            val current = map.getOrDefault(conversorName, UploadState())
            map + (conversorName to block(current))
        }
    }

    fun enqueue(conversorName: String, files: List<File>) {
        if (files.isEmpty()) return
        updateState(conversorName) {
            it.copy(pending = it.pending + files.size, total = it.total + files.size)
        }
        val jobList = jobs.getOrPut(conversorName) { mutableListOf() }
        files.forEach { file ->
            val job = scope.launch {
                val fileName = file.name
                val ok = uploadWithRetry(conversorName, file)
                updateState(conversorName) { s ->
                    val newPending = (s.pending - 1).coerceAtLeast(0)
                    s.copy(
                        pending = newPending,
                        total = if (newPending == 0) 0 else s.total,
                        completed = if (ok) s.completed + fileName else s.completed,
                        retryingFiles = s.retryingFiles - fileName
                    )
                }
            }
            jobList.add(job)
            job.invokeOnCompletion { jobList.remove(job) }
        }
    }

    fun cancelAll(conversorName: String) {
        jobs[conversorName]?.toList()?.forEach { it.cancel() }
        jobs[conversorName]?.clear()
        updateState(conversorName) {
            it.copy(pending = 0, total = 0, retryingFiles = emptySet())
        }
    }

    fun clearCompleted(conversorName: String) {
        updateState(conversorName) { it.copy(completed = emptyList()) }
    }

    fun clearError(conversorName: String) {
        updateState(conversorName) { it.copy(lastError = null) }
    }

    private suspend fun uploadWithRetry(conversorName: String, file: File): Boolean {
        return semaphore.withPermit {
            val bytes = try {
                val b = file.readBytes()
                file.delete()
                b
            } catch (e: Exception) {
                updateState(conversorName) { it.copy(lastError = "Erro ao ler ${file.name}") }
                return@withPermit false
            }

            ThumbnailCache.generateFromBytes(conversorName, file.name, bytes)

            var backoff = 3_000L
            var attempts = 0
            while (true) {
                val result = ApiClient.uploadFoto(conversorName, file.name, bytes)
                if (result.isSuccess) return@withPermit true

                val e = result.exceptionOrNull()
                attempts++

                // Erro permanente — não retentável
                if (e !is ApiError.Network && e !is ApiError.Timeout) {
                    updateState(conversorName) {
                        it.copy(lastError = "Falha em ${file.name}: ${e?.message ?: "erro"}")
                    }
                    return@withPermit false
                }

                updateState(conversorName) {
                    it.copy(retryingFiles = it.retryingFiles + file.name)
                }

                delay(backoff)
                backoff = minOf(backoff * 2, 60_000L)

                // Após muitas tentativas, desiste — mas mantém o cache em RAM (já apagamos o file)
                if (attempts >= 10) {
                    updateState(conversorName) {
                        it.copy(lastError = "Sem rede após várias tentativas (${file.name})")
                    }
                    return@withPermit false
                }
            }
            @Suppress("UNREACHABLE_CODE")
            false
        }
    }
}
