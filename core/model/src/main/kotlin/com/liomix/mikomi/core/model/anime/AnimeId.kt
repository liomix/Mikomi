package com.liomix.mikomi.core.model.anime

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
