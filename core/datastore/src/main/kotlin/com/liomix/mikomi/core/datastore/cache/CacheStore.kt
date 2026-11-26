package com.liomix.mikomi.core.datastore.cache

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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.cacheDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "api_cache")

/**
 * 接口数据的本地副本：`key → 一份 JSON`。
 *
 * ## 它是什么
 *
 * 不是业务表。里面每个值都是「服务端上一次给的响应」，随时可以整块丢掉重建 ——
 * 所以它不需要 schema 版本、不需要迁移，也不参与任何查询。
 *
 * ## 用法只有四件事
 *
 * ```
 * 进页面 → isStale(key, ttl) 判断要不要刷新
 * 拿到数据 → put(key, json)
 * 显示 → observe(key)，解析成模型
 * 清除缓存 → clear()
 * ```
 *
 * 没有 `invalidateAll()`、没有按前缀删除：key 是个位数的常量，需要批量操作就说明
 * key 设计错了。
 *
 * ## 为什么用 DataStore 而不是 Room
 *
 * 缓存的数据形状是「一坨 JSON」，没有查询、没有关联、没有分页。Room 能为这些做的
 * 事情一件都用不上，却要付出 schema 版本与 migration 的代价 —— 而这个库的整个前提
 * 是「可以随时删掉」。用 Room 存缓存，会让「删掉重来」变成「写一个 migration」。
 */
@Singleton
class CacheStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore = context.cacheDataStore

    /** 观察某条缓存。没有就是 `null`；写入新值时会再次发射。 */
    fun observe(key: String): Flow<CacheSnapshot.Entry?> = dataStore.data
        .map { preferences -> preferences.snapshot().entries[key] }
        .distinctUntilChanged()

    /** 读一次。用于「进页面时判断要不要刷新」这类一次性判断。 */
    suspend fun get(key: String): CacheSnapshot.Entry? =
        dataStore.data.first().snapshot().entries[key]

    /**
     * 某条缓存是否已过期。
     *
     * 没有记录也算过期 —— 对调用方来说，「从没拉过」和「拉过但太旧」要做的事一样。
     */
    suspend fun isStale(key: String, ttlMillis: Long): Boolean {
        val entry = get(key) ?: return true
        // 有戳没数据 = 这条缓存无效（比如上次只写了时间戳、或者数据被清掉了）。
        // 只看时间的话，它会「新鲜」好几个小时，这期间页面一直是空的。
        if (entry.payload.isBlank()) return true
        return System.currentTimeMillis() - entry.savedAt > ttlMillis
    }

    /**
     * 写入一条缓存。
     *
     * **内容相同就不写**：榜单一天才变一次，用户点刷新时若内容没变，写进去会让
     * 整条 Flow 空发一次 —— 一屏卡片的列表因此重组一遍，而画面上什么都没变。
     *
     * `edit` 本身是串行的，多页面同时写入不需要额外的锁。
     */
    suspend fun put(key: String, payload: String) {
        dataStore.edit { preferences ->
            val snapshot = preferences.snapshot()
            if (snapshot.entries[key]?.payload == payload) return@edit

            val updated = snapshot.copy(
                entries = snapshot.entries + (key to CacheSnapshot.Entry(
                    payload = payload,
                    savedAt = System.currentTimeMillis(),
                )),
            )
            preferences[SnapshotKey] = json.encodeToString(CacheSnapshot.serializer(), updated)
        }
    }

    /**
     * 只更新时间戳，不写数据。
     *
     * 给「数据自己已经有归属」的页面用：时间表的多源合并、首页的番剧表都落在 Room 里，
     * 缓存层在这里唯一的职责是回答「上次成功拉取是什么时候」，好让进页面时不必重拉。
     */
    suspend fun touch(key: String) {
        dataStore.edit { preferences ->
            val snapshot = preferences.snapshot()
            val updated = snapshot.copy(
                entries = snapshot.entries + (key to CacheSnapshot.Entry(
                    payload = snapshot.entries[key]?.payload.orEmpty(),
                    savedAt = System.currentTimeMillis(),
                )),
            )
            preferences[SnapshotKey] = json.encodeToString(CacheSnapshot.serializer(), updated)
        }
    }

    /** 清空全部缓存：设置页的「清除缓存」与调试用。 */
    suspend fun clear() {
        dataStore.edit { preferences -> preferences.remove(SnapshotKey) }
    }

    /** 把存储里的字符串解析回快照；解析失败按空处理（宁可重拉，也不要因为坏数据卡死）。 */
    @Suppress("TooGenericExceptionCaught")
    private fun Preferences.snapshot(): CacheSnapshot {
        val raw = this[SnapshotKey] ?: return CacheSnapshot()
        return runCatching { json.decodeFromString(CacheSnapshot.serializer(), raw) }
            .getOrElse { CacheSnapshot() }
    }

    private companion object {
        /** 整块缓存存在一个字符串项里。 */
        val SnapshotKey = stringPreferencesKey("snapshot")

        /** `ignoreUnknownKeys`：旧缓存里多出来的字段不致命，少字段走默认值。 */
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
