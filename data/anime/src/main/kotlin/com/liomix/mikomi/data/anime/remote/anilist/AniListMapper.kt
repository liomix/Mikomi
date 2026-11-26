package com.liomix.mikomi.data.anime.remote.anilist

import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.model.AnimeStatus
import com.liomix.mikomi.data.anime.remote.anilist.dto.AniListMediaDto
import com.liomix.mikomi.data.anime.remote.anilist.dto.AniListTitleDto

/**
 * AniList GraphQL 的 `Media` → [Anime]。
 *
 * 与 Bangumi 那份映射并列而不是共用：两边的字段名、缺失方式、状态取值都不同
 * （AniList 的 `FINISHED` 对应内部 `completed`，`description` 还是 HTML）。
 * 硬抽一个「通用条目映射」，每个字段都要写一层 when，还不如各写一份清楚。
 *
 * 不映射的东西：
 * - `idMal`（MyAnimeList id）：项目里没有第二条链路用它，留着只会让人以为能跳转；
 * - `bannerImage`：轮播用的是封面，横幅图尺寸不一致会裁得很难看。
 */
internal fun AniListMediaDto.toDomain(): Anime = Anime(
    // 前缀隔离命名空间：AniList 的 id 与 Bangumi 的数字 id 会撞。
    id = "$ANILIST_ID_PREFIX$id",
    title = title.displayTitle(),
    coverUrl = coverImage?.large ?: coverImage?.medium,
    // 简介是 HTML（含 `<br>`），不洗的话列表里会出现原始标签。
    synopsis = description?.let(::stripHtml),
    // AniList 的 averageScore 是 0~100，项目统一用 0~10。
    rating = averageScore?.let { it / 10.0 },
    status = AnimeStatus.from(status.toInternalStatus()),
    genres = genres,
    popularity = popularity ?: 0,
)

/** 主键前缀，与 Bangumi 等数据源的 id 空间隔离，避免落同一张表时碰撞。 */
internal const val ANILIST_ID_PREFIX = "anilist:"

/** AniList 评分是 0~100，项目统一用 0~10。 */
private const val ANILIST_SCORE_DIVISOR = 10.0

private val HTML_TAG_REGEX = Regex("<[^>]*>")
private val HTML_BR_REGEX = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)

/**
 * 标题回退顺序：native（中文/原名）→ english → romaji。
 *
 * 罗马音垫底：它永远是有的，但可读性最差 —— 一旦有个正经标题就不该用它。
 */
internal fun AniListTitleDto?.displayTitle(): String =
    this?.nativeTitle ?: this?.english ?: this?.romaji ?: ""

/**
 * AniList 简介是 HTML（`<i>`、`<br>`），洗成纯文本再给 UI。
 *
 * `<br>` 换成空格而不是删掉：直接删会让「第一行第二行」粘成一个词。
 */
internal fun stripHtml(html: String): String = html
    .replace(HTML_BR_REGEX, " ")
    .replace(HTML_TAG_REGEX, "")
    .trim()

/** AniList 枚举 → 内部状态字符串（对应 [com.liomix.mikomi.core.model.AnimeStatus] 的语义）。 */
internal fun String?.toInternalStatus(): String = when (this) {
    "RELEASING" -> "ongoing"
    "FINISHED" -> "completed"
    "NOT_YET_RELEASED" -> "upcoming"
    else -> "unknown"
}
