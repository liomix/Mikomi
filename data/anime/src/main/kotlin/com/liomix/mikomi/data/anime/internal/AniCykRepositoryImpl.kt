package com.liomix.mikomi.data.anime.internal

import com.liomix.mikomi.data.anime.AniCykCalendar
import com.liomix.mikomi.data.anime.AniCykRepository
import com.liomix.mikomi.data.anime.remote.anicyk.AniCykSource
import com.liomix.mikomi.data.anime.remote.anicyk.CykScheduleDay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 次元库日历实现。
 *
 * 同一条请求路径、靠 `type` 区分线路（[AniCykCalendar.HQ] / [AniCykCalendar.SAKURA]），
 * 两条线路是不同片库而不是不同清晰度。解析失败按空数据返回（由合并层判定不可用），
 * 网络错误则向上抛出。
 */
@Singleton
internal class AniCykRepositoryImpl @Inject constructor(
    private val source: AniCykSource,
) : AniCykRepository {

    // 请求路径、参数、报文解析都在数据源里；这一层只负责把接口交给上层。
    override suspend fun fetchSchedule(calendar: AniCykCalendar): List<CykScheduleDay> =
        source.schedule(calendar)
}
