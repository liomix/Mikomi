package com.liomix.mikomi.core.model

/** 时间表条目：番剧展示信息与该集的放送时间。 */
data class ScheduleEntry(
    val anime: Anime,
    /** 三态放送时间（精确 / 仅日期 / 未知），合并与展示以它为准。 */
    val airTime: AirTime,
    /** 放送时间（Unix 毫秒），所属星期由展示层按本地时区解释。 */
    val airingAtMillis: Long,
    /** 该条目对应的集数。 */
    val episode: Int,
    /** 服务端集数文案，例如 `更新至第4集`；为空时展示层回退到 [episode]。 */
    val episodeText: String = "",
    /** 服务端现成更新文案，例如 `4#周五00:58更`。 */
    val updateText: String = "",
    /** AniRpb 频道 id（用于后续详情/播放链路映射）。 */
    val channelId: String = "",
    /** 详情页地址（AniCyk 提供），为空表示该源没有详情链接。 */
    val detailLink: String = "",
    /** 跨源判重后的 canonical 身份键，同时作为列表 key 的一部分。 */
    val canonicalKey: String = "",
    /** 该条目最终采用的源 id（合并时即 base 源）。 */
    val sourceId: String = "",
)

/**
 * 单个数据源在一次刷新后的可用状态。
 *
 * [version] 是该源缓存数据的版本号，每次成功抓取 +1；失败/空数据时保留旧版本，
 * 便于排查「这一条到底是哪次抓取的结果」。
 */
data class ScheduleSourceState(
    val sourceId: String,
    val displayName: String,
    val priority: Int,
    val available: Boolean,
    val itemCount: Int,
    val version: Int,
)

/**
 * 本次时间表的来源信息：base 源、参与合并的源、不可用的源、冲突条数。
 *
 * 供 UI 标注「数据来源」与线上监控使用；[conflictCount] 只统计去重阶段的冲突，
 * 明细落在数据库的复核队列里。
 */
data class ScheduleProvenance(
    val baseSourceId: String = "",
    val baseSourceLabel: String = "",
    val usedSourceLabels: List<String> = emptyList(),
    val unavailableSourceLabels: List<String> = emptyList(),
    val conflictCount: Int = 0,
) {
    /** 是否发生了降级（base 不是优先级最高的源）。 */
    val isDegraded: Boolean
        get() = unavailableSourceLabels.isNotEmpty() && baseSourceLabel.isNotEmpty()
}
