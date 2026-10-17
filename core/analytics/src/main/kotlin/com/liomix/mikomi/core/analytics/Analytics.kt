package com.liomix.mikomi.core.analytics

/** 埋点事件（snake_case 命名，集中在单一 schema）。 */
data class AnalyticsEvent(
    val name: String,
    val params: Map<String, Any?> = emptyMap(),
)

/** 埋点事件名 schema。 */
object AnalyticsEvents {
    const val SCREEN_VIEW = "screen_view"
    const val ITEM_CLICK = "item_click"
    const val PLAY_START = "play_start"
}

/** 埋点上报接口，异步、失败静默限次重试。 */
interface AnalyticsTracker {
    fun track(event: AnalyticsEvent)
}
