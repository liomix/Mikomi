package com.liomix.mikomi.data.anime

import com.liomix.mikomi.core.model.AniRpbHotItem
import com.liomix.mikomi.core.model.AniRpbScheduleItem
import com.liomix.mikomi.core.model.RankRegion

/**
 * AniRpb 特殊数据源。
 *
 * 与 Bangumi / AniList 不同，AniRpb 的接口必须经官方 `libzhuque.so` 的本地帧
 * 传输层访问；这里只暴露应用需要的只读能力。
 */
interface AniRpbRepository {

    /**
     * 拉取日漫 / 国漫热播榜。
     *
     * 频道 id 启动时通过 `type.listActive` 动态解析；接口失败会向上抛出，
     * 由 UI 显示可重试错误态。
     */
    suspend fun fetchHotRanking(region: RankRegion): List<AniRpbHotItem>

    /**
     * 拉取日漫 / 国漫的**完整榜单**（`video.categoryList`，按热度排序，自动翻页）。
     *
     * 与 [fetchHotRanking] 的区别：`home.load` 的 hot 模块只有首屏那十几条，
     * 这里走频道整榜接口，能取满 [limit] 条（默认 100）。
     *
     * 整榜接口失败时降级到 [fetchHotRanking]，不向上抛错 —— 榜单是展示型数据，
     * 有短榜也比空榜好；调用方仍可据返回条数判断是否需要提示。
     */
    suspend fun fetchRanking(region: RankRegion, limit: Int = DEFAULT_RANK_LIMIT): List<AniRpbHotItem>

    /**
     * 拉取指定日期的更新表（`calendar.videos`）。
     *
     * [playDate] 为本地日期 `yyyy-MM-dd`；返回值保留服务端下发的 `updateDesc`，
     * 不在数据层自行拼装更新文案。
     */
    suspend fun fetchCalendar(playDate: String): List<AniRpbScheduleItem>

    companion object {
        /** 榜单默认条数：日漫 / 国漫各取 100。 */
        const val DEFAULT_RANK_LIMIT = 100
    }
}
