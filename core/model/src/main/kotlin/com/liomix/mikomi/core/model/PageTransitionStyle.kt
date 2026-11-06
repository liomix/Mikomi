package com.liomix.mikomi.core.model

/**
 * 页面动效：整页进出时的过渡。
 *
 * 与 [TransitionStyle]（封面动效）是**两件互不相关的事**，所以各存一条偏好、各占一个选项组：
 * 封面飞行由 `HeroFlightController` 画在页面之上，页面怎么动都不会影响它的落点；
 * 反过来，封面选「旋转」也不该强迫页面跟着转。混在一个选项里只能二选一，是错的。
 *
 * `id` 持久化到 DataStore（`page_transition`）。
 */
enum class PageTransitionStyle(
    val id: String,
) {
    /**
     * 推进：新页从右 48dp 处滑入、内容淡入；返回时被关闭的页右移淡出，目标页直接可见。
     *
     * 这是 Telegram `ActionBarLayout` 的手感，也是默认值。
     */
    Push("push"),

    /**
     * 淡入：新页原地交叉淡入、不做位移。
     *
     * 适合在意「页面不要横向晃动」的人 —— 内容更新类页面（设置、榜单）读起来更安静。
     */
    Fade("fade"),

    /** 无：直接切换，不播放任何转场。 */
    None("none"),
    ;

    companion object {
        /** 按 id 取样式；未知 id（含历史脏值）回退 [Push]。 */
        fun fromId(id: String?): PageTransitionStyle =
            entries.firstOrNull { it.id == id } ?: Push
    }
}
