package com.liomix.mikomi.core.cache.disk

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlin.time.Duration
import com.liomix.mikomi.core.cache.CacheSnapshot
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.diskCacheDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "disk_cache")

/**
 * 磁盘缓存（L2）：`key → 一份 JSON + 写入时刻`。
 *
 * ## 它不是业务表
 *
 * 每个值都是「服务端上一次给的响应」，随时可以整块丢掉重建 —— 所以没有 schema 版本、
 * 没有迁移、也不参与任何查询。用 Room 存这种东西，会让「删掉重来」变成「写一个 migration」。
 *
 * ## TTL 的唯一真相在这里
 *
 * [isStale] 是唯一判过期的地方。内存层（`MemoryCache`）不自己计时，只镜像这里读出来的
 * 对象 —— 两层各记一份时间，必然会出现互相矛盾的结论。
 *
 * ## 与 `core:datastore` 的 `CacheStore` 的关系
 *
 * 功能重叠，**这是迁移的第一步**：这一版补了两件事 ——
 * 用 `Duration` 而不是毫秒常量（`5.minutes` 比 `300_000` 少一次心算），
 * 以及把「有戳没数据」判定为过期写进文档而不是散在调用方。
 * 迁移完成后 `CacheStore` 删除，所有 Repository 改注入本类。
 */
@Singleton
class DiskCache @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore = context.diskCacheDataStore
    private val json = Json { ignoreUnknownKeys = true }

    /** 观察某条缓存；写入新值时会再次发射。没有就是 `null`。 */
    fun observe(key: String): Flow<CacheSnapshot.Entry?> = dataStore.data
        .map { preferences -> preferences.snapshot().entries[key] }
        .distinctUntilChanged()

    /** 读一次。用于「进页面判断要不要刷新」这类一次性判断。 */
    suspend fun get(key: String): CacheSnapshot.Entry? = dataStore.data.first().snapshot().entries[key]

    /** 写入；`savedAt` 由这里统一盖，避免调用方各自 `System.currentTimeMillis()`。 */
    suspend fun put(key: String, payload: String) {
        dataStore.edit { preferences ->
            val snapshot = preferences.snapshot()
            preferences[SnapshotKey] = json.encodeToString(
                CacheSnapshot.serializer(),
                snapshot.copy(
                    entries = snapshot.entries + (key to CacheSnapshot.Entry(payload, System.currentTimeMillis())),
                ),
            )
        }
    }

    /**
     * 是否该重新拉取。
     *
     * 三种情况都算过期：没有这条、**有戳但数据为空**、超过 [ttl]。
     * 中间那条容易被漏掉：只写时间戳忘了写数据时，那一 key 会「新鲜」好几个小时，
     * 而这期间页面一直是空的。
     */
    suspend fun isStale(key: String, ttl: Duration): Boolean {
        val entry = get(key) ?: return true
        if (entry.payload.isBlank()) return true
        return System.currentTimeMillis() - entry.savedAt > ttl.inWholeMilliseconds
    }

    /** 删一条。 */
    suspend fun invalidate(key: String) {
        dataStore.edit { preferences ->
            val snapshot = preferences.snapshot()
            preferences[SnapshotKey] = json.encodeToString(
                CacheSnapshot.serializer(),
                snapshot.copy(entries = snapshot.entries - key),
            )
        }
    }

    /** 清空全部：设置页的「清除缓存」与调试用。 */
    suspend fun clear() {
        dataStore.edit { preferences -> preferences.clear() }
    }

    private fun Preferences.snapshot(): CacheSnapshot =
        this[SnapshotKey]?.let { raw ->
            runCatching { json.decodeFromString(CacheSnapshot.serializer(), raw) }.getOrNull()
        } ?: CacheSnapshot()

    private companion object {
        val SnapshotKey = androidx.datastore.preferences.core.stringPreferencesKey("snapshot")
    }
}
