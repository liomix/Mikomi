package com.liomix.mikomi.core.datastore.cache

import kotlinx.serialization.json.Json

/**
 * 缓存用的 JSON 配置。**只此一份**。
 *
 * 两处配置各写一份的结果是：写入时 `encodeDefaults = true`、读出时忘了配，
 * 于是默认值字段在文件里有、反序列化时却被丢掉 —— 这类问题不会报错，只会表现为
 * 「刷新后某个字段突然变空了」。
 *
 * `ignoreUnknownKeys`：缓存是旧版本的产物时，多出来的字段应该被忽略而不是让整条解析失败。
 */
object CacheJson {
    @PublishedApi
    internal val instance: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** 模型 → 存进缓存的字符串。 */
    inline fun <reified T> encode(value: T): String = instance.encodeToString(value)

    /**
     * 存进缓存的字符串 → 模型；解析失败返回 `null`。
     *
     * 失败当成「没有缓存」处理：宁可重新请求，也不要因为一条坏数据让页面卡在错误态。
     */
    inline fun <reified T> decode(raw: String): T? =
        runCatching { instance.decodeFromString<T>(raw) }.getOrNull()
}
