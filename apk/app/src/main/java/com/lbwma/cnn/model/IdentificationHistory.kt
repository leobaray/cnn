package com.lbwma.cnn.model

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class IdentificationEntry(
    val classe: String,
    val confianca: Float,
    val timestamp: Long
)

object IdentificationHistory {
    private const val PREFS_NAME = "identification_history_v1"
    private const val KEY_ENTRIES = "entries"
    private const val MAX_ENTRIES = 20

    private lateinit var prefs: SharedPreferences

    private val _entries = MutableStateFlow<List<IdentificationEntry>>(emptyList())
    val entries: StateFlow<List<IdentificationEntry>> = _entries.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        reload()
    }

    fun add(classe: String, confianca: Float) {
        val current = _entries.value.toMutableList()
        current.add(0, IdentificationEntry(classe, confianca, System.currentTimeMillis()))
        val trimmed = current.take(MAX_ENTRIES)
        save(trimmed)
    }

    fun clear() {
        save(emptyList())
    }

    private fun save(list: List<IdentificationEntry>) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(JSONObject().apply {
                put("classe", e.classe)
                put("confianca", e.confianca.toDouble())
                put("timestamp", e.timestamp)
            })
        }
        prefs.edit().putString(KEY_ENTRIES, arr.toString()).apply()
        _entries.value = list
    }

    private fun reload() {
        val json = prefs.getString(KEY_ENTRIES, null) ?: return
        try {
            val arr = JSONArray(json)
            val list = (0 until arr.length()).mapNotNull { i ->
                try {
                    val obj = arr.getJSONObject(i)
                    IdentificationEntry(
                        classe = obj.getString("classe"),
                        confianca = obj.getDouble("confianca").toFloat(),
                        timestamp = obj.getLong("timestamp")
                    )
                } catch (_: Exception) { null }
            }
            _entries.value = list
        } catch (_: Exception) {}
    }
}
