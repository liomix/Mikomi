package com.liomix.mikomi.core.model

/**
 * 页面转场动画样式（对齐 Dart 版 `HeroAnimationStyle`）。
 *
 * 作用于页面本身；封面从列表飞向详情头部由 `HeroFlightController` 独立承担（画在页面之上的 overlay），
 * 两者互不影响——与 Dart 版「页面用 MaterialPageRoute、Hero 飞行在 Navigator overlay」结构一致。
 * `id` 持久化到 DataStore（`transition_style`）。
 *
 * 页面过渡只用**变换类**动画（缩放/淡入淡出）：slide 是布局位移，会让页面内元素上报的坐标带上位移，
 * 封面飞行拿不到正确终点。
 *
 * 说明：Compose 的 `EnterTransition` 没有旋转类转场（`rotateIn` / `rotateOut` 不存在），
 * 页面本身不做旋转；需要旋转效果只能做在飞行内容上。
 */
enum class TransitionStyle(
    val id: String,
) {
    /** 标准：新页 0.92 → 1 放大并淡入（对齐 Dart 版 `MaterialPageRoute` 的缩放过渡，默认）。 */
    Standard("standard"),

    /** 淡入淡出：新页淡入覆盖旧页。 */
    Fade("fade"),

    /** 缩放过渡：新页从 0.85 放大覆盖，比标准更明显。 */
    Scale("scale"),

    /**
     * 旋转：封面飞行时转一整圈并从小放大（对齐 Dart 版 `RotationTransition` + `ScaleTransition`）。
     *
     * 页面本身仍用标准缩放——Compose 没有旋转类页面转场，旋转做在飞行内容上。
     */
    Rotate("rotate"),
    ;

    companion object {
        /** 按 id 取样式；未知 id（含已废弃的旧值）回退 [Standard]。 */
        fun fromId(id: String?): TransitionStyle =
            entries.firstOrNull { it.id == id } ?: Standard
    }
}
