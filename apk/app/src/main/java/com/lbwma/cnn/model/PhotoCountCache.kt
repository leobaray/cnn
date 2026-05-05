package com.lbwma.cnn.model

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Cache persistente de contagens de fotos por conversor e por filtro.
 *
 * Resolve o problema descrito em a_resolver.md: o estado "completo" (verde)
 * de um conversor não pode depender apenas do carregamento da página — deve
 * ser persistido localmente assim que atingido e mantido até observar o
 * contrário.
 */
object PhotoCountCache {
    private const val PREFS_NAME = "photo_counts_v1"
    private const val THRESHOLD_KEY_PREFIX = "complete_"

    private lateinit var prefs: SharedPreferences

    private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val counts: StateFlow<Map<String, Int>> = _counts.asStateFlow()

    private val _completedConversores = MutableStateFlow<Set<String>>(emptySet())
    val completedConversores: StateFlow<Set<String>> = _completedConversores.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        reload()
    }

    private fun reload() {
        val countsMap = mutableMapOf<String, Int>()
        val completed = mutableSetOf<String>()
        prefs.all.forEach { (k, v) ->
            when {
                k.startsWith(THRESHOLD_KEY_PREFIX) && v is Boolean && v -> {
                    completed.add(k.removePrefix(THRESHOLD_KEY_PREFIX))
                }
                v is Int -> countsMap[k] = v
            }
        }
        _counts.value = countsMap
        _completedConversores.value = completed
    }

    /** Conversor: contagem total de fotos. */
    fun setCount(conversor: String, count: Int) {
        prefs.edit().putInt(conversor, count).apply()
        _counts.update { it + (conversor to count) }
        if (count >= TARGET_TOTAL) markComplete(conversor)
        else if (isComplete(conversor)) markIncomplete(conversor)
    }

    fun getCount(conversor: String): Int = prefs.getInt(conversor, 0)

    /** Filtro IA: contagem por (conversor, filtroId). */
    fun setFilterCount(conversor: String, filtroId: Int, count: Int) {
        val key = "${conversor}__f$filtroId"
        prefs.edit().putInt(key, count).apply()
        _counts.update { it + (key to count) }
    }

    fun getFilterCount(conversor: String, filtroId: Int): Int =
        prefs.getInt("${conversor}__f$filtroId", 0)

    /** Marca conversor como completo (>= 4400 fotos). Persiste para reagir imediatamente. */
    fun markComplete(conversor: String) {
        val key = "$THRESHOLD_KEY_PREFIX$conversor"
        if (!prefs.getBoolean(key, false)) {
            prefs.edit().putBoolean(key, true).apply()
            _completedConversores.update { it + conversor }
        }
    }

    fun markIncomplete(conversor: String) {
        val key = "$THRESHOLD_KEY_PREFIX$conversor"
        if (prefs.getBoolean(key, false)) {
            prefs.edit().putBoolean(key, false).apply()
            _completedConversores.update { it - conversor }
        }
    }

    fun isComplete(conversor: String): Boolean = prefs.getBoolean("$THRESHOLD_KEY_PREFIX$conversor", false)

    fun removeConversor(conversor: String) {
        val editor = prefs.edit()
        editor.remove(conversor)
        editor.remove("$THRESHOLD_KEY_PREFIX$conversor")
        // Remove contagens de filtros desse conversor
        prefs.all.keys.filter { it.startsWith("${conversor}__f") }.forEach { editor.remove(it) }
        editor.apply()
        _counts.update { it - conversor }
        _completedConversores.update { it - conversor }
    }

    fun renameConversor(old: String, new: String) {
        val count = getCount(old)
        val complete = isComplete(old)
        val filterCounts = prefs.all.entries
            .filter { it.key.startsWith("${old}__f") }
            .associate { it.key.removePrefix("${old}__") to (it.value as? Int ?: 0) }

        removeConversor(old)
        setCount(new, count)
        if (complete) markComplete(new)
        filterCounts.forEach { (suffix, c) ->
            val filtroId = suffix.removePrefix("f").toIntOrNull() ?: return@forEach
            setFilterCount(new, filtroId, c)
        }
    }

    const val TARGET_TOTAL = FOTOS_POR_FILTRO * 16
}
