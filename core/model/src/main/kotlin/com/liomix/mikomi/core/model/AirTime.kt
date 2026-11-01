package com.liomix.mikomi.core.model

/**
 * 放送时间的精度三态。
 *
 * 各数据源给的粒度不同：AniRpb 的更新表带具体时刻，AniCyk 的日历只有日期（少数条目
 * 的章节文案里带 `⏰HH:mm`）。用三态建模后，UI 能区分「精确 / 时间待定 / 待公布」，
 * 合并阶段也只在**精度更低**的一侧才允许被补全。
 */
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
