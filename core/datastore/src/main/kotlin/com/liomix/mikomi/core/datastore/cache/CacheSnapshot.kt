package com.liomix.mikomi.core.datastore.cache

import kotlinx.serialization.Serializable

/**
 * 整个缓存文件：`页面 key → 一份 JSON`。
 *
 * 存成**一个字段**而不是「每个 key 两个偏好项」（payload + 时间戳）：DataStore 的
 * 写入单位是文件，拆成十几项只会在 `edit` 里多几次读写，还多一种「只写了一半」的状态。
 *
 * 之所以能这么简单：缓存的 key 只有个位数（见 [CacheKey]），整块读写的成本可以忽略；
 * 一旦需要按字段查询，就说明这不该是缓存，而是业务表。
 */
@Serializable
data class CacheSnapshot(
    val entries: Map<String, Entry> = emptyMap(),
) {
    /** 一条缓存：[payload] 是原样返回给解析器的 JSON，[savedAt] 是写入时刻（毫秒）。 */
    @Serializable
    data class Entry(
        val payload: String,
        val savedAt: Long,
    )
}
