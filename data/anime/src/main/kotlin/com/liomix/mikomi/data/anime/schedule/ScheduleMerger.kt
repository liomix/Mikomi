package com.liomix.mikomi.data.anime.schedule

import com.liomix.mikomi.core.model.AirTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 合并 / 补全策略开关。
 *
 * 前两项是**字段补全**策略，由 [ScheduleFieldCompleter] 消费；后两项是**判重**策略，
 * 由 [ScheduleMerger] 消费。
 */
internal data class ScheduleMergeConfig(
    /**
     * 命中重复后是否走 patch（只补 base 缺失的字段，不覆盖已有值）。
     *
     * 关掉即退回「纯去重」：重复条目直接丢弃，不产生任何字段补全。
     */
    val patchMissingTime: Boolean = true,
    /**
     * 是否用低优先级源补 base 缺失的**简介**。
     *
     * 时间表的 base 源（AniCyk）不提供简介，低优先级源（AniRpb）有；
     * 只填空缺、不覆盖，取不到就保持为空（UI 显示「暂无简介」）。
     */
    val patchMissingSynopsis: Boolean = true,
    /** 判重的日期容差（天）：跨源时区 / 日界差异会导致同一条更新差一天。 */
    val dayWindow: Int = 1,
    /** 判重的集数容差：各源集数文案口径不同，差 1 集内视为同一条。 */
    val episodeTolerance: Int = 1,
)

/** 合并结果中的一行（已写入数据库前的形态）。 */
internal data class MergedScheduleRow(
    val canonicalKey: String,
    val sourceId: String,
    val entryId: String,
    val title: String,
    val coverUrl: String?,
    val synopsis: String?,
    val airTime: AirTime,
    val airingAtMillis: Long,
    val episode: Int,
    val episodeText: String,
    val updateText: String,
    val channelId: String,
    val detailLink: String,
)

/** 冲突类型：日期对不上 / 时刻对不上 / 集数对不上 / 标题靠前缀自动匹配（待复核）。 */
internal enum class ScheduleConflictType {
    DATE_MISMATCH,
    TIME_MISMATCH,
    EPISODE_MISMATCH,
    TITLE_AMBIGUOUS,
}

internal data class ScheduleConflict(
    val canonicalKey: String,
    val title: String,
    val type: ScheduleConflictType,
    val baseSourceId: String,
    val baseDate: String,
    val baseTime: String,
    val candidateSourceId: String,
    val candidateDate: String,
    val candidateTime: String,
)

/**
 * 冲突记录的唯一构造入口。
 *
 * 合并阶段的冲突（集数 / 标题）与补全阶段的冲突（时刻 / 日期）都从这里生成，
 * 保证复核队列里两种来源的记录形状一致。
 */
internal fun scheduleConflictFor(
    target: FieldPatchTarget,
    type: ScheduleConflictType,
    candidateSourceId: String,
    candidateAirTime: AirTime,
    title: String = target.title,
): ScheduleConflict = ScheduleConflict(
    canonicalKey = target.canonicalKey,
    title = title,
    type = type,
    baseSourceId = target.sourceId,
    baseDate = target.airTime.date.orEmpty(),
    baseTime = target.airTime.time.orEmpty(),
    candidateSourceId = candidateSourceId,
    candidateDate = candidateAirTime.date.orEmpty(),
    candidateTime = candidateAirTime.time.orEmpty(),
)

internal data class ScheduleMergeOutcome(
    val rows: List<MergedScheduleRow>,
    /** 基准源 id。 */
    val baseSourceId: String,
    /** 本次参与合并的源（按优先级）。 */
    val usedSourceIds: List<String>,
    val conflicts: List<ScheduleConflict>,
    /** 本次观察到的身份分组，用于回写别名映射。 */
    val identityGroups: List<IdentityGroup>,
)

/**
 * 多源时间表合并。
 *
 * 两阶段流程：
 *
 * **第一阶段（去重）**：按 priority 升序处理，第一个可用源自动成为 base，它的条目
 * 全部保留；后续源逐条以 canonical 身份键（外部 id → 映射表 → 归一化标题 + 别名）
 * 判重，命中则丢、未命中则追加。首源挂掉时 base 自动落到次优先级源，逻辑不变。
 *
 * **第二阶段（字段补充）**：命中的重复条目交给 [ScheduleFieldCompleter.fillFromDuplicate]
 * —— 只用低优先级源补 base 缺失的**具体播出时刻**与**简介**，其余字段一律以 base 为准。
 * EXACT 永不被覆盖；base 为 UNKNOWN / DATE_ONLY 时，仅当对方是 EXACT 且日期一致才采纳时刻，
 * 并标 `inferred`。日期/时刻对不上时保留 base 并记冲突，落日志与复核队列。
 * 补全规则全部集中在 [ScheduleFieldCompleter]，本类只负责「身份 + 判重 + 排序」。
 *
 * 判重键是「canonical 身份 + 播出日期窗口」：同一部番一周内更新多天（例如周日加更）
 * 属于不同条目，只有落在同一天（±[ScheduleMergeConfig.dayWindow]）才算同一条更新。
 */
@Singleton
internal class ScheduleMerger @Inject constructor(
    private val identityRegistry: AnimeIdentityRegistry,
    private val fieldCompleter: ScheduleFieldCompleter,
    private val config: ScheduleMergeConfig,
) {

    fun merge(feeds: List<ScheduleFeed>): ScheduleMergeOutcome {
        require(feeds.isNotEmpty()) { "合并至少需要一个可用源" }
        val ordered = feeds.sortedBy { it.priority }

        val rows = mutableListOf<MutableRow>()
        val conflicts = mutableListOf<ScheduleConflict>()

        ordered.forEach { feed ->
            feed.items.forEach { item ->
                val identity = identityRegistry.resolve(item)
                // 1) canonical 精确命中；
                // 2) 未命中时尝试「自动匹配」：同日期窗口 + 集数相近 + 标题互为前缀，
                //    命中后把变体标题学进别名表，同时留一条待复核记录。
                val exact = rows.firstOrNull { row ->
                    row.isSameIdentity(identity) &&
                        row.seasonMatches(identity.signature.season) &&
                        row.isSameAirWindow(item.airTime, item.episode, config)
                }
                val auto = if (exact == null) {
                    rows.firstOrNull { row ->
                        // 只在跨源之间做模糊匹配：同源内部标题不同就是不同条目。
                        row.sourceId != item.sourceId &&
                            row.seasonMatches(identity.signature.season) &&
                            row.isSameAirWindow(item.airTime, item.episode, config) &&
                            row.isTitleVariant(identity.signature)
                    }
                } else {
                    null
                }

                when {
                    exact != null -> {
                        exact.titles += item.title
                        // 同一天被迫合并但集数口径差很多时留痕，供运营判断是否漏了一条。
                        if (exact.hasEpisodeConflict(item.episode, config)) {
                            conflicts += exact.conflict(
                                type = ScheduleConflictType.EPISODE_MISMATCH,
                                candidateSourceId = item.sourceId,
                                candidateAirTime = item.airTime,
                            )
                        }
                        fieldCompleter.fillFromDuplicate(exact, item, conflicts)
                    }

                    auto != null -> {
                        auto.titles += item.title
                        conflicts += auto.conflict(
                            type = ScheduleConflictType.TITLE_AMBIGUOUS,
                            candidateSourceId = item.sourceId,
                            candidateAirTime = item.airTime,
                            title = "${auto.title} / ${item.title}",
                        )
                        fieldCompleter.fillFromDuplicate(auto, item, conflicts)
                    }
                    else -> rows += MutableRow.of(feed.sourceId, item, identity)
                }
            }
        }

        val merged = rows
            .sortedWith(
                compareBy(
                    { it.airTime.date ?: UNKNOWN_DATE_ORDER },
                    { it.airTime.time ?: UNKNOWN_TIME_ORDER },
                    { it.canonicalKey },
                    { it.entryId },
                ),
            )
            .map { it.toRow() }

        return ScheduleMergeOutcome(
            rows = merged,
            baseSourceId = ordered.first().sourceId,
            usedSourceIds = ordered.map { it.sourceId },
            conflicts = conflicts,
            identityGroups = buildIdentityGroups(rows),
        )
    }

    /** 按 canonical 键聚合本次见过的标题变体，交给身份表沉淀为别名。 */
    private fun buildIdentityGroups(rows: List<MutableRow>): List<IdentityGroup> =
        rows.groupBy { it.canonicalKey }.mapNotNull { (canonicalKey, group) ->
            val baseTitle = group.firstOrNull()?.title?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            IdentityGroup(
                canonicalKey = canonicalKey,
                baseTitle = baseTitle,
                titles = group.flatMap { it.titles }.filter { it.isNotBlank() }.toSet(),
                externalIds = group.flatMap { it.externalIds.entries }
                    .associate { it.key to it.value },
            )
        }

    /**
     * 合并过程中的可变行；只在 [merge] 内部使用。
     *
     * 实现 [FieldPatchTarget] 是为了让补全逻辑留在 [ScheduleFieldCompleter] 里，
     * 又不必把这个私有结构暴露出去。
     */
    private data class MutableRow(
        override val canonicalKey: String,
        override val sourceId: String,
        val entryId: String,
        override val title: String,
        val coverUrl: String?,
        /** 可被 patch 补全（base 源没有简介时采纳低优先级源的值）。 */
        override var synopsis: String?,
        override var airTime: AirTime,
        override var airingAtMillis: Long,
        val episode: Int,
        val episodeText: String,
        val updateText: String,
        val channelId: String,
        val detailLink: String,
        val season: Int?,
        val signature: TitleNormalizer.Signature,
        val externalIds: Map<String, String>,
        /** 该行见过的所有原始标题（含自动匹配到的变体），用于沉淀别名。 */
        val titles: MutableSet<String>,
    ) : FieldPatchTarget {

        fun isSameIdentity(identity: ResolvedIdentity): Boolean =
            canonicalKey == identity.canonicalKey

        /** 季数消歧：两边季数都明确且不同时，视为不同条目。 */
        fun seasonMatches(otherSeason: Int?): Boolean =
            season == null || otherSeason == null || season == otherSeason

        /** 两条同身份条目的集数口径是否相差过大（累计话数 vs 本季集数）。 */
        fun hasEpisodeConflict(otherEpisode: Int, config: ScheduleMergeConfig): Boolean {
            if (episode <= 0 || otherEpisode <= 0) return false
            return kotlin.math.abs(episode - otherEpisode) > config.episodeTolerance
        }

        /**
         * 自动匹配：标题互为前缀且长度接近时，认为指向同一部（例如 `PSYREN` 与
         * `PSYREN 决战游戏`）。归一化标题完全相同的情况走 canonical 精确匹配。
         */
        fun isTitleVariant(other: TitleNormalizer.Signature): Boolean {
            val left = signature.normalized
            val right = other.normalized
            if (left.isBlank() || right.isBlank() || left == right) return false
            val delta = kotlin.math.abs(left.length - right.length)
            if (delta > MAX_TITLE_LENGTH_DELTA) return false
            return left.startsWith(right) || right.startsWith(left)
        }

        /** 日期窗口 + 集数容差：判断是否为「同一条更新」。 */
        fun isSameAirWindow(other: AirTime, otherEpisode: Int, config: ScheduleMergeConfig): Boolean {
            val baseDate = airTime.date
            val otherDate = other.date
            // 双方都没有日期：canonical 相同即视为同一条。
            if (baseDate == null || otherDate == null) return true

            val diff = ScheduleWeek.daysBetween(baseDate, otherDate)
                ?.let { kotlin.math.abs(it) }
                ?: return true
            if (diff > config.dayWindow) return false

            val episodeDiff = if (episode > 0 && otherEpisode > 0) {
                kotlin.math.abs(episode - otherEpisode)
            } else {
                0
            }
            if (episodeDiff <= config.episodeTolerance) return true

            // 同一天同一部番、集数口径却差很多（累计话数 vs 本季集数）时仍视为同一条：
            // 宁可合并也不要在同日重复出现两条。
            return diff == 0
        }

        fun conflict(
            type: ScheduleConflictType,
            candidateSourceId: String,
            candidateAirTime: AirTime,
            title: String = this.title,
        ): ScheduleConflict = scheduleConflictFor(
            target = this,
            type = type,
            candidateSourceId = candidateSourceId,
            candidateAirTime = candidateAirTime,
            title = title,
        )

        fun toRow(): MergedScheduleRow = MergedScheduleRow(
            canonicalKey = canonicalKey,
            sourceId = sourceId,
            entryId = entryId,
            title = this.title,
            coverUrl = coverUrl,
            synopsis = synopsis,
            airTime = airTime,
            airingAtMillis = airingAtMillis,
            episode = episode,
            episodeText = episodeText,
            updateText = updateText,
            channelId = channelId,
            detailLink = detailLink,
        )

        companion object {
            fun of(sourceId: String, item: ScheduleFeedItem, identity: ResolvedIdentity): MutableRow =
                MutableRow(
                    canonicalKey = identity.canonicalKey,
                    sourceId = sourceId,
                    entryId = item.entryId,
                    title = item.title,
                    coverUrl = item.coverUrl,
                    synopsis = item.synopsis,
                    airTime = item.airTime,
                    airingAtMillis = ScheduleWeek.toMillis(item.airTime.date, item.airTime.time),
                    episode = item.episode,
                    episodeText = item.episodeText,
                    updateText = item.updateText,
                    channelId = item.channelId,
                    detailLink = item.detailLink,
                    season = identity.signature.season,
                    signature = identity.signature,
                    externalIds = item.externalIds,
                    titles = mutableSetOf(item.title),
                )
        }
    }

    private companion object {
        /** 无日期的行排在最后，保证排序结果幂等。 */
        const val UNKNOWN_DATE_ORDER = "9999-12-31"
        const val UNKNOWN_TIME_ORDER = "99:99"

        /** 自动匹配允许的标题长度差：再长就算不同作品，避免「死神」吃掉「千年血战篇」。 */
        const val MAX_TITLE_LENGTH_DELTA = 4
    }
}
