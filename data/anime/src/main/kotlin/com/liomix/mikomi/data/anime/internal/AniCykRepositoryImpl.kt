package com.liomix.mikomi.data.anime.internal

import com.liomix.mikomi.core.common.dispatcher.IoDispatcher
import com.liomix.mikomi.data.anime.AniCykCalendar
import com.liomix.mikomi.data.anime.AniCykRepository
import com.liomix.mikomi.data.anime.remote.anicyk.AniCykClient
import com.liomix.mikomi.data.anime.remote.anicyk.AniCykParser
import com.liomix.mikomi.data.anime.remote.anicyk.CykScheduleDay
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * 次元库日历实现。
 *
 * 同一条请求路径、靠 `type` 区分线路（[AniCykCalendar.HQ] / [AniCykCalendar.SAKURA]），
 * 两条线路是不同片库而不是不同清晰度。解析失败按空数据返回（由合并层判定不可用），
 * 网络错误则向上抛出。
 */
@Singleton
internal class AniCykRepositoryImpl @Inject constructor(
    private val client: AniCykClient,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AniCykRepository {

    override suspend fun fetchSchedule(calendar: AniCykCalendar): List<CykScheduleDay> =
        withContext(ioDispatcher) {
            val body = client.get(
                path = SCHEDULE_PATH,
                params = mapOf("type" to calendar.apiType),
            )
            AniCykParser.parseDays(body, json)
        }

    private companion object {
        const val SCHEDULE_PATH = "/mobile/schedule"
    }
}
