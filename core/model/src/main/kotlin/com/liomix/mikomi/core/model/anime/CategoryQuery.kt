package com.liomix.mikomi.core.model.anime

import com.liomix.mikomi.core.model.Anime

/**
 * 分类浏览的筛选条件。
 *
 * 四个维度**正交**：换年份不该把已选的标签清掉，所以它们合成一个不可变值 ——
 * 任何一次筛选都是 `copy` 出一份新条件，界面不必自己维护「哪个还没选」。
 *
 * 每一项的 `null` 都表示「不限」，而不是一个空串：那让「全部」和「选了某个空值」
 * 在类型上就是两件事。
 */
data class CategoryQuery(
    val region: CategoryRegion = CategoryRegion.JAPAN,
    /** 标签（「热血」「悬疑」…）；`null` = 全部。 */
    val tag: String? = null,
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
 */
object CategoryTags {
    val values: List<String> = listOf(
        "热血", "悬疑", "日常", "科幻",
        "恋爱", "搞笑", "奇幻", "运动",
        "校园", "治愈", "机战", "历史",
    )
}
