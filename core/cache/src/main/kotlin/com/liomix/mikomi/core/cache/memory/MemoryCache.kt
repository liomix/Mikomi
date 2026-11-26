package com.liomix.mikomi.core.cache.memory

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 进程内缓存（L1）：最近读到的**已解码对象**。
 *
 * ## 它不判断过期
 *
 * 这是这一层最重要的约定：**TTL 只有一个真相，在磁盘的 `savedAt` 里**。
 * 内存层如果自己也记一份时间，就会出现「L1 说还有 3 分钟、L2 说已经过期」——
 * 这两个答案不可能都对，而排查时你会先怀疑数据、再怀疑时间、最后才怀疑这一层。
 *
 * 所以它只做两件事：存最近用过的对象、满了淘汰最久没用的。
 *
 * ## 为什么自己写 LRU 而不用 `android.util.LruCache`
 *
 * 那个类在 `android.util` 下，纯 JVM 测试会拿到 stub（调用即抛异常）。
 * 用 `LinkedHashMap(accessOrder = true)` 十几行就能写完，而且能在普通单测里跑。
 *
 * ## 容量按「条数」算
 *
 * 不按字节：对象大小要反射才估得准，而条数一眼能估 —— 首页 20 页 × 每页 20 条 = 400 个
 * 条目，留 200 个就够覆盖「来回翻两页」的真实场景。
 */
@Singleton
class MemoryCache @Inject constructor() {

    private val maxEntries: Int = DEFAULT_MAX_ENTRIES

    /** accessOrder = true：每次 get 会把条目移到队尾，队头就是要淘汰的那个。 */
    private val entries = object : LinkedHashMap<String, Any>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Any>): Boolean =
            size > maxEntries
    }

    /** 读一个对象；类型不对视为未命中（不抛异常，缓存不该让调用方失败）。 */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(key: String): T? = entries[key] as? T

    /** 写入；同一个 key 覆盖旧值。 */
    fun put(key: String, value: Any) {
        entries[key] = value
    }

    /**
     * 移除单条。
     *
     * 磁盘层写入或失效后**必须**调它：否则页面在别处刷新过、这里还在拿老对象，
     * 表现是「下拉刷新了但内容没变」。
     */
    fun remove(key: String) = entries.remove(key)

    /** 清空：磁盘缓存被整体清除、或用户手动清缓存时调。 */
    fun clear() = entries.clear()

    /** 当前条数（调试与测试用）。 */
    val size: Int
        get() = entries.size

    companion object {
        /** 默认 200 条：够覆盖「首页来回翻两页 + 一次详情」。 */
        const val DEFAULT_MAX_ENTRIES = 200
    }
}
