package com.liomix.mikomi.data.anime

import com.liomix.mikomi.core.model.ScheduleEntry
import com.liomix.mikomi.core.model.ScheduleProvenance
import kotlinx.coroutines.flow.Flow

/**
 * 放送时间表数据门面。
 *
 * 多源（AniCyk 优先、AniRpb 兜底）并行拉取后合并去重，Room 为单一可信来源：
 * UI 只观察本地数据，[refreshSchedule] 负责把远端结果合并落库。
 */
interface ScheduleRepository {

    /** 观察当前周时间表（按放送时间升序的本地数据）。 */
    fun observeSchedule(): Flow<List<ScheduleEntry>>

    /** 观察数据来源：base 源、参与合并的源、本次不可用的源。 */
    fun observeProvenance(): Flow<ScheduleProvenance>

    /**
     * 并行拉取所有源、合并去重后回写本地。
     *
     * 全部源失败时向上抛出（保留上一次结果）；全部源为空时清空本周数据。
     */
    suspend fun refreshSchedule()
}
