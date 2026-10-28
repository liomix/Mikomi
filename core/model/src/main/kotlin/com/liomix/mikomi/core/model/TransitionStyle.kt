package com.liomix.mikomi.core.model

/**
 * 详情页转场动画样式（对齐 Dart 版 `HeroAnimationStyle`）。
 *
 * 用于「点进番剧详情页」时的页面过渡动画。
 * `id` 持久化到 DataStore（`transition_style`）。
 *
 * 说明：Compose 的 `EnterTransition` 只提供 fade / slide / scale / expand 系列，
 * **没有**旋转类转场（`rotateIn` / `rotateOut` 不存在），且 `EnterTransition` 不可自定义，
 * 因此不提供「旋转」样式——保留一个设置项却没有真实效果反而是缺陷。
 */
enum class TransitionStyle(
    val id: String,
) {
    /** 标准过渡：横向滑入滑出（默认）。 */
    Standard("standard"),

    /** 淡入淡出。 */
    Fade("fade"),

    /** 缩放过渡。 */
    Scale("scale"),
    ;

    companion object {
        /** 按 id 取样式；未知 id（含已废弃的旧值）回退 [Standard]。 */
        fun fromId(id: String?): TransitionStyle =
            entries.firstOrNull { it.id == id } ?: Standard
    }
}
