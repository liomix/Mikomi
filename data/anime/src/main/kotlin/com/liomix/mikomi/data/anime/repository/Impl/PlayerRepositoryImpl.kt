package com.liomix.mikomi.data.anime.repository.Impl

import com.liomix.mikomi.data.anime.local.AnimeDetailCache
import com.liomix.mikomi.data.anime.processor.AnimeIdConverter
import com.liomix.mikomi.core.model.anime.AnimeSource
import com.liomix.mikomi.data.anime.remote.bangumi.BangumiSource
import com.liomix.mikomi.data.anime.repository.PlayerRepository
import javax.inject.Inject

/**
 * 播放页实现：一次「本地 → 远程 → 落库 → 再读本地」的取标题流程。
 *
 * 与详情页共用同一个 [AnimeDetailWriter]：落库规则（保留来源与首页位次）只有一份，
 * 两页各写一次迟早会不一致 —— 而那个不一致的表现是「点开过的番从首页消失」。
 */
internal class PlayerRepositoryImpl @Inject constructor(
    private val detailCache: AnimeDetailCache,
    private val source: BangumiSource,
    private val idConverter: AnimeIdConverter,
) : PlayerRepository {

    override suspend fun title(animeId: String): String? {
        detailCache.get(animeId)?.title?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }

        val bangumiId = resolveBangumiId(animeId) ?: return null
        // 失败就返回 null：播放页能继续播（只是番名显示「未知」），不该因为取名失败整页报错。
        return runCatching { source.detail(bangumiId) }
            .getOrNull()
            ?.also { detailCache.save(it) }
            ?.title
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    /** 领域 id → Bangumi 数字 id；跨源（AniList）走 [AnimeIdConverter]。 */
    private suspend fun resolveBangumiId(animeId: String): Int? {
        val parsed = idConverter.parse(animeId) ?: return null
        if (parsed.source == AnimeSource.BANGUMI) return parsed.raw.toIntOrNull()
        return idConverter.convert(parsed, AnimeSource.BANGUMI)?.raw?.toIntOrNull()
    }
}
