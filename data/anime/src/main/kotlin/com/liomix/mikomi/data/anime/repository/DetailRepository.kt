package com.liomix.mikomi.data.anime.repository

import com.liomix.mikomi.core.model.anime.AnimeDetail
import com.liomix.mikomi.core.model.anime.CreatorComment
import com.liomix.mikomi.core.model.anime.CreatorDetail

/**
 * 详情页：番剧详情 + 主创 + **主创评论**。
 *
 * 主创评论放在这里而不是 [CommentRepository]：那条链路评的是番剧，这条评的是人，
 * 两者的入口、形态、递归结构都不同。混在一个接口里，调用方要先知道「这次我要的是哪种评论」
 * 再去挑方法，而正确的做法是它们本来就在不同的页面上。
 */
interface DetailRepository {

    /**
     * 番剧详情，含角色与制作人员。
     *
     * 一次给全而不是拆成三个调用：详情页打开时这三块都要，拆开会让首屏出现
     * 「简介出来了、角色还在转圈」的半截状态。
     *
     * @param animeId 领域 id（带前缀，如 `bangumi:123`）；非 Bangumi 条目返回空详情。
     */
    suspend fun detail(animeId: String): AnimeDetail

    /** 主创详情：点角色或职员后展开的那一层。 */
    suspend fun creatorDetail(creatorId: Int, isCharacter: Boolean): CreatorDetail

    /** 主创评论：角色页与人物页共用。 */
    suspend fun creatorComments(creatorId: Int, isCharacter: Boolean): List<CreatorComment>
}
