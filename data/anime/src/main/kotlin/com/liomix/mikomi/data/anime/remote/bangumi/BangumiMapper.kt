package com.liomix.mikomi.data.anime.remote.bangumi

import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.model.AnimeStatus
import com.liomix.mikomi.core.model.anime.AnimeCollection
import com.liomix.mikomi.core.model.anime.AnimeComment
import com.liomix.mikomi.core.model.anime.AnimeDetail
import com.liomix.mikomi.core.model.anime.CreatorComment
import com.liomix.mikomi.core.model.anime.CreatorDetail
import com.liomix.mikomi.core.model.anime.DetailCharacter
import com.liomix.mikomi.core.model.anime.DetailStaff
import com.liomix.mikomi.core.model.anime.DetailVoiceActor
import com.liomix.mikomi.core.model.anime.AnimeSource
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiAnimeCommentDto
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiCreatorCharacterDto
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiCreatorCommentDto
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiCreatorDto
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiCreatorKind
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiCreatorPersonDto
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiDetailDto
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiDto
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiHomeBannerDto
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiHomeContentDto
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiImagesDto
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiSearchResponseDto

/**
 * Bangumi 的 DTO → 领域模型，**全部映射集中在这一处**。
 *
 * 为什么不分文件：这些函数共享同一套判断（封面取哪一档、中文名怎么取、id 加什么前缀、
 * 空值怎么兜）。分散在七个文件里时，改一处约定要翻七遍，而且很容易出现「首页取 large、
 * 搜索取 common」这种没人说得清的差异。这里是数据源与上层之间唯一的翻译层，
 * 一旦有映射问题，只需要看这一个文件。
 *
 * 分区顺序：基础 → 列表（搜索 / 首页）→ 详情 → 主创 → 评论。
 */

/* ------------------------------------------------------------------ 基础 */

/**
 * 基础条目 → [Anime]。首页、搜索、榜单都走这一条。
 *
 * 不映射的东西：
 * - `type`：项目只做动画，这个字段恒为 2（过滤在 [BangumiSource] 里做）；
 * - `nsfw` / `collection`：只在详情页有意义，见 [BangumiDetailDto.toDetail]。
 */
internal fun BangumiDto.toDomain(): Anime = Anime(
    // 前缀把 Bangumi 的数字 id 和 AniRpb 的视频 id 隔开：两者都叫「id」，但不同命名空间。
    id = AnimeSource.BANGUMI.prefix + id,
    title = displayName,
    coverUrl = images.bestCover(),
    synopsis = summary?.let(::extractChineseSummary),
    rating = rating?.score,
    // 条目接口不给连载状态：v0 的 subject 没有这个字段，`date` 只能推首播时间。
    // 猜一个 ONGOING 会让已完结的番显示成「连载中」，所以宁可留 UNKNOWN。
    status = AnimeStatus.UNKNOWN,
    genres = metaTags,
    // 评分人数当热度：Bangumi 没有真实的「热度」字段，而首页的排序需要一个可比的数。
    popularity = rating?.total ?: 0,
    airDate = date,
    rank = rating?.rank ?: 0,
    ratingCount = rating?.total ?: 0,
)

/**
 * 取一张能用的封面。
 *
 * 顺序按「屏幕够用就好」排：`large` 是原图，列表里用不上；`common` 是最常用的一档。
 * 全为空返回 `null` 而不是空串 —— 空串会被 Coil 当成一个待加载的 url。
 */
internal fun BangumiImagesDto?.bestCover(): String? =
    this?.large?.takeIf { it.isNotBlank() }
        ?: this?.common?.takeIf { it.isNotBlank() }
        ?: this?.medium?.takeIf { it.isNotBlank() }
        ?: this?.small?.takeIf { it.isNotBlank() }

/* ------------------------------------------------------------ 列表：搜索 / 首页 */

/**
 * 搜索响应 → 条目列表。
 *
 * 请求侧的 `BangumiSearchDto` 不需要映射：它是**发出去的东西**，字段就是服务端要的样子。
 * 分页信息（`total`）也不在这里处理 —— 映射层只回答「这页里有哪些条目」。
 */
internal fun BangumiSearchResponseDto.toDomains(): List<Anime> = data.map { it.toDomain() }

/**
 * 轮播项 → [Anime]。
 *
 * 只取 `subject`：`count` 是「这张图为什么排在前面」的依据，排序完就没用了，
 * 塞进 [Anime] 会让同一个领域模型在不同页面有不同含义。
 */
internal fun BangumiHomeBannerDto.toDomain(): Anime = subject.toDomain()

/**
 * 内容区 → 条目列表。
 *
 * 分区标题不进模型：它是**这一个分区**的属性，不是每张卡片的属性。标题由调用方
 * 连同 `id`（列表 key）一起从 DTO 取。
 */
internal fun BangumiHomeContentDto.toDomains(): List<Anime> = items.map { it.toDomain() }

/* ---------------------------------------------------------------- 详情 */

/**
 * 详情 → [Anime]（列表形态）。
 *
 * 与基础映射的差别：`tags` 比 `meta_tags` 全（带计数、已按热度排序），更适合当类型标签。
 */
internal fun BangumiDetailDto.toDomain(): Anime = Anime(
    id = AnimeSource.BANGUMI.prefix + id,
    title = displayName,
    coverUrl = images.bestCover(),
    synopsis = summary,
    rating = rating?.score,
    status = AnimeStatus.UNKNOWN,
    genres = tags.map { it.name },
    airDate = date,
    rank = rating?.rank ?: 0,
    ratingCount = rating?.total ?: 0,
)

/**
 * 详情 → [AnimeDetail]，够画一整页。
 *
 * 多出来的是集数、平台、收藏分布 —— 列表页用不到的字段。角色与职员不在这里合并：
 * 它们来自另外两个接口，要不要一起等由调用方决定。
 */
internal fun BangumiDetailDto.toDetail(): AnimeDetail = AnimeDetail(
    id = AnimeSource.BANGUMI.prefix + id,
    title = displayName,
    coverUrl = images.bestCover(),
    synopsis = summary?.let(::extractChineseSummary),
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

/* ---------------------------------------------------------------- 主创 */

/**
 * 主创 → 详情页的角色行。`kind` 决定走哪条：角色带配音演员，职员带职位。
 *
 * 返回可空、由调用方按 kind 各取一条，而不是合并成一个函数 —— 合并后「关系」与「职位」
 * 会抢同一个字段，而它们在页面上的位置本来就不同。
 */
internal fun BangumiCreatorDto.toDetailCharacter(): DetailCharacter? {
    if (kind != BangumiCreatorKind.Character) return null
    return DetailCharacter(
        id = id,
        name = name,
        relation = relation.orEmpty(),
        imageUrl = images?.large ?: images?.medium,
        voiceActors = actors.map {
            DetailVoiceActor(id = it.id, name = it.name, imageUrl = it.images?.grid)
        },
    )
}

/** 主创 → 详情页的职员行。职位只取第一个：那一行放不下「原作 / 导演 / 分镜」。 */
internal fun BangumiCreatorDto.toDetailStaff(): DetailStaff? {
    if (kind != BangumiCreatorKind.Staff) return null
    val position = positions.firstOrNull()?.type
        ?.let { it.cn?.takeIf(String::isNotBlank) ?: it.name }
        .orEmpty()
    return DetailStaff(
        id = id,
        name = name,
        localizedName = displayName.takeIf { it != name },
        position = position,
        imageUrl = images?.large ?: images?.medium,
    )
}

/** 角色详情 → 主创详情页。 */
internal fun BangumiCreatorCharacterDto.toCreatorDetail(): CreatorDetail = CreatorDetail(
    id = id,
    name = name,
    localizedName = displayName.takeIf { it != name },
    imageUrl = images?.large ?: images?.medium,
    info = info,
    summary = summary,
)

/** 人物详情 → 主创详情页。信息框是结构化数据，这里只取大图与简介。 */
internal fun BangumiCreatorPersonDto.toCreatorDetail(): CreatorDetail = CreatorDetail(
    id = id,
    name = name,
    localizedName = displayName.takeIf { it != name },
    imageUrl = images?.large ?: images?.medium,
    summary = summary,
)

/* ---------------------------------------------------------------- 评论 */

/**
 * 番剧评论 → [AnimeComment]。
 *
 * `score` 的 0 表示「没打分」而不是「0 分」—— Bangumi 的有效评分是 1~10，
 * 改写成 null 会让「没打分」和「未知」混为一谈。
 */
internal fun BangumiAnimeCommentDto.toAnimeComment(): AnimeComment? {
    val author = user ?: return null
    return AnimeComment(
        id = id,
        user = AnimeComment.User(
            id = author.id,
            name = author.nickname?.takeIf(String::isNotBlank) ?: author.username.orEmpty(),
            // 头像用 medium：评论区一行只有 32dp，large 是白白多下几十 KB。
            avatarUrl = author.avatar?.medium ?: author.avatar?.large,
        ),
        score = rate,
        // 空串而不是 null：模型里 `content` 非空，页面直接渲染，不必再判空。
        content = comment.orEmpty(),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

/**
 * 主创评论 → [CreatorComment]，递归处理楼中楼。
 *
 * 作者名昵称优先：用户名是登录名（可能是拼音+数字），昵称才是他自己起的。
 */
internal fun BangumiCreatorCommentDto.toCreatorComment(): CreatorComment? {
    val author = user ?: return null
    return CreatorComment(
        id = id,
        user = CreatorComment.User(
            id = author.id,
            name = author.nickname?.takeIf(String::isNotBlank) ?: author.username.orEmpty(),
            avatarUrl = author.avatar?.medium ?: author.avatar?.large,
        ),
        content = content.orEmpty(),
        createdAt = createdAt,
        replies = replies.mapNotNull { it.toCreatorComment() },
    )
}

/**
 * 从混合简介里取中文段落。
 *
 * Bangumi 的 `summary` 常见形态是「日文原文 + 空行 + `[简体中文]` 译文」。整段直接显示，
 * 用户先看到一大段日文，要滑一会儿才到中文 —— 所以这里优先取标记之后的内容。
 *
 * 没有标记时按「有没有汉字」判断：
 * - 有汉字（日文也含汉字）→ 原样返回。宁可能夹着日文，也不要显示空；
 * - 纯假名 → 返回 `null`，让页面走「暂无简介」而不是显示一堆看不懂的音节。
 */
internal fun extractChineseSummary(text: String): String? {
    val afterMarker = text.substringAfter(CHINESE_MARKER, "").trim()
    if (afterMarker.isNotEmpty()) return stripLanguagePrefix(afterMarker)

    // 只有「纯假名」才判为没有可读内容：日文含汉字要原样显示，纯英文简介也是有效内容。
    val hasHan = text.any { it in HAN_RANGE }
    val hasKana = text.any { it in KANA_RANGE }
    if (!hasHan && hasKana) return null

    return stripLanguagePrefix(text.trim()).takeIf { it.isNotEmpty() }
}

/** 去掉开头的「中文：」「简体中文:」这类语言前缀。 */
private fun stripLanguagePrefix(text: String): String =
    text.replace(LANGUAGE_PREFIX, "").trim()

/** 译文标记：Bangumi 用方括号包住语言名。 */
private const val CHINESE_MARKER = "[简体中文]"

/** 摘要开头偶尔写着「中文：」，去掉更干净。 */
private val LANGUAGE_PREFIX = Regex("^(简体中文|中文)\\s*[：:]\\s*")

/** 平假名 + 片假名：用来识别「纯假名」这种没有可读内容的情况。 */
private val KANA_RANGE = '\u3040'..'\u30ff'

/** CJK 统一表意文字区间：用它判断「有没有汉字」，比正则快且够用。 */
private val HAN_RANGE = '\u4e00'..'\u9fff'
