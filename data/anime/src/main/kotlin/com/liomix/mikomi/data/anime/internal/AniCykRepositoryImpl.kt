package com.liomix.mikomi.data.anime.internal

import com.liomix.mikomi.core.common.dispatcher.IoDispatcher
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
 * 只取「超清」源：它是 7 天视图，与 Mikomi 时间表的一周分组一致；「樱花」源是 11 天，
 * 会带来跨周噪声。解析失败按空数据返回（由合并层判定不可用），网络错误则向上抛出。
 */
@Singleton
internal class AniCykRepositoryImpl @Inject constructor(
    private val client: AniCykClient,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AniCykRepository {

    override suspend fun fetchSchedule(): List<CykScheduleDay> = withContext(ioDispatcher) {
        val body = client.get(path = SCHEDULE_PATH, params = mapOf("type" to SCHEDULE_SOURCE))
        AniCykParser.parseDays(body, json)
    }

    private companion object {
        const val SCHEDULE_PATH = "/mobile/schedule"
        const val SCHEDULE_SOURCE = "超清"
    }
}
