package com.liomix.mikomi.data.anime.mapper

import com.liomix.mikomi.core.database.entity.AnimeEntity
import com.liomix.mikomi.core.database.entity.RankEntity
import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.model.AnimeStatus
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiSubjectDto
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiTrendItem

/** trending 条目：用趋势计数 [BangumiTrendItem.count] 作为热度排序依据。 */
internal fun BangumiTrendItem.toEntity(): AnimeEntity =
    subject.toEntity(popularity = count)

/** trending 条目 → 领域模型（搜索结果展示用，不落库）。 */
internal fun BangumiTrendItem.toDomain(): Anime = subject.toDomain(popularity = count)

/**
 * 搜索 / 趋势条目 → 领域模型。
 *
 * 直接用于搜索结果展示，不经 Room；简介同样只保留中文译文。
 */
internal fun BangumiSubjectDto.toDomain(popularity: Int? = null): Anime = Anime(
    id = "$BANGUMI_ID_PREFIX$id",
    title = displayName,
    coverUrl = images?.large ?: images?.common ?: images?.medium,
    synopsis = summary?.let(::extractChineseSummary),
    rating = rating?.score,
    status = AnimeStatus.UNKNOWN,
    genres = metaTags.filterNot { it in GENRE_NOISE }.take(GENRE_DISPLAY_LIMIT),
    popularity = popularity ?: rating?.total ?: 0,
    airDate = date,
    rank = rating?.rank ?: 0,
    ratingCount = rating?.total ?: 0,
)

internal fun BangumiSubjectDto.toEntity(popularity: Int? = null): AnimeEntity = AnimeEntity(
    id = "$BANGUMI_ID_PREFIX$id",
    title = displayName,
    coverUrl = images?.large ?: images?.common ?: images?.medium,
    synopsis = summary,
    rating = rating?.score,
    status = "unknown",
    popularity = popularity ?: (rating?.total ?: 0),
)

/**
 * 详情条目：带完整详情字段（上映日期 / 排行 / 评分人数 / 类型标签）。
 * 供详情页经 Bangumi `/v0/subjects/{id}` 拉取后回写。
 *
 * 简介仅保留中文译文：[summary] 字段常为「原文（多段）+ 空行 + 译文」的混合，
 * 用 [extractChineseSummary] 提取中文部分，避免原文译文一起显示。
 */
internal fun BangumiSubjectDto.toDetailEntity(): AnimeEntity = AnimeEntity(
    id = "$BANGUMI_ID_PREFIX$id",
    title = displayName,
    coverUrl = images?.large ?: images?.common ?: images?.medium,
    synopsis = summary?.let(::extractChineseSummary),
    rating = rating?.score,
    status = "unknown",
    popularity = rating?.total ?: 0,
    airDate = date.orEmpty(),
    rank = rating?.rank ?: 0,
    ratingCount = rating?.total ?: 0,
    genres = metaTags
        .filterNot { it in GENRE_NOISE }
        .joinToString(GENRES_SEPARATOR),
)

/**
 * 从 Bangumi 简介中提取中文译文。
 *
 * Bangumi 的 summary 可能为「日文原文 + 空行 + [简体中文]译文」格式：
 * 1. 先按空行 / `[简体中文]` / `中文:` 等标记切块；
 * 2. 优先取显式带「中文/简体/chinese/zh-cn」标记的块；
 * 3. 否则在所有块中挑中文字符占比最高者；
 * 4. 去掉 `[简体中文]` / `中文:` 前缀。
 *
 * 全中文（无原文）的简介原样返回；无中文时返回 null。
 */
internal fun extractChineseSummary(text: String): String? {
    val normalized = text.replace("\r\n", "\n").trim()
    if (normalized.isEmpty()) return null

    val blocks = normalized
        .split(CHINESE_BLOCK_SPLIT_REGEX)
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    val explicitChinese = blocks.filter { block ->
        val lower = block.lowercase()
        block.contains("中文") ||
            block.contains("简体") ||
            lower.contains("chinese") ||
            lower.contains("zh-cn")
    }

    val candidates = explicitChinese.ifEmpty { blocks }
    val best = candidates.maxByOrNull { chineseRatio(it) } ?: return null

    // 含任意汉字即返回（日语与中文共享汉字，无法严格区分；纯假名/拉丁才判无中文）。
    if (!best.any { it in '\u4e00'..'\u9fff' }) return null
    return best
        .replace(CHINESE_PREFIX_TAG_REGEX, "")
        .replace(CHINESE_PREFIX_COLON_REGEX, "")
        .trim()
        .takeIf { it.isNotEmpty() }
}

/** 榜单实体：[region] 为产地标签（cn / jp），类型标签取 meta_tags 去噪后展示。 */
internal fun BangumiSubjectDto.toRankEntity(region: String): RankEntity = RankEntity(
    id = "$BANGUMI_ID_PREFIX$id",
    region = region,
    title = displayName,
    coverUrl = images?.large ?: images?.common ?: images?.medium,
    synopsis = summary,
    rating = rating?.score,
    status = "unknown",
    popularity = rating?.total ?: 0,
    genres = metaTags
        .filterNot { it in GENRE_NOISE }
        .take(GENRE_DISPLAY_LIMIT)
        .joinToString(GENRES_SEPARATOR),
)

/** 标准标签中的产地/媒介噪音（榜单已按产地分列，无需重复展示）。 */
private val GENRE_NOISE = setOf(
    "日本", "中国", "国产", "国产动画", "中国动画", "日漫", "国漫",
    "TV", "OVA", "OAD", "剧场版", "WEB", "真人版",
)

private const val GENRE_DISPLAY_LIMIT = 2

/** 中文字符占比（0~1）：去掉空白后的可见字符中，汉字所占比例。 */
private fun chineseRatio(text: String): Float {
    val visible = text.replace(WHITESPACE_REGEX, "")
    if (visible.isEmpty()) return 0f
    val chineseCount = CJK_CHAR_REGEX.findAll(visible).count()
    return chineseCount.toFloat() / visible.length
}

/** 简介切块分隔：空行，或中文标记起始。 */
private val CHINESE_BLOCK_SPLIT_REGEX = Regex("""\n{2,}|(?=\[简体中文\])|(?=\[中文\])|(?=简体中文[:：])|(?=中文[:：])""")

/** 块首的 `[简体中文]` / `[中文]` / `[zh-cn]` / `[chinese]` 标记。 */
private val CHINESE_PREFIX_TAG_REGEX = Regex("""^\[(简体中文|中文|zh-cn|chinese)]\s*""", RegexOption.IGNORE_CASE)

/** 块首的 `简体中文:` / `中文:` / `zh-cn:` / `chinese:` 标记。 */
private val CHINESE_PREFIX_COLON_REGEX = Regex("""^(简体中文|中文|zh-cn|chinese)[:：]\s*""", RegexOption.IGNORE_CASE)

/** 中文字符（CJK 统一表意文字）。 */
private val CJK_CHAR_REGEX = Regex("""[\u4e00-\u9fff]""")

/** 空白字符。 */
private val WHITESPACE_REGEX = Regex("""\s+""")

/** 主键前缀，与 AniList 等数据源的 id 空间隔离，避免落同一张表时碰撞。 */
internal const val BANGUMI_ID_PREFIX = "bangumi:"
