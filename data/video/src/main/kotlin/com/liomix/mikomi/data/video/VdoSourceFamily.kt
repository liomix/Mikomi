package com.liomix.mikomi.data.video

/**
 * 大源（source family）。
 *
 * 「大源」是**出流实现的家族**，不是某部片的来源：一个家族对应一套抓取与出流代码，
 * 家族内部还有很多具体的源（插件源里的 DM84、7sefun…）。
 *
 * 换源弹窗的 Tab 按它分组：Tab 是大源，Tab 里才是具体的源与它们的线路。
 * 做成枚举而不是字符串，是为了让「哪个源归哪个家族」在编译期就定死，UI 不用猜也不用兜底。
 *
 * ## 接入状态
 *
 * **当前只接入了 [PLUGIN]。** 其余两个值刻意保留、但没有任何实现：
 * - [CYK] / [RPB] 是占位，`remote/vdocyk/`、`remote/vdorpb/` 目前只有一份 README；
 * - 保留枚举值不会影响 UI——播放页的换源弹窗（`PlaybackSourceSheet`）按「实际有源的大源」
 *   过滤后才建 Tab（`groupBy` 结果里没有就不出现），所以不会留空 Tab；
 * - 也别为了让列表好看而临时给它们塞假数据：Tab 一旦出现，用户点进去只会看到空列表。
 *
 * 反过来，**删掉这两个值也不对**：Tab 的位置由声明顺序决定，将来补上时顺序会跳变。
 * 接的时候在对应目录里实现、在数据层显式指定 family，UI 一行都不用改。
 */
enum class VdoSourceFamily(
    /**
     * Tab 上的代号（`VdoPlg` / `VdoCyk` / `VdoRpb`）。
     *
     * 刻意不进 strings.xml：它是这套架构的专有名词，既不翻译也不该被本地化。
     */
    val label: String,
) {
    /** 已接入：JSON 插件源，实现全在 `remote/vdoplg/`。装多少份插件都算这**一个**家族。 */
    PLUGIN("VdoPlg"),

    /** **暂未接入**：非规则的独立源，将来住 `remote/vdocyk/`。目前该目录只有 README。 */
    CYK("VdoCyk"),

    /** **暂未接入**：非规则的独立源，将来住 `remote/vdorpb/`。目前该目录只有 README。 */
    RPB("VdoRpb"),
}
