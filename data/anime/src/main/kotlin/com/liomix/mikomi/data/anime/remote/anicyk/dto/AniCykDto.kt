package com.liomix.mikomi.data.anime.remote.anicyk.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 追番日历报文的一项（`/mobile/schedule`）。
 *
 * 外层是「天」的数组、内层是该天更新的条目；**服务端不返回日期**，只有顺序 ——
 * 日期由 `ScheduleDateAnchor` 结合 AniRpb 的明确日期校准，所以这里没有 date 字段。
 */
@Serializable
internal data class CykScheduleItemDto(
    val title: String = "",
    val cover: String = "",
    /** 章节文案，形如 `更新至32话   ⏰09:00`；部分条目不包含时刻。 */
    val chapter: String = "",
    /** 详情页地址，形如 `https://www.lmm85.com/detail/8220.html`。 */
    val link: String = "",
    @SerialName("isNew") val isNew: Boolean = false,
)
