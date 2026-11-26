package com.liomix.mikomi.data.anime.remote.anicyk

import com.liomix.mikomi.core.common.dispatcher.IoDispatcher
import com.liomix.mikomi.data.anime.AniCykCalendar
import com.liomix.mikomi.data.anime.remote.anicyk.client.AniCykClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * AniCyk 数据源的**唯一出口**，与 `BangumiSource` / `AniListSource` 同构。
 *
 * 两个 source 的差别在于它能给什么：AniCyk 只提供「追番日历」这一份数据，
 * 而且返回的是**中间模型**（[CykScheduleDay]）而不是最终领域模型 —— 日历没有日期，
 * 必须先和 AniRpb 的明确日期做锚点校准，才能拼成 `ScheduleEntry`。
 * 那一步在 `ScheduleRepositoryImpl` 的合并层，这里不做。
 *
 * 不 try/catch：网络异常原样上抛，重试与降级由调用方（合并层）决定。
 */
@Singleton
internal class AniCykSource @Inject constructor(
    private val client: AniCykClient,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /** 拉取一周的追番日历（原样按报文顺序返回 7 天）。 */
    suspend fun schedule(calendar: AniCykCalendar): List<CykScheduleDay> =
        withContext(ioDispatcher) {
            val body = client.get(
                path = SCHEDULE_PATH,
                params = mapOf("type" to calendar.apiType),
            )
            AniCykMapper.parseDays(body, json)
        }

    private companion object {
        const val SCHEDULE_PATH = "/mobile/schedule"
    }
}
