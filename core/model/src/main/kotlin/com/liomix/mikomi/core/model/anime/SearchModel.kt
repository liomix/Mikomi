package com.liomix.mikomi.core.model.anime

import com.liomix.mikomi.core.model.Anime
import kotlinx.serialization.Serializable

/**
 * 搜索页要用到的三类数据。
 *
 * 放在同一个文件里，是因为它们属于同一个页面；但**各自是独立类型**，不合成一个大类 ——
 * 热播榜有名次与播放量，搜索结果没有；建议可能只是一个词（没有对应的番剧）。
 * 塞进一个类的话，每个使用方都要忽略掉一半字段，而「哪些字段在场」本身就说明了
 * 「这是哪一类数据」。
 */

/**
 * 一次搜索的结果页。
 *
 * 两个值都要：`items` 给页面渲染，`rawCount` 给分页游标。
 *
 * 为什么不是直接用 `items.size`：服务端一页返回 24 条，其中可能有几条是书籍/游戏被
 * 过滤掉了。按过滤后的数量推进 `offset`，会反复请求同一段区间 —— 表现是「一直加载
 * 不出新内容」。
 */
data class Search(
    val items: List<Anime> = emptyList(),
    /** 服务端本页返回的原始条数（过滤前）。 */
    val rawCount: Int = 0,
)

/**
 * 热播榜的一项。
 *
 * 与 [Search.items] 里的 [Anime] 不是一个东西：这里带 [rank]（榜单名次）与 [playCount]
 * （播放量），而它们是**榜单的属性**，不是番剧的属性 —— 同一部番在日榜和周榜的名次不同，
 * 放进 [Anime] 就会出现「这个字段代表哪个榜」的问题。
 *
 * [id] 是数据源自己的视频 id，与 Bangumi 的条目 id 不在同一命名空间，不能拿去查详情。
 */
@Serializable
data class HotRanking(
    /** 数据源内的视频 id。 */
    val id: String,
    /** 名次，从 1 开始。由数据层按列表位置赋值，不来自报文。 */
    val rank: Int = 0,
    val title: String,
    val coverUrl: String? = null,
    /** 服务端现成的更新文案，例如 `7#周五16:00更`。 */
    val updateText: String = "",
    /** 当前已更新集数。 */
    val currentEpisodes: Int = 0,
    /** 评分（0~10，未知为 null）。 */
    val score: Double? = null,
    /** 播放量（未知为 0）。 */
    val playCount: Long = 0,
    /** 标签（年份 / 类型 / 关键词）。为空时界面整行不出现，不用占位文本假装有数据。 */
    val tags: List<String> = emptyList(),
)

/**
 * 一条搜索建议。
 *
 * [anime] 可空是刻意的：建议既可能是「一条你正在找的番」（有封面、有年份），
 * 也可能只是一个词（用户没打完，或者服务端只回了关键词）。用 `anime == null` 表示后者，
 * 页面据此决定显示卡片行还是纯文本行。
 */
data class SearchSuggestion(
    val text: String,
    /** 对应的番剧；为 null 时这条建议只有词、没有具体条目。 */
    val anime: Anime? = null,
)
