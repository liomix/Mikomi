package com.liomix.mikomi.data.anime.schedule

/**
 * 日历源的「天索引锚点」解析。
 *
 * AniCyk 的追番日历只返回顺序、不返回日期。实测其接口 `day[0..6]` 与站点时间表
 * `week-1..week-7`（周一…周日）逐条一一对应，即 **day[0] = 本周一**，因此默认锚点是 0。
 * 但接口顺序没有契约保证，这里仍用同一天的 AniRpb 明确日期做**交叉校准**：把候选锚点
 * 逐一代入，选标题重叠度最高的那个。校准结果由调用方缓存到
 * `schedule_source.anchorStartIndex`，这样下次即使 AniRpb 不可用也能沿用上次的正确锚点。
 *
 * 注意这里只借用 AniRpb 的**日期**做校准，条目本身仍以 base（AniCyk）为准。
 */
internal object ScheduleDateAnchor {

    data class Result(
        /** day[0] 对应的周内索引（0 = 周一）。 */
        val startIndex: Int,
        /** 最佳锚点的重叠度（0..1）。 */
        val confidence: Double,
        /** 是否通过交叉校准得出（false 表示沿用缓存/默认值）。 */
        val calibrated: Boolean,
    )

    /**
     * @param dayTitles 每个日历日的归一化标题集合。
     * @param referenceByDate AniRpb 的「日期 → 归一化标题集合」。
     * @param week 本周窗口。
     * @param fallbackStartIndex 校准失败时使用（通常为 0，即「本周一」，或上次缓存值）。
     */
    fun resolve(
        dayTitles: List<Set<String>>,
        referenceByDate: Map<String, Set<String>>,
        week: ScheduleWeek,
        fallbackStartIndex: Int,
    ): Result {
        if (dayTitles.isEmpty() || referenceByDate.isEmpty()) {
            return Result(fallbackStartIndex, confidence = 0.0, calibrated = false)
        }

        var bestIndex = fallbackStartIndex
        var bestScore = 0.0
        for (startIndex in week.dates.indices) {
            var score = 0.0
            var covered = 0
            dayTitles.forEachIndexed { dayOffset, titles ->
                val date = week.dateAt(startIndex + dayOffset) ?: return@forEachIndexed
                val reference = referenceByDate[date] ?: return@forEachIndexed
                if (titles.isEmpty() || reference.isEmpty()) return@forEachIndexed
                covered++
                score += overlapRatio(titles, reference)
            }
            if (covered == 0) continue
            val average = score / covered
            if (average > bestScore) {
                bestScore = average
                bestIndex = startIndex
            }
        }

        return if (bestScore >= MIN_CONFIDENCE) {
            Result(bestIndex, bestScore, calibrated = true)
        } else {
            Result(fallbackStartIndex, bestScore, calibrated = false)
        }
    }

    /** 重叠率以较小集合为分母：两源覆盖面差异大时，用并集会把对齐信号摊薄。 */
    private fun overlapRatio(left: Set<String>, right: Set<String>): Double {
        val smaller = minOf(left.size, right.size).coerceAtLeast(1)
        val hits = left.count { it in right }
        return hits.toDouble() / smaller
    }

    /** 低于该重叠度就认为校准不可信，回退到缓存/默认锚点。 */
    private const val MIN_CONFIDENCE = 0.2
}
