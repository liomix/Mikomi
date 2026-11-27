package com.liomix.mikomi.core.model.anime

/**
 * 时间表里的一天：日期、当天的条目、以及它是不是今天。
 *
 * 早先是 `days: List<List<ScheduleEntry>>` + `dates: List<String>` 两个平行列表，
 * 靠下标对齐（`days[page]` / `dates[index]`）。那种形状的错误不报、不崩：
 * 少一天、乱序、多一天，界面只会把周三的番安静地显示在周四的标签下面。
 * 合成一个类型，错位就写不出来。
 *
 * [isToday] 也在这里而不是在界面上算：它和 [date] 必须来自同一刻的时钟。
 * 分两处算（一处生成日期、一处在 composable 里读 `Calendar`），跨零点那一秒页面
 * 就会出现「『今』在这一格，日期却还是昨天的周」。
 */
data class ScheduleDay(
    /** `yyyy-MM-dd`，本地时区。 */
    val date: String,
    val entries: List<ScheduleEntry> = emptyList(),
    val isToday: Boolean = false,
)
