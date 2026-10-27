package com.liomix.mikomi.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.searchHistoryDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "search_history")

/**
 * 搜索历史数据源（DataStore）。
 *
 * 以 JSON 数组持久化关键词列表，最新在前、去重、上限 [MAX_HISTORY_COUNT] 条。
 */
@Singleton
class SearchHistoryDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore = context.searchHistoryDataStore

    /** 搜索历史（最新在前）；数据损坏时降级为空列表，不阻断搜索页。 */
    val history: Flow<List<String>> = dataStore.data.map { prefs ->
        prefs[KEY_HISTORY]?.let { raw ->
            runCatching { json.decodeFromString<List<String>>(raw) }.getOrNull()
        }.orEmpty()
    }

    /** 记录一次搜索：已存在则提到最前，超出上限截断。 */
    suspend fun add(keyword: String) {
        val normalized = keyword.trim()
        if (normalized.isEmpty()) return
        dataStore.edit { prefs ->
            val current = prefs[KEY_HISTORY]?.let { raw ->
                runCatching { json.decodeFromString<List<String>>(raw) }.getOrNull()
            }.orEmpty()
            val updated = (listOf(normalized) + current.filterNot { it == normalized })
                .take(MAX_HISTORY_COUNT)
            prefs[KEY_HISTORY] = json.encodeToString(updated)
        }
    }

    /** 删除单条历史。 */
    suspend fun remove(keyword: String) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_HISTORY]?.let { raw ->
                runCatching { json.decodeFromString<List<String>>(raw) }.getOrNull()
            }.orEmpty()
            prefs[KEY_HISTORY] = json.encodeToString(current.filterNot { it == keyword })
        }
    }

    /** 清空历史。 */
    suspend fun clear() {
        dataStore.edit { prefs -> prefs.remove(KEY_HISTORY) }
    }

    private companion object {
        val KEY_HISTORY = stringPreferencesKey("keywords")
        const val MAX_HISTORY_COUNT = 20
        val json = Json { ignoreUnknownKeys = true }
    }
}
