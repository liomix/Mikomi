package com.liomix.mikomi.core.model

/**
 * AniRpb 更新表条目。
 *
 * 与通用 [ScheduleEntry] 分开：AniRpb 返回的是更新文案和集数文本，时间表落库时
 * 再转换成展示所需的统一模型。
 */
data class AniRpbScheduleItem(
    val videoId: String,
    val title: String,
    val coverUrl: String?,
    val synopsis: String?,
    /** 该条目所属日期（yyyy-MM-dd）。 */
    val playDate: String,
    /** 所属时段的本地时间标签，例如 `00:58`。 */
    val updateTime: String,
    /** 服务端集数文案，例如 `更新至第4集`。 */
    val episodeText: String,
    /** 从集数文案解析出的数字；无法解析时为 0。 */
    val episode: Int,
    /** 服务端现成更新文案，例如 `4#周五00:58更`。 */
    val updateText: String,
    /** AniRpb 频道 id。 */
    val channelId: String,
)
