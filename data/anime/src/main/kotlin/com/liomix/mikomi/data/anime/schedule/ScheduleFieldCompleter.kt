package com.liomix.mikomi.data.anime.schedule

import android.util.Log
import com.liomix.mikomi.core.database.dao.AnimeDao
import com.liomix.mikomi.core.model.AirTime
import com.liomix.mikomi.core.model.AirTimePrecision
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 时间表字段补全：**唯一**决定「哪些字段会被补、按什么优先级补、冲突怎么记」的地方。
 *
 * 补全分两个入口，因为输入不同：
 *
 * 1. [fillFromDuplicate] —— 跨源 patch。合并判定为「同一条更新」时，用低优先级源的候选
 *    补 base 缺失的字段。候选条目在命中重复后就被丢弃了，所以它只在 merge 循环里可用，
 *    由 [ScheduleMerger] 调用（target 传合并行）。
 * 2. [completeFromLibrary] —— 本地番剧库兜底。合并完成后按归一化标题从 `anime` 表
 *    （首页 / 详情页缓存的 AniList 数据）补简介，批量、不联网、不记冲突。
 *
 * 补全原则始终是「只填空缺、不覆盖」：base 已有的值一律保留，取不到就保持为空。
 *
 * | 字段 | 来源 | 触发 |
 * |---|---|---|
 * | 具体时刻 | 低优先级源（EXACT 且日期一致） | 命中重复 + base 非 EXACT |
 * | 简介 | 低优先级源的同一条更新 | 命中重复 + base 无简介 |
 * | 简介 | 本地番剧库（标题匹配） | 合并后批量 |
 *
 * EXACT 时刻永不被覆盖；日期/时刻对不上时保留 base 并记冲突进复核队列。
 */
@Singleton
internal class ScheduleFieldCompleter @Inject constructor(
    private val animeDao: AnimeDao,
    private val config: ScheduleMergeConfig,
) {

    // ------------------------------------------------------------ 入口一：跨源 patch

    /**
     * 命中重复时的字段补全：补简介 + 补具体时刻。
     *
     * 补时刻受 [ScheduleMergeConfig.patchMissingTime] 控制，补简介受
     * [ScheduleMergeConfig.patchMissingSynopsis] 控制，两者互不影响：
     * 关掉补时刻（纯去重）时仍可补简介。
     */
    fun fillFromDuplicate(
        target: FieldPatchTarget,
        candidate: ScheduleFeedItem,
        conflicts: MutableList<ScheduleConflict>,
    ) {
        fillSynopsis(target, candidate)
        if (!config.patchMissingTime) return
        fillAirTime(target, candidate, conflicts)
    }

    /**
     * 补简介：只在 base 没有简介时采纳低优先级源的值。
     *
     * 简介不影响判重与排序，纯展示字段，因此不做冲突记录 —— 两个源描述不同属于正常现象。
     */
    private fun fillSynopsis(target: FieldPatchTarget, candidate: ScheduleFeedItem) {
        if (!config.patchMissingSynopsis) return
        if (!target.synopsis.isNullOrBlank()) return
        val incoming = candidate.synopsis?.trim()
        if (incoming.isNullOrBlank()) return
        target.synopsis = incoming
    }

    /** 补具体时刻：只在 base 精度低于 EXACT 且候选是 EXACT 时采纳，并标 `inferred`。 */
    private fun fillAirTime(
        target: FieldPatchTarget,
        candidate: ScheduleFeedItem,
        conflicts: MutableList<ScheduleConflict>,
    ) {
        val base = target.airTime
        val incoming = candidate.airTime

        // EXACT 永不被覆盖；只有时刻互相矛盾时留一条监控记录。
        if (base.precision == AirTimePrecision.EXACT) {
            if (incoming.precision == AirTimePrecision.EXACT &&
                base.time != null &&
                incoming.time != null &&
                base.time != incoming.time &&
                base.date == incoming.date
            ) {
                conflicts += scheduleConflictFor(
                    target = target,
                    type = ScheduleConflictType.TIME_MISMATCH,
                    candidateSourceId = candidate.sourceId,
                    candidateAirTime = incoming,
                )
            }
            return
        }

        // 低优先级源也只有日期时，没有可补的信息。
        if (incoming.precision != AirTimePrecision.EXACT) return

        val incomingTime = incoming.time ?: return

        // 日期不一致时保留 base，记冲突（不猜测谁对）。
        if (base.date != null && incoming.date != null && base.date != incoming.date) {
            conflicts += scheduleConflictFor(
                target = target,
                type = ScheduleConflictType.DATE_MISMATCH,
                candidateSourceId = candidate.sourceId,
                candidateAirTime = incoming,
            )
            return
        }

        val date = base.date ?: incoming.date ?: return
        target.airTime = AirTime.exact(date = date, time = incomingTime, inferred = true)
        target.airingAtMillis = ScheduleWeek.toMillis(date, incomingTime)
    }

    // -------------------------------------------------------- 入口二：本地库兜底补简介

    /**
     * 简介补全：合并之后用**本地番剧库**再兜一轮。
     *
     * 时间表的 base 源（AniCyk）只有标题/封面/更新时间，没有简介；低优先级源（AniRpb）虽有，
     * 但它本身可能不可用。因此这里用首页 / 详情页已缓存的 AniList 数据按标题补齐：
     *
     * - 只补空缺，已有简介的行原样返回；
     * - 先做归一化标题精确匹配，再做「互为前缀、长度接近、季数不冲突、候选唯一」的模糊匹配；
     * - 匹配不到就保持为空（UI 显示「暂无简介」），不发额外网络请求、不抛错。
     */
    suspend fun completeFromLibrary(rows: List<MergedScheduleRow>): List<MergedScheduleRow> {
        if (rows.isEmpty()) return rows
        if (rows.none { it.synopsis.isNullOrBlank() }) return rows

        val library = animeDao.titlesWithSynopsis().mapNotNull { entry ->
            val signature = TitleNormalizer.signature(entry.title)
            if (signature.isBlank) {
                null
            } else {
                LibrarySynopsis(
                    key = signature.normalized,
                    season = signature.season,
                    synopsis = entry.synopsis,
                )
            }
        }
        if (library.isEmpty()) return rows

        val byKey = library.groupBy { it.key }

        var filled = 0
        val result = rows.map { row ->
            if (!row.synopsis.isNullOrBlank()) return@map row
            val signature = TitleNormalizer.signature(row.title)
            if (signature.isBlank) return@map row
            val synopsis = byKey[signature.normalized]
                ?.firstOrNull { seasonsCompatible(signature.season, it.season) }
                ?.synopsis
                ?: uniquePrefixMatch(signature, library)
                ?: return@map row
            filled++
            row.copy(synopsis = synopsis)
        }

        if (filled > 0) {
            Log.i(TAG, "简介补全：本地番剧库命中 $filled/${rows.size} 条")
        }
        return result
    }

    /**
     * 前缀模糊匹配：只在候选唯一时采纳。
     *
     * 多个候选同时命中说明无法判断是哪一部（例如「死神」与「死神 千年血战篇」都在库里），
     * 此时宁可留空也不写错简介。
     */
    private fun uniquePrefixMatch(
        signature: TitleNormalizer.Signature,
        library: List<LibrarySynopsis>,
    ): String? {
        val key = signature.normalized
        var matched: String? = null
        library.forEach { candidate ->
            val other = candidate.key
            if (other == key) return@forEach
            if (!seasonsCompatible(signature.season, candidate.season)) return@forEach
            // 太短的标题做前缀匹配误伤严重（「剑」会命中「剑来」）。
            if (minOf(key.length, other.length) < MIN_PREFIX_LENGTH) return@forEach
            if (kotlin.math.abs(key.length - other.length) > MAX_TITLE_LENGTH_DELTA) return@forEach
            if (!key.startsWith(other) && !other.startsWith(key)) return@forEach
            if (matched != null) return null
            matched = candidate.synopsis
        }
        return matched
    }

    /** 季数消歧：两边季数都明确且不同，就不是同一部。 */
    private fun seasonsCompatible(left: Int?, right: Int?): Boolean =
        left == null || right == null || left == right

    /** 本地番剧库里的一条候选。 */
    private data class LibrarySynopsis(
        val key: String,
        val season: Int?,
        val synopsis: String,
    )

    private companion object {
        const val TAG = "ScheduleFieldComplete"

        /** 前缀匹配允许的标题长度差，与 [ScheduleMerger] 的自动匹配口径一致。 */
        const val MAX_TITLE_LENGTH_DELTA = 4

        /** 前缀匹配的最短标题长度（按归一化后的字符数算）。 */
        const val MIN_PREFIX_LENGTH = 4
    }
}

/**
 * 可被跨源 patch 的合并行。
 *
 * 抽成接口是为了让补全逻辑待在 [ScheduleFieldCompleter] 里，同时不必把 [ScheduleMerger]
 * 内部的合并行 `MutableRow` 暴露出去（它只在 merge 循环里可变、生命周期只有一次合并）。
 */
internal interface FieldPatchTarget {
    val canonicalKey: String
    val sourceId: String
    val title: String
    var airTime: AirTime
    var airingAtMillis: Long
    var synopsis: String?
}
