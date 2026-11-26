package com.liomix.mikomi.data.anime.local

import com.liomix.mikomi.core.database.dao.AnimeDao
import com.liomix.mikomi.core.model.anime.AnimeDetail
import com.liomix.mikomi.data.anime.mapper.toEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 把远程拿到的详情写回本地库。
 *
 * 单独一个类而不是塞进某个 Repository：详情页与播放页都要落库（播放页在没有标题时也会
 * 拉一次），而这条写入规则**必须只有一份**——
 *
 * > 已有行保留原来的 `source` 与 `homeRank`。
 *
 * 首页网格按 `source = 'home'` 查询，如果把点开过的那条覆盖成 `'detail'`，它就会从首页
 * 消失、后面的卡片整排左移。这个 bug 曾经真实发生过，规则留在这一个地方就够了。
 */
@Singleton
internal class AnimeDetailWriter @Inject constructor(
    private val animeDao: AnimeDao,
) {

    /** 写入一条详情；返回落库后的实体 id（调用方一般用不上，但便于串日志）。 */
    suspend fun save(detail: AnimeDetail) {
        val entity = detail.toEntity()
        val existing = animeDao.findById(entity.id)
        animeDao.upsertAll(
            listOf(
                if (existing != null) {
                    entity.copy(source = existing.source, homeRank = existing.homeRank)
                } else {
                    entity
                },
            ),
        )
    }
}
