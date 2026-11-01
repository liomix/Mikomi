package com.liomix.mikomi.data.anime

import com.liomix.mikomi.data.anime.remote.anicyk.CykScheduleDay

/**
 * 次元库（AniCyk）数据源。
 *
 * 只暴露时间表需要的匿名只读能力：追番日历。接口失败向上抛出，由合并层标记该源不可用。
 */
internal interface AniCykRepository {

    /**
     * 拉取追番日历（`/mobile/schedule?type=超清`）。
     *
     * 报文按「天」返回、不带日期，[CykScheduleDay.index] 只是报文顺序；日期由合并层
     * 的锚定逻辑结合 AniRpb 的明确日期推导，这里不做猜测。
     */
    suspend fun fetchSchedule(): List<CykScheduleDay>
}
