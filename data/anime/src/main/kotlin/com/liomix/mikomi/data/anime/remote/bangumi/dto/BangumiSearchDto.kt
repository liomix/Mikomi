package com.liomix.mikomi.data.anime.remote.bangumi.dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable

/**
 * 搜索请求体（`POST /v0/search/subjects`）。
 *
 * 三个字段就是「搜什么、怎么排、筛什么」，所以 [filter] 不是可选参数而是默认值对象 ——
 * 调用方只想改关键词时不必知道过滤器的存在。
 */
@Serializable
data class BangumiSearchDto(
    val keyword: String,
    /** 排序：`rank`（站内排名）/ `match`（相关度）/ `heat`（热度）。 */
    val sort: String = "rank",
    val filter: BangumiSearchFilterDto = BangumiSearchFilterDto(),
)

/**
 * 搜索过滤。
 *
 * [type] 必须**始终编码**：默认值 `[2]`（动画）如果被省略，服务端会返回书籍、游戏、
 * 音乐 —— 榜单里混进一本小说就是这么来的。`@EncodeDefault(ALWAYS)` 就是为这一处。
 */
@Serializable
data class BangumiSearchFilterDto(
    /** 条目类型（2 = 动画）。 */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val type: List<Int> = listOf(2),
    /** 标签过滤（如产地标签「国产动画」「日本」）；空表示不过滤。 */
    val tag: List<String> = emptyList(),
    /** 排名过滤表达式（如 `">0"` 表示只要站内有排名的）；空表示不过滤。 */
    val rank: List<String> = emptyList(),
)

/**
 * 搜索响应。
 *
 * [total] 不是 `data.size`：服务端分页，总数决定了「还要不要继续加载」。
 */
@Serializable
data class BangumiSearchResponseDto(
    val data: List<BangumiDto> = emptyList(),
    val total: Int = 0,
)
