package com.liomix.mikomi.core.model

/**
 * 封面动效：列表封面飞向详情封面时的**内容变换**（只作用于封面，与页面过渡无关）。
 *
 * 飞行本体由 `HeroFlightController` 画在页面之上的 overlay 里，
 * 因此页面怎么缩放/淡入都不会影响它。
 * `id` 持久化到 DataStore（`transition_style`）。
 */
enum class TransitionStyle(
    val id: String,
) {
    /** 平移：封面按原样从列表位置飞到详情位置，飞行途中不做额外变换（默认）。 */
    Translate("translate"),

    /** 旋转：飞行中转满一圈，并从 0 放大到原始尺寸（对齐 Dart 版 RotationTransition）。 */
    Rotate("rotate"),
    ;

    companion object {
        /** 按 id 取样式；未知 id（含已废弃的旧值 standard / fade / scale）回退 [Translate]。 */
        fun fromId(id: String?): TransitionStyle =
            entries.firstOrNull { it.id == id } ?: Translate
    }
}
