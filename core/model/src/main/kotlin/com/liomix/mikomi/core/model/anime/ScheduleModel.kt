package com.liomix.mikomi.core.model.anime

import com.liomix.mikomi.core.model.Anime
import kotlinx.serialization.Serializable

/**
 * 放送时间的精度三态。
 *
 * 各数据源给的粒度不同：AniRpb 的更新表带具体时刻，AniCyk 的日历只有日期（少数条目
 * 的章节文案里带 `⏰HH:mm`）。用三态建模后，UI 能区分「精确 / 时间待定 / 待公布」，
 * 合并阶段也只在**精度更低**的一侧才允许被补全。
 */
@Serializable
enum class AirTimePrecision {
    /** 日期 + 具体时刻（HH:mm）。 */
    EXACT,

    /** 只有日期，时刻未公布。 */
    DATE_ONLY,

    /** 连日期都不确定。 */
    UNKNOWN,
}

/**
 * 归一化后的放送时间。
 *
 * [date] 为本地时区（`Asia/Shanghai`）下的 `yyyy-MM-dd`，[time] 为 `HH:mm`。
 * [inferred] 表示时刻由低优先级源补全（base 原本只有日期），UI 需要弱化展示。
 *
 * 不用 `java.time`：minSdk 24 且未开启 core library desugaring，`LocalDate` 需 API 26。
 */
@Serializable
data class AirTime(
    val date: String?,
    val time: String?,
    val precision: AirTimePrecision,
    /** 时刻是否来自低优先级源的推断补全。 */
    val inferred: Boolean = false,
) {

    /** 是否可直接展示具体时刻。 */
    val hasExactTime: Boolean
        get() = precision == AirTimePrecision.EXACT && !time.isNullOrBlank()

    /** 是否只有日期（用于判断能否被低优先级源补时刻）。 */
    val isTimeMissing: Boolean
        get() = !hasExactTime

    companion object {

        val UNKNOWN = AirTime(date = null, time = null, precision = AirTimePrecision.UNKNOWN)

        fun exact(date: String, time: String, inferred: Boolean = false): AirTime = AirTime(
            date = date,
            time = time,
            precision = AirTimePrecision.EXACT,
            inferred = inferred,
        )

        fun dateOnly(date: String): AirTime = AirTime(
            date = date,
            time = null,
            precision = AirTimePrecision.DATE_ONLY,
        )

        /**
         * 由「日期 + 可选时刻」推导精度：有时刻 → EXACT，有日期 → DATE_ONLY，
         * 都没有 → UNKNOWN。日期非法（非 `yyyy-MM-dd`）时降级为 UNKNOWN。
         */
        fun of(date: String?, time: String?): AirTime {
            val safeDate = date?.takeIf { DATE_REGEX.matches(it) }
            val safeTime = time?.takeIf { TIME_REGEX.matches(it) }
            return when {
                safeDate == null -> UNKNOWN
                safeTime == null -> dateOnly(safeDate)
                else -> exact(safeDate, safeTime)
            }
        }

        private val DATE_REGEX = Regex("\\d{4}-\\d{2}-\\d{2}")
        private val TIME_REGEX = Regex("\\d{2}:\\d{2}")
    }
}

/** 时间表条目：番剧展示信息与该集的放送时间。 */
@Serializable
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
    /**
     * 更新文案（已按时间表过滤规则收敛）：含具体时刻时只保留 `HH:mm`（`4#周三21:00更` →
     * `21:00`），否则为空串，展示层回退 [airTime] 的三态文案。
     */
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
@Serializable
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
