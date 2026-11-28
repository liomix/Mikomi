package com.liomix.mikomi.data.anime.repository.Impl

import com.liomix.mikomi.core.model.anime.CategoryPage
import com.liomix.mikomi.core.model.anime.CategoryQuery
import com.liomix.mikomi.core.model.anime.CategoryRegion
import com.liomix.mikomi.core.model.anime.CategorySort
import com.liomix.mikomi.data.anime.remote.bangumi.BangumiSource
import com.liomix.mikomi.data.anime.repository.CategoryRepository
import javax.inject.Inject

/**
 * 分类浏览：把四个筛选维度翻成 Bangumi 搜索的 `sort` 与 `filter`。
 *
 * **翻译只在这里做**。`CategoryRegion` 到「日本 / 国产动画」这两个 tag 的对应、
 * `CategorySort` 到 `match / heat / rank` 的对应，都是 Bangumi 的知识 —— 泄漏到
 * `core:model` 或界面，就等于把「换数据源」变成改三层。
 */
internal class CategoryRepositoryImpl @Inject constructor(
    private val source: BangumiSource,
) : CategoryRepository {

    override suspend fun browse(query: CategoryQuery, page: Int): CategoryPage {
        val result = source.browse(
            sort = query.sort.bangumiSort,
            // 产地是 tag，不是独立字段：和用户勾的标签一起拼成同一个数组，服务端按交集筛。
            // 空集天然什么都不加，不需要为「全部」写分支。
            tag = query.region.bangumiTags + query.tags,
            airDate = query.year.toAirDateRange(),
            limit = CategoryRepository.PAGE_SIZE,
            offset = page * CategoryRepository.PAGE_SIZE,
        )
        return CategoryPage(
            items = result.items,
            // 服务端没有「总数」以外的游标信息，用「这一页拿满了」判断还有下一页。
            // 刚好整页用完时多请求一次空页，比「提前判定没有下一页」安全。
            hasMore = result.items.size >= CategoryRepository.PAGE_SIZE,
        )
    }
}

/** 产地 → Bangumi 的地区标签。 */
private val CategoryRegion.bangumiTags: List<String>
    get() = when (this) {
        CategoryRegion.JAPAN -> listOf(TAG_JAPAN)
        CategoryRegion.CHINA -> listOf(TAG_CHINA)
    }

/**
 * 排序 → 服务端的 `sort`。
 *
 * 服务端只有 `match`（相关度）/ `heat`（热度）/ `rank`（站内排名）三种。
 * 「最新」用 `match` 近似 —— 这个接口**没有按日期排序**的能力，日期只能用来筛
 * （见 [toAirDateRange]）。「评分」用 `rank` 近似：站内排名就是综合评分的排序结果。
 */
private val CategorySort.bangumiSort: String
    get() = when (this) {
        CategorySort.LATEST -> "match"
        CategorySort.HOT -> "heat"
        CategorySort.SCORE -> "rank"
    }

/** 年份 → 放送日期区间。服务端用字符串比较，所以是「大于等于当年 1 月 1 日、小于次年 1 月 1 日」。 */
private fun Int?.toAirDateRange(): List<String> = this?.let { year ->
    listOf(">=$year-01-01", "<${year + 1}-01-01")
}.orEmpty()

private const val TAG_JAPAN = "日本"
private const val TAG_CHINA = "国产动画"
