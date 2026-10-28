package com.liomix.mikomi.core.model

/**
 * 页面动效：新旧两页之间的过渡方式（与封面飞行相互独立）。
 *
 * 只用**变换类**动画（缩放 / 透明度）：slide 是布局位移，会让页面内元素上报的坐标带上位移，
 * 封面飞行（画在页面之上的 overlay）就拿不到正确的终点。
 * `id` 持久化到 DataStore（`page_transition_style`）。
 */
enum class PageTransitionStyle(
    val id: String,
) {
    /** 标准：新页 0.88 → 1 放大并淡入（Material 的 zoom 观感）。 */
    Standard("standard"),

    /** 溶解：旧页淡出、新页淡入的交叉溶解，无位移无缩放。 */
    Fade("fade"),

    /** 弹入：新页从 0.55 尺寸带回弹地放大。 */
    Scale("scale"),

    /** 无：页面本身不做过渡，只保留封面飞行。 */
    None("none"),
    ;

    companion object {
        /** 按 id 取样式；未知 id（含已废弃的旧值）回退 [Standard]。 */
        fun fromId(id: String?): PageTransitionStyle =
            entries.firstOrNull { it.id == id } ?: Standard
    }
}
