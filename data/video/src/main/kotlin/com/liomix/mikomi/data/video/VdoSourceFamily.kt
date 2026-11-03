package com.liomix.mikomi.data.video

/**
 * 大源（source family）。
 *
 * 「大源」是**出流实现的家族**，不是某部片的来源：一个家族对应一套抓取与出流代码，
 * 家族内部还有很多具体的源（插件源里的 DM84、7sefun…）。
 *
 * - [PLUGIN]：JSON 规则源，住在 `remote/vdoplg/`。装多少份插件都算这**一个**家族；
 * - [CYK] / [RPB]：非规则的独立源，各自一套实现（`remote/vdocyk/`、`remote/vdorpb/`）。
 *
 * 换源弹窗的 Tab 按它分组：Tab 是大源，Tab 里才是具体的源与它们的线路。
 * 做成枚举而不是字符串，是为了让「哪个源归哪个家族」在编译期就定死，UI 不用猜也不用兜底。
 */
enum class VdoSourceFamily(
    /**
     * Tab 上的代号（`VdoPlg` / `VdoCyk` / `VdoRpb`）。
     *
     * 刻意不进 strings.xml：它是这套架构的专有名词，既不翻译也不该被本地化。
     */
    val label: String,
) {
    PLUGIN("VdoPlg"),
    CYK("VdoCyk"),
    RPB("VdoRpb"),
}
