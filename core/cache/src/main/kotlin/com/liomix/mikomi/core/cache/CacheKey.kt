package com.liomix.mikomi.core.cache

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * 缓存的 key：**一个页面一条**。
 *
 * 粒度按「一次请求对应一份数据」定，不按页面上有几块 UI —— 榜单的两个 tab 在同一个
 * 响应里，所以只有 `rank` 一个 key，而不是 `rank:japanese` / `rank:chinese`。
 * 拆细了只会让「刷新一次要写两条」这种本不该存在的协调出现。
 *
 * 常量而不是字符串字面量：key 同时是持久化标识，写错一个字母的表现是「缓存永远不命中」，
 * 那种 bug 不会崩、不会报错，只会一直重新请求。
 */
object CacheKey {
    const val BANNER = "banner"
    const val HOME = "home"
    const val SCHEDULE = "schedule"
    const val RANK = "rank"
    const val HOT = "hot"
    const val CATEGORY = "category"

    /** 单条详情的前缀，后接领域 id（`detail:bangumi:123`）。 */
    const val DETAIL = "detail:"
}

/**
 * 每条缓存多久算「旧」。
 *
 * 用 [Duration] 而不是毫秒常量：`5.minutes` 比 `300_000` 少一次心算，也少一次
 * 「这是秒还是毫秒」的怀疑。
 *
 * 只影响**要不要后台刷新**，不影响要不要显示 —— 过期的数据照样先出来，这是「切页不白屏」
 * 的关键。数值按数据的变脸速度定：排名会动（热播）→ 5 分钟；一天才换（榜单、时间表）
 * → 1 小时 / 6 小时。
 */
object CacheTtl {
    val BANNER: Duration = 30.minutes
    val HOME: Duration = 10.minutes
    val SCHEDULE: Duration = 6.hours
    val RANK: Duration = 1.hours
    val HOT: Duration = 5.minutes
    val CATEGORY: Duration = 1.hours
}
