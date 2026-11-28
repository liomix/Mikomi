package com.liomix.mikomi.core.model.anime

import com.liomix.mikomi.core.model.Anime

/**
 * 分类浏览的筛选条件。
 *
 * 四个维度**正交**：换年份不该把已选的标签清掉，所以它们合成一个不可变值 ——
 * 任何一次筛选都是 `copy` 出一份新条件，界面不必自己维护「哪个还没选」。
 *
 * 「不限」在类型上看得出来：可选维度用 `null`，标签那种可多选的用**空集** ——
 * 都不是「有值的空串」，所以 `全部` 与「选了某个空值」永远是两件事。
 */
data class CategoryQuery(
    val region: CategoryRegion = CategoryRegion.JAPAN,
    /**
     * 标签（「热血」「悬疑」…），**可多选**；空集 = 全部。
     *
     * 多选在服务端就是一个 `tag` 数组、彼此取交集（同时满足），不需要客户端做二次过滤 ——
     * 所以这里存的是「用户勾了哪几个」，而不是「已经算好的结果集」。
     */
    val tags: Set<String> = emptySet(),
    /** 放送年份；`null` = 全部。 */
    val year: Int? = null,
    val sort: CategorySort = CategorySort.LATEST,
)

/** 产地。 */
enum class CategoryRegion { JAPAN, CHINA }

/** 排序方式。 */
enum class CategorySort { LATEST, HOT, SCORE }

/** 一页分类结果。 */
data class CategoryPage(
    val items: List<Anime> = emptyList(),
    /** 服务端这一页还有下一条吗。 */
    val hasMore: Boolean = false,
)

/**
 * 标签候选。
 *
 * 服务端的标签是开放集合（任何人都能打），这里只列**常用且能筛出结果**的一批：
 * 全量标签表既不现实，也会让下拉变成一个需要搜索的列表。要新标签直接加进来。
 *
 * 条数不是随手写的：分类页的标签弹窗一屏是 4 列 × 5 行 = 20 格，**第一页必须铺满**
 * 才有「左滑看更多」可言。少于 20 条时弹窗只有一页、铺不满，滑动手势也成了摆设。
 */
object CategoryTags {
    val values: List<String> = listOf(
        "热血", "战斗", "冒险", "奇幻",
        "科幻", "机战", "悬疑", "推理",
        "恋爱", "校园", "日常", "搞笑",
        "治愈", "运动", "音乐", "历史",
        "战争", "青春", "犯罪", "后宫",
        "百合", "猎奇", "催泪", "单元剧",
    )
}
