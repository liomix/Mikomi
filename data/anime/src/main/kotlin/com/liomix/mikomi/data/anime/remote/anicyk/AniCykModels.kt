package com.liomix.mikomi.data.anime.remote.anicyk

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 追番日历报文：外层是「天」的数组，内层是该天更新的条目。
 *
 * 服务端不返回日期（只有顺序），日期由 `ScheduleDateAnchor` 结合 AniRpb 的明确日期校准。
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

/** 单日日历条目（已解析出时刻/集数/详情 id）。 */
internal data class CykScheduleItem(
    val detailId: String,
    val title: String,
    val coverUrl: String?,
    /** 章节文案原文，例如 `更新至32话`。 */
    val chapterText: String,
    /** 章节文案里解析出的时刻 `HH:mm`，没有则为 null。 */
    val airTime: String?,
    /** 从章节文案解析出的集数；无法解析时为 0。 */
    val episode: Int,
    val detailLink: String,
)

/** 一天的日历条目；[index] 从 0 开始，与报文顺序一致。 */
internal data class CykScheduleDay(
    val index: Int,
    val items: List<CykScheduleItem>,
)
