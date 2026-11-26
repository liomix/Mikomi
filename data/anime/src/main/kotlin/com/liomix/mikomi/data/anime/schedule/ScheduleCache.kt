package com.liomix.mikomi.data.anime.schedule

import com.liomix.mikomi.core.model.ScheduleEntry
import com.liomix.mikomi.core.model.ScheduleProvenance
import kotlinx.serialization.Serializable

/**
 * 时间表缓存的整块内容：合并后的条目 + 这份结果的来源说明。
 *
 * 两者必须一起存：单独存条目的话，「这次哪些源挂了」只能再去 Room 里拼，而那份状态
 * 是**这次合并的快照**——刷新失败后 Room 里留下的是上一次的，页面就会显示出与数据
 * 不匹配的来源说明。
 *
 * 不在此列的东西（仍留在 Room）：分源版本号、冲突复核队列。它们要被查询、被逐条标记，
 * 不是「一份可以整块丢掉的快照」。
 */
@Serializable
data class ScheduleCache(
    val entries: List<ScheduleEntry> = emptyList(),
    val provenance: ScheduleProvenance = ScheduleProvenance(),
)
