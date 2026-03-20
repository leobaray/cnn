package com.lbwma.cnn.model

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.io.File

object RefinementStore {
    private lateinit var prefs: SharedPreferences
    private lateinit var pendingDir: File
    private val _pending = MutableStateFlow<Map<String, List<File>>>(emptyMap())
    val pending: StateFlow<Map<String, List<File>>> = _pending.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences("refinement_pending", Context.MODE_PRIVATE)
        pendingDir = File(context.filesDir, "pending_review").apply { mkdirs() }
        migrateFromCache(context.cacheDir)
        cleanup()
        reload()
    }

    /**
     * Migração única: move arquivos de fotos pendentes do cacheDir (versões antigas)
     * para filesDir/pending_review/ (persistente entre atualizações).
     * Atualiza os caminhos no store.
     */
    private fun migrateFromCache(cacheDir: File) {
        val pattern = Regex("^(f\\d{2}_|batch_).+\\.jpg$")
        val editor = prefs.edit()
        var changed = false

        prefs.all.forEach { (k, v) ->
            if (v is String) {
                try {
                    val arr = JSONArray(v)
                    var migrated = false
                    val newArr = JSONArray()
                    for (i in 0 until arr.length()) {
                        val original = File(arr.getString(i))
                        if (original.exists() && original.parent == cacheDir.absolutePath && pattern.matches(original.name)) {
                            // Mover para pendingDir
                            val dest = File(pendingDir, original.name)
                            original.renameTo(dest)
                            newArr.put(dest.absolutePath)
                            migrated = true
                        } else {
                            newArr.put(original.absolutePath)
                        }
                    }
                    if (migrated) {
                        editor.putString(k, newArr.toString())
                        changed = true
                    }
                } catch (_: Exception) {}
            }
        }
        if (changed) editor.apply()

        // Mover também arquivos órfãos no cacheDir que não estão no store
        cacheDir.listFiles()?.forEach { file ->
            if (file.isFile && pattern.matches(file.name)) {
                val dest = File(pendingDir, file.name)
                file.renameTo(dest)
            }
        }
    }

    /**
     * Limpeza de arquivos órfãos no startup:
     * 1. Remove entradas do store cujos arquivos não existem mais
     * 2. Apaga arquivos em pending_review/ que não pertencem a nenhuma entrada
     */
    private fun cleanup() {
        val referencedPaths = mutableSetOf<String>()
        val editor = prefs.edit()
        prefs.all.forEach { (k, v) ->
            if (v is String) {
                try {
                    val arr = JSONArray(v)
                    val valid = (0 until arr.length())
                        .map { File(arr.getString(it)) }
                        .filter { it.exists() }
                    if (valid.isEmpty()) {
                        editor.remove(k)
                    } else if (valid.size < arr.length()) {
                        val newArr = JSONArray()
                        valid.forEach { newArr.put(it.absolutePath) }
                        editor.putString(k, newArr.toString())
                        valid.forEach { referencedPaths.add(it.absolutePath) }
                    } else {
                        valid.forEach { referencedPaths.add(it.absolutePath) }
                    }
                } catch (_: Exception) {
                    editor.remove(k)
                }
            }
        }
        editor.apply()

        // Apagar arquivos órfãos em pending_review/
        pendingDir.listFiles()?.forEach { file ->
            if (file.isFile && file.absolutePath !in referencedPaths) {
                file.delete()
            }
        }
    }

    private fun key(conversor: String, filtroId: Int) = "${conversor}_$filtroId"

    fun save(conversor: String, filtroId: Int, files: List<File>) {
        val k = key(conversor, filtroId)
        val arr = JSONArray()
        files.forEach { arr.put(it.absolutePath) }
        prefs.edit().putString(k, arr.toString()).apply()
        reload()
    }

    fun load(conversor: String, filtroId: Int): List<File> {
        val k = key(conversor, filtroId)
        val json = prefs.getString(k, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length())
                .map { File(arr.getString(it)) }
                .filter { it.exists() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clear(conversor: String, filtroId: Int) {
        prefs.edit().remove(key(conversor, filtroId)).apply()
        reload()
    }

    fun has(conversor: String, filtroId: Int): Boolean = load(conversor, filtroId).isNotEmpty()

    private fun reload() {
        val map = mutableMapOf<String, List<File>>()
        prefs.all.forEach { (k, v) ->
            if (v is String) {
                try {
                    val arr = JSONArray(v)
                    val files = (0 until arr.length())
                        .map { File(arr.getString(it)) }
                        .filter { it.exists() }
                    if (files.isNotEmpty()) map[k] = files
                } catch (_: Exception) {}
            }
        }
        _pending.value = map
    }
}
