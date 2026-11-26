package com.liomix.mikomi.data.anime.repository.Ipml

import com.liomix.mikomi.core.model.anime.AnimeDetail
import com.liomix.mikomi.core.model.anime.CreatorComment
import com.liomix.mikomi.core.model.anime.CreatorDetail
import com.liomix.mikomi.data.anime.remote.bangumi.BangumiSource
import com.liomix.mikomi.data.anime.repository.DetailRepository
import com.liomix.mikomi.data.anime.repository.toBangumiSubjectId
import javax.inject.Inject

/**
 * 详情实现：**只做 id 解析**，其余全部委托数据源。
 *
 * 详情、角色、职员三块在 [BangumiSource.detail] 里已经并行取好，这一层不再拼装 ——
 * 拼装逻辑散在两处时，「为什么角色出不来」要同时看两个文件。
 */
internal class DetailRepositoryIpml @Inject constructor(
    private val source: BangumiSource,
) : DetailRepository {

    override suspend fun detail(animeId: String): AnimeDetail {
        // 非 Bangumi 条目（例如只在本站有的番）：给一个空详情，页面按「信息不全」展示，
        // 而不是抛错 —— 详情页不该因为一个 id 前缀就打不开。
        val bangumiId = animeId.toBangumiSubjectId()
            ?: return AnimeDetail(id = animeId, title = "")
        return source.detail(bangumiId)
    }

    override suspend fun creatorDetail(creatorId: Int, isCharacter: Boolean): CreatorDetail =
        source.creatorDetail(creatorId, isCharacter)

    override suspend fun creatorComments(creatorId: Int, isCharacter: Boolean): List<CreatorComment> =
        source.creatorComments(creatorId, isCharacter)
}
