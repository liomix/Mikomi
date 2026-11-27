package com.liomix.mikomi.feature.home.components

/**
 * 一次刷新的三个事实：**在途 / 用户看得见 / 上次失败**。
 *
 * 本模块的三个页面（首页 / 排行榜 / 时间表）各自维护刷新，但它们的状态迁移只有这一种：
 * 发起时清掉失败、跑的时候在途、结束后收尾、出错只置失败位。以前三个 VM 各写一套布尔
 * 组合（`isRefreshing` + `manualRefreshing` + `refreshFailed` / `isRefreshing` +
 * `refreshFailed`），同一件事有三种表示 —— 于是「按钮该不该转」在三个页面的判断条件
 * 都长得不一样，改口径要改三处。
 *
 * 三个字段的分工：
 * - [inFlight] 只管「现在有一趟请求」，用于状态机分支与重复触发保护；
 * - [userVisible] 只管「这趟是不是用户点的」，自动首载不亮指示器；
 * - [failed] 只管「上一次失败了」，页面据此在没有数据时切错误态。
 *
 * [showsIndicator] 是两者相乘而不是单独一个字段：分开存就会出现「在途结束了、指示器
 * 还亮着」这种只能靠 finally 手工对齐的中间态。
 */
internal data class RefreshState(
    val inFlight: Boolean = false,
    val userVisible: Boolean = false,
    val failed: Boolean = false,
) {
    /** 页面要转的指示器：只有在途、且是用户发起的那一趟才转。 */
    val showsIndicator: Boolean get() = inFlight && userVisible
}