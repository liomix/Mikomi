package com.liomix.mikomi.core.model

/** 时间表条目：番剧展示信息与该集的放送时间。 */
data class ScheduleEntry(
    val anime: Anime,
    /** 放送时间（Unix 毫秒），所属星期由展示层按本地时区解释。 */
    val airingAtMillis: Long,
    /** 该条目对应的集数。 */
    val episode: Int,
)
