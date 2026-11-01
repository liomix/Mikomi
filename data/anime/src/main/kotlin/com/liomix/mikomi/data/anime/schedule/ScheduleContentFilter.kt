package com.liomix.mikomi.data.anime.schedule

import java.util.Locale

/**
 * 时间表内容过滤：在**物化阶段（进库之前）**把「不该出现的条目」和「不该出现的文案」
 * 一起收敛掉。两条规则放在同一个对象里，是因为它们互相影响（见下）。
 *
 * ### 一、已完结的番整条丢掉
 *
 * 各源的章节文案口径不同，但「完结」这件事是共通的：
 * - 次元库超清：`全10话,已完结   ⏰23:30`；
 * - 次元库樱花：整条文案就是 `已完结`（实测前 7 天 64 条里有 17 条）；
 * - AniRpb：文案里偶尔带 `已完结` / `全12话`。
 *
 * 完结的番仍留在上游日历里是站点的产品口径（方便回看），对时间表却是噪声：它本周并没有
 * 新一集，只会把「今天更新什么」淹掉。丢掉发生在判重 / 冲突检测 / 补简介之前。
 *
 * ### 二、更新文案只保留具体时刻
 *
 * 星期已经由日期 Tab 表达、集数由集数文案表达，所以更新文案里的 `集数#周X…更` 骨架全是
 * 冗余，只保留具体时刻（`4#周三21:00更` → `21:00`）；没有时刻就整段丢掉，交给 UI 用
 * [com.liomix.mikomi.core.model.AirTime] 的三态精度文案（时间待定 / 待公布）。
 * 时刻同时会被 [parseClock] 取出来喂给 `AirTime`，所以「文案里有、字段里没有」的时刻
 * 不会再白丢。
 *
 * ### 两者的耦合
 *
 * 先判完结、再收敛文案：`全12话,已完结 ⏰21:00` 这种文案既有时刻又有完结标记，若先把
 * 它压成 `21:00`，[isFinished] 就再也看不出它完结了。因此 [filterUpdateText] 遇到完结
 * 文案原样返回，由 [isFinished] 在物化阶段把整条丢掉。
 */
internal object ScheduleContentFilter {

    // ---------------------------------------------------------------- 完结判定

    /** 该条目是否已完结（看集数文案与更新文案两处，任一命中即算）。 */
    fun isFinished(item: ScheduleFeedItem): Boolean =
        isFinishedText(item.episodeText) || isFinishedText(item.updateText)

    fun isFinishedText(text: String): Boolean {
        if (text.isBlank()) return false
        if (FINISHED_MARKERS.any { text.contains(it) }) return true
        return FULL_EPISODE_TEXT.containsMatchIn(text)
    }

    // ---------------------------------------------------------------- 时刻过滤

    /**
     * 从任意更新文案里取出具体时刻 `HH:mm`，取不到（或越界）返回 null。
     *
     * 接受 `⏰9:00`、`21:00`、`9：00`（全角冒号）、`21:00:00` 等写法，统一补零成 `HH:mm`，
     * 这样 [com.liomix.mikomi.core.model.AirTime.of] 的 `\d{2}:\d{2}` 校验能直接吃下。
     */
    fun parseClock(raw: String): String? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        val match = CLOCK_WITH_ICON.find(text) ?: CLOCK_PLAIN.find(text) ?: return null
        val hour = match.groupValues[HOUR_GROUP].toIntOrNull() ?: return null
        val minute = match.groupValues[MINUTE_GROUP].toIntOrNull() ?: return null
        if (hour !in 0..MAX_HOUR || minute !in 0..MAX_MINUTE) return null
        return String.format(Locale.US, "%02d:%02d", hour, minute)
    }

    /**
     * 展示用文案过滤：只保留具体时刻。规则幂等（`21:00` 再滤一次还是 `21:00`）。
     *
     * - 文案里有具体时刻 → `HH:mm`（`4#周三21:00更` → `21:00`）；
     * - 「集数 + 星期 + 更」但没有时刻（`4#周三更`）→ 空串，信息全是冗余的；
     * - 其它文案（`全10话,已完结`）原样保留 —— 完结文案交给 [isFinished] 判定，
     *   这里不能压成时刻（见类注释「两者的耦合」）。
     */
    fun filterUpdateText(raw: String): String {
        val text = raw.trim()
        if (text.isEmpty()) return ""
        if (isFinishedText(text)) return text
        parseClock(text)?.let { return it }
        if (EPISODE_WEEKDAY_TEXT.containsMatchIn(text)) return ""
        return text
    }

    // ---------------------------------------------------------------- 内部规则

    /** `完结` / `完結`（简繁两种写法）。 */
    private val FINISHED_MARKERS = listOf("完结", "完結")

    /** `全12话` / `全 12 集`：全集放完也是完结，部分源只给这一种写法。 */
    private val FULL_EPISODE_TEXT = Regex("全\\s*\\d+\\s*[话話集]")

    private const val HOUR_GROUP = 1
    private const val MINUTE_GROUP = 2
    private const val MAX_HOUR = 23
    private const val MAX_MINUTE = 59

    private val CLOCK_WITH_ICON = Regex("⏰\\s*(\\d{1,2}):(\\d{2})")

    /** 前后都排除数字，避免把 `2026:11` 这类连写片段误当时刻。 */
    private val CLOCK_PLAIN = Regex("(?<!\\d)(\\d{1,2})[:：](\\d{2})(?!\\d)")

    /** AniRpb 的更新文案骨架：`<集数>#周<几>…更`。 */
    private val EPISODE_WEEKDAY_TEXT = Regex("^\\s*\\d*\\s*#?\\s*周[一二三四五六日天]")
}
