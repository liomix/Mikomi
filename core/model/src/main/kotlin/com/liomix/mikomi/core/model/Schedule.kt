package com.liomix.mikomi.core.model

/** 时间表条目：番剧展示信息与该集的放送时间。 */
data class ScheduleEntry(
    val anime: Anime,
    /** 放送时间（Unix 毫秒），所属星期由展示层按本地时区解释。 */
    val airingAtMillis: Long,
    /** 该条目对应的集数。 */
    val episode: Int,
    /** 服务端集数文案，例如 `更新至第4集`；为空时展示层回退到 [episode]。 */
    val episodeText: String = "",
    /** 服务端现成更新文案，例如 `4#周五00:58更`。 */
    val updateText: String = "",
    /** AniRpb 频道 id（用于后续详情/播放链路映射）。 */
    val channelId: String = "",
)
