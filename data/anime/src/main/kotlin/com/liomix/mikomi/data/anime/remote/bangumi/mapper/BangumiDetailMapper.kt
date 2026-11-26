package com.liomix.mikomi.data.anime.remote.bangumi.mapper

import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.model.anime.AnimeCollection
import com.liomix.mikomi.core.model.anime.AnimeDetail
import com.liomix.mikomi.core.model.AnimeStatus
import com.liomix.mikomi.data.anime.mapper.BANGUMI_ID_PREFIX
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiDetailDto

/**
 * 详情 → [Anime]。
 *
 * 与基础映射的区别只有两处：`tags` 比 `meta_tags` 全（带计数、按热度排序），
 * 以及详情才有 `platform` / `eps`。这两个不进模型（[Anime] 没有对应字段）——
 * 需要它们的是详情页那个专门的 UI 状态，届时从 DTO 直接读，而不是先塞进通用模型。
 *
 * 不在这里做「有没有中文名」「封面是否为空」之类的兜底：那是展示层的决定，
 * 映射层猜错了反而更难修。
 */
internal fun BangumiDetailDto.toDomain(): Anime = Anime(
    id = "$BANGUMI_ID_PREFIX$id",
    title = displayName,
    coverUrl = images.bestCover(),
    synopsis = summary,
    rating = rating?.score,
    status = AnimeStatus.UNKNOWN,
    // 详情接口的 tags 带 count 且已按热度排序，比 metaTags 更适合当类型标签。
    genres = tags.map { it.name },
    airDate = date,
    rank = rating?.rank ?: 0,
    ratingCount = rating?.total ?: 0,
)

/**
 * 详情 → [AnimeDetail]。
 *
 * 与 [toDomain] 的区别是「够不够画一页」：这边带上集数、平台、收藏分布这些
 * 列表页用不到的字段。角色与职员由调用方另取后合并 —— 它们来自三个不同接口，
 * 映射层不该替调用方决定要不要等它们。
 */
internal fun BangumiDetailDto.toDetail(): AnimeDetail = AnimeDetail(
    id = "$BANGUMI_ID_PREFIX$id",
    title = displayName,
    coverUrl = images.bestCover(),
    synopsis = summary,
    score = rating?.score,
    rank = rating?.rank ?: 0,
    ratingCount = rating?.total ?: 0,
    date = date,
    platform = platform,
    episodes = eps,
    totalEpisodes = totalEpisodes,
    genres = tags.map { it.name },
    collection = AnimeCollection(
        wish = collection?.wish ?: 0,
        collect = collection?.collect ?: 0,
        doing = collection?.doing ?: 0,
        onHold = collection?.onHold ?: 0,
        dropped = collection?.dropped ?: 0,
    ),
)
