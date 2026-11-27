package com.liomix.mikomi.data.anime.repository

import com.liomix.mikomi.core.model.anime.CategoryPage
import com.liomix.mikomi.core.model.anime.CategoryQuery

/**
 * 分类浏览：按「产地 + 标签 + 年份 + 排序」翻页。
 *
 * ## 不缓存
 *
 * 条件的组合是无限的（3 产地 × 12 标签 × 12 年份 × 3 排序），缓存只会命中少数几个热门
 * 组合，其余全在冷路径上；换来的是「换一个标签立刻有结果」，而不是等一遍缓存过期。
 * 首页那种「一次响应喂一屏」的数据才值得落盘，翻页浏览不值得。
 *
 * ## 页码而不是游标
 *
 * [page] 从 0 开始。服务端这个接口只认 `offset`，没有稳定游标可用 —— 翻页过程中若有新条目
 * 上架，相邻页可能重复一条。客户端按 id 去重（见 ViewModel），不去和服务端较劲。
 */
interface CategoryRepository {

    suspend fun browse(query: CategoryQuery, page: Int): CategoryPage

    companion object {
        /** 一页 24 条：3 列网格正好 8 行。 */
        const val PAGE_SIZE = 24
    }
}
