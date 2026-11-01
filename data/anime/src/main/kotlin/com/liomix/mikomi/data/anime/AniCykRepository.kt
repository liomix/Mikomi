package com.liomix.mikomi.data.anime

import com.liomix.mikomi.data.anime.remote.anicyk.CykScheduleDay

/**
 * 次元库（AniCyk）数据源。
 *
 * 只暴露时间表需要的匿名只读能力：追番日历。接口失败向上抛出，由合并层标记该源不可用。
 */
internal interface AniCykRepository {

    /**
     * 拉取追番日历（`/mobile/schedule?type=<线路>`）。
     *
     * 报文按「天」返回、不带日期，[CykScheduleDay.index] 只是报文顺序；日期由合并层的
     * 锚定逻辑结合 AniRpb 的明确日期推导，这里不做猜测。
     */
    suspend fun fetchSchedule(calendar: AniCykCalendar = AniCykCalendar.HQ): List<CykScheduleDay>
}

/**
 * `/mobile/schedule` 的 `type` 参数：服务端按**线路（上游站点）**分组。
 *
 * 两条线路是两套不同的片库，不是同一份数据的两种清晰度：
 * - [HQ]（超清）：`www.lmm85.com`，7 天，章节文案带 `⏰HH:mm`；
 * - [SAKURA]（樱花）：`www.yinhuadm.cc`，11 天，章节文案只有集数、**没有任何时刻**。
 *
 * 实测两条线路详情 id 零重叠、标题交集约三成，同一部番的更新星期最多差 3 天，
 * 所以不能当成同源数据简单并集（会出重复条目）。时间表侧的混合策略见
 * `schedule/CykCalendarMixer`，本接口只保证「一次一条线路」的原子能力。
 */
internal enum class AniCykCalendar(
    /** 请求参数 `type` 的取值。 */
    val apiType: String,
    /** 报文天数。两条线路都从周一开始；[SAKURA] 第 7 天之后是下周数据，物化时截断。 */
    val dayCount: Int,
) {
    HQ("超清", 7),
    SAKURA("樱花", 11),
}
