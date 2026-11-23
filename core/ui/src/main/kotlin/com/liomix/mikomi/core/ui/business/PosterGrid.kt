package com.liomix.mikomi.core.ui.business

import androidx.compose.ui.unit.Dp

/**
 * 海报网格的列数计算与「补满一行」。
 *
 * ## 为什么不用 `GridCells.Adaptive`
 *
 * 自适应列数的值要在**测量阶段**才确定，组合期拿不到。而海报网格有一个组合期就必须回答的
 * 问题：**最后一行还差几格**。答不上来的后果不是少算一个数，而是布局上真的破一个洞 ——
 * 7 张卡铺满两行、第三行只剩 1 张时，右边两格没有任何东西，下面的分页骨架又另起一整行，
 * 中间就留着一块空白背景。
 *
 * 所以这里把 `Adaptive` 的公式抄出来自己算（公式一致，布局结果完全相同），再把列数当普通
 * 参数用：网格改用 [androidx.compose.foundation.lazy.grid.GridCells.Fixed]，骨架可以
 * 精确地补齐当前行，首屏骨架也能只铺整数行。
 *
 * 用法：
 * ```
 * BoxWithConstraints {
 *     val columns = PosterGrid.columnsFor(maxWidth, minColumnWidth, spacing)
 *     LazyVerticalGrid(columns = GridCells.Fixed(columns)) {
 *         items(...)
 *         if (isLoading) {
 *             items(count = PosterGrid.cellsToFillRow(columns, count) ?: columns) { Skeleton() }
 *         }
 *     }
 * }
 * ```
 */
object PosterGrid {

    /**
     * 一行能放几格。
     *
     * 与 `GridCells.Adaptive(minSize)` 完全同一条公式：`n = (可用宽 + 间距) / (最小宽 + 间距)`。
     * 间距出现在分子上，是因为 n 个格子之间只有 n-1 条缝，把缝补成 n 条正好抵消。
     *
     * @param width 网格可用宽度（已扣掉 contentPadding）。
     * @param minColumnWidth 与 `Adaptive` 的 `minSize` 同值。
     * @param spacing 网格的水平间距。
     */
    fun columnsFor(width: Dp, minColumnWidth: Dp, spacing: Dp): Int {
        val slots = (width + spacing) / (minColumnWidth + spacing)
        return slots.toInt().coerceAtLeast(1)
    }

    /**
     * 补齐**当前行**还需要几格。
     *
     * @return `1..columns-1`：差几格补几格；`0`：正好铺满，调用方若要继续显示骨架，
     *   应当再起一整行（通常写成 `cellsToFillRow(...) ?: columns`）。
     */
    fun cellsToFillRow(columns: Int, itemCount: Int): Int =
        (columns - itemCount % columns) % columns
}
