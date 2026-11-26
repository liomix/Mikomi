package com.liomix.mikomi.data.anime.processor

import com.liomix.mikomi.data.anime.remote.anilist.AniListSource
import com.liomix.mikomi.data.anime.remote.bangumi.BangumiSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 番剧 id 的**领域表示**：哪个源 + 该源自己的编号。
 *
 * ## 为什么需要它
 *
 * 四个数据源各有一套编号，拼进同一个字段时必须带前缀（`bangumi:123` / `anirpb:31818...`）。
 * 但字符串前缀有个问题：**它没有类型**。任何函数都能收一个 `String` 并假装它是 id，
 * 于是「把 AniRpb 的视频 id 传给 Bangumi 详情接口」这种错，编译器一句话都不说，
 * 运行时才发现查出来的是不相干的番。
 *
 * [AnimeId] 把「源」和「编号」拆成两个字段：要传给哪个接口，看 [source] 就够，
 * 不用去解字符串。
 */
data class AnimeId(
    val source: AnimeSource,
    val raw: String,
) {
    /** 落库 / 存缓存的字符串形式（`源前缀 + 编号`）。 */
    val value: String
        get() = source.prefix + raw

    override fun toString(): String = value

    companion object {
        /** 解析字符串 id；不认识的前缀返回 null（不猜，猜错比返回 null 更难查）。 */
        fun parse(value: String): AnimeId? {
            val source = AnimeSource.entries.firstOrNull { value.startsWith(it.prefix) } ?: return null
            val raw = value.removePrefix(source.prefix)
            return raw.takeIf { it.isNotBlank() }?.let { AnimeId(source, it) }
        }

        /** 构造；编号为空时返回 null。 */
        fun of(source: AnimeSource, raw: String): AnimeId? =
            raw.trim().takeIf { it.isNotEmpty() }?.let { AnimeId(source, it) }
    }
}

/**
 * 数据源。
 *
 * [prefix] 是 id 前缀的**唯一**来源：这些字符串曾经散在 `mapper/IdPrefix`、各 Repository
 * 的 companion 里，加一个源要翻四五个文件。
 */
enum class AnimeSource(
    val prefix: String,
    val displayName: String,
) {
    BANGUMI("bangumi:", "Bangumi"),
    ANILIST("anilist:", "AniList"),

    /** AniRpb 的 id 是长串视频编号，与数字 id 不在同一命名空间。 */
    ANIRPB("anirpb:", "AniRpb"),

    /** AniCyk 的详情 id 取自详情页地址的尾段。 */
    ANICYK("anicyk:", "AniCyk"),
}

/**
 * 跨源 id 转换。
 *
 * ## 能力边界
 *
 * 转换不是查表：各源之间没有公共编号，只能**靠标题反查** —— 拿源 A 的 id 问出标题，
 * 再去源 B 搜这个标题。所以能不能转，取决于目标源有没有「按标题搜索」：
 *
 * | 目标 | 可转 | 原因 |
 * |---|---|---|
 * | Bangumi | 是 | v0 有 `search/subjects` |
 * | AniList | 是 | GraphQL 有 `Page.media(search:)` |
 * | AniRpb | 否 | 只有分类榜与日历，没有检索接口 |
 * | AniCyk | 否 | 只有追番日历，没有检索接口 |
 *
 * 不可转时返回 `null`，而不是抛错或返回原 id：调用方需要区分「转不过去」与「本来就是同一个」。
 */
interface AnimeIdConverter {

    /** 解析字符串 id，不联网。 */
    fun parse(value: String): AnimeId?

    /**
     * 同一部番在 [target] 源的编号。
     *
     * 同源直接返回（不联网）；跨源按标题反查，查不到返回 `null`。
     */
    suspend fun convert(id: AnimeId, target: AnimeSource): AnimeId?
}

/**
 * 实现：**唯一的跨源桥**。
 *
 * 它替代了原来的 `AnimeIdMapper`（`bangumiToAniList` / `aniListToBangumi` 两个方向写死）。
 * 现在加一个源只需要改 [AnimeSource] 与这里的 `when`，不必再加一个新方法。
 *
 * 标题匹配用「归一化后相等」而不是模糊匹配：Bangumi 的搜索本身就会返回近似结果，
 * 我们再松一层，很容易把「第二季」匹配到「第一季」—— 那种错用户看得出，代码看不出。
 */
@Singleton
internal class AnimeIdConverterImpl @Inject constructor(
    private val bangumi: BangumiSource,
    private val aniList: AniListSource,
) : AnimeIdConverter {

    override fun parse(value: String): AnimeId? = AnimeId.parse(value)

    override suspend fun convert(id: AnimeId, target: AnimeSource): AnimeId? {
        // 同源不需要转换。返回同一个对象而不是新建，语义上就是「它本身」。
        if (id.source == target) return id

        val title = titleOf(id) ?: return null
        return when (target) {
            AnimeSource.BANGUMI -> findBangumiId(title)
            AnimeSource.ANILIST -> findAniListId(title)
            // 没有检索接口的源无法从外部定位：这类 id 只能由该源自己的列表带出来。
            AnimeSource.ANIRPB, AnimeSource.ANICYK -> null
        }
    }

    /** 取标题：跨源定位的唯一依据。源不支持按 id 单查时返回 null。 */
    private suspend fun titleOf(id: AnimeId): String? = when (id.source) {
        AnimeSource.BANGUMI -> {
            val numeric = id.raw.toIntOrNull() ?: return null
            bangumi.detail(numeric).title.takeIf { it.isNotBlank() }
        }

        AnimeSource.ANILIST -> {
            val numeric = id.raw.toIntOrNull() ?: return null
            aniList.titleById(numeric)
        }

        // AniRpb 的条目只在榜单里出现，按 id 单查没有接口；AniCyk 同理。
        AnimeSource.ANIRPB, AnimeSource.ANICYK -> null
    }

    /** 在 Bangumi 搜标题，取第一条「归一化后相等」的结果。 */
    private suspend fun findBangumiId(title: String): AnimeId? {
        val target = normalize(title)
        val hits = bangumi.search(keyword = title, sort = SORT_MATCH, limit = SEARCH_LIMIT)
        val hit = hits.items.firstOrNull { normalize(it.title) == target } ?: return null
        return AnimeId.of(AnimeSource.BANGUMI, hit.id.removePrefix(AnimeSource.BANGUMI.prefix))
    }

    /** 在 AniList 搜标题，同样只认「归一化后相等」。 */
    private suspend fun findAniListId(title: String): AnimeId? {
        val target = normalize(title)
        val hit = aniList.searchByTitle(title)
            .firstOrNull { normalize(it.title) == target } ?: return null
        return AnimeId.of(AnimeSource.ANILIST, hit.id.removePrefix(AnimeSource.ANILIST.prefix))
    }

    /**
     * 标题归一化：去空白、去常见标点、忽略大小写。
     *
     * 不做「去括号内容」「繁简转换」这类更聪明的处理 —— 它们会让「第二季」和「第一季」
     * 变成同一个标题，而这两者恰恰是最容易误匹配的一对。
     */
    private fun normalize(title: String): String = title
        .lowercase()
        .filterNot { it.isWhitespace() || it in PUNCTUATION }

    private companion object {
        /** 搜索取前 10 条：跨源定位只需要「最像的那一个」。 */
        const val SEARCH_LIMIT = 10
        const val SORT_MATCH = "match"

        /** 只去掉明确无意义的符号，保留括号与数字（它们区分季数、剧场版）。 */
        val PUNCTUATION = setOf('·', '・', '。', '，', ',', '.', '!', '！', '?', '？', ':', '：')
    }
}
