package com.liomix.mikomi.data.anime

import com.liomix.mikomi.core.model.ScheduleEntry
import kotlinx.coroutines.flow.Flow

/** 放送时间表数据门面：AniRpb 每周更新表，Room 为单一可信来源。 */
interface ScheduleRepository {

    /** 观察当前周时间表（按放送时间升序的本地数据）。 */
    fun observeSchedule(): Flow<List<ScheduleEntry>>

    /** 从 AniRpb 拉取本周更新表并回写本地；失败向上抛出。 */
    suspend fun refreshSchedule()
}
