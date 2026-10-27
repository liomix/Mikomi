package com.liomix.mikomi.data.anime

import com.liomix.mikomi.core.model.TeasingComment

/** 番剧吐槽分页结果。 */
data class TeasingPage(
    val comments: List<TeasingComment>,
    /** 是否还有下一页（本页返回条数达到 limit）。 */
    val hasMore: Boolean,
)

/**
 * 番剧吐槽数据门面（Bangumi 短评，分页拉取）。
 *
 * 吐槽不进本地库：数据量大、时效性强，按需分页拉取即可。
 */
interface AnimeCommentRepository {

    /**
     * 拉取一页番剧吐槽。
     *
     * [animeId] 为带前缀的领域 id（`bangumi:` / `anilist:`），内部解析为 Bangumi 数字 id。
     * 失败向上抛出，由上层展示重试。
     */
    suspend fun fetchTeasingComments(
        animeId: String,
        limit: Int,
        offset: Int,
    ): TeasingPage
}
