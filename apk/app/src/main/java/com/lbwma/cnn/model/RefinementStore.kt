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
    private val _pending = MutableStateFlow<Map<String, List<File>>>(emptyMap())
    val pending: StateFlow<Map<String, List<File>>> = _pending.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences("refinement_pending", Context.MODE_PRIVATE)
        reload()
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
