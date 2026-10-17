package com.liomix.mikomi.data.anime

/**
 * 跨数据源动漫 id 映射工具：在 Bangumi 与 AniList 之间通过动漫名互相转换 id。
 *
 * 转换以「名字」为桥梁：拿到源数据源的标题后，在目标数据源按名字搜索并精确匹配。
 * 匹配失败返回 null（不做猜测）。
 */
interface AnimeIdMapper {

    /** Bangumi id → AniList id。 */
    suspend fun bangumiToAniList(bangumiId: Int): Int?

    /** AniList id → Bangumi id。 */
    suspend fun aniListToBangumi(aniListId: Int): Int?
}
