package com.liomix.mikomi.data.anime.repository

import com.liomix.mikomi.core.model.anime.AnimeComment

/**
 * 番剧评论的一页。
 *
 * [hasMore] 由「本页拿到的条数是否填满 limit」判断，不依赖服务端的 total ——
 * Bangumi 的 `total` 与实际可翻页条数并不总一致，用它判断会出现「点了加载更多什么都没有」。
 */
data class CommentPage(
    val items: List<AnimeComment> = emptyList(),
    val hasMore: Boolean = false,
)

/**
 * 番剧评论：详情页的「短评」区。
 *
 * 只有一条能力 —— 分页拉取。评论不缓存（时效性强、用户翻两页就走），也不落库。
 * 主创评论不在这里，见 [DetailRepository.creatorComments]。
 */
interface CommentRepository {

    /**
     * 拉取一页评论。
     *
     * @param animeId 领域 id（带前缀，如 `bangumi:123`）；非 Bangumi 条目返回空页。
     */
    suspend fun comments(
        animeId: String,
        limit: Int = PAGE_SIZE,
        offset: Int = 0,
    ): CommentPage

    companion object {
        /** 一页 20 条：Bangumi 评论接口的常用分页大小。 */
        const val PAGE_SIZE = 20
    }
}
