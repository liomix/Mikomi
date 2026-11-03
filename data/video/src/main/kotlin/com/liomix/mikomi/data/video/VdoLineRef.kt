package com.liomix.mikomi.data.video

/**
 * 一条线路的稳定身份。
 *
 * 为什么不用「源名 + 下标」：站点改版后 `chapterRoads` 的命中顺序会变，
 * 用户「记住的第 2 条线路」就悄悄变成了另一条，能力缓存也会张冠李戴。
 *
 * 所以身份由三样拼成：源名、线路名、以及**首集地址归一化后的形状**。
 * 地址形状只保留结构、把数字换成 `{n}`——同一站点换一部番时集号会变，
 * 但路径模板不会变，这正是我们要的稳定性。
 *
 * @property sourceName 规则名（也是 UI 上的源 id）。
 * @property lineName 规则 `chapterRoads` 给出的线路名。
 * @property urlShape 首集地址的形状，如 `/vodplay/{n}-{n}-{n}.html`。
 */
data class VdoLineRef(
    val sourceName: String,
    val lineName: String,
    val firstEpisodeUrl: String,
) {

    /** 缓存键。分隔符用 `|`：线路名里不会出现它，比拼 JSON 省事且可读。 */
    val storageKey: String
        get() = "$sourceName|$lineName|${shapeOf(firstEpisodeUrl)}"

    companion object {

        /** 把 URL 里的数字段折成 `{n}`，丢掉 query 与 hash。 */
        private fun shapeOf(url: String): String =
            DIGITS.replace(url.substringBefore('?').substringBefore('#'), "{n}")

        private val DIGITS = Regex("""\d+""")
    }
}
