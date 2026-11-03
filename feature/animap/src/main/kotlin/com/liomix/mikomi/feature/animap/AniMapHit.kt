package com.liomix.mikomi.feature.animap

import kotlinx.serialization.Serializable

/**
 * 一次搜索的完整回包。
 *
 * 数据不是走 HTTP 接口拿的，而是从地图页面自己的内存里读出来的：
 * 站点把整个应用对象挂在了 `window.mapApp` 上（`window.mapApp = b`），
 * 里面的 `bangumis` 就是当前地图掌握的全部作品（每部带 `points` 取景点）。
 *
 * @param ready 页面是否已经就绪（地图数据未到位时不是「没搜到」，而是「还不能搜」）
 * @param total 命中总数，界面只展示前若干条，这个数字用于提示「还有更多」
 * @param hits 前若干条命中
 */
@Serializable
data class AniMapSearchPayload(
    val ready: Boolean = false,
    val total: Int = 0,
    val hits: List<AniMapHit> = emptyList(),
)

/**
 * 一条搜索命中，三选一：
 *
 * - `city`：城市。站点没有这一类（它只把城市名当关键词去筛作品），
 *   这里补上是因为「搜城市」最自然的期望是地图移过去，而不是列一屏作品。
 * - `point`：具体取景地标。点击直接落到这个点的坐标。
 * - `bangumi`：整部作品。点击缩放到该作品所有取景地的范围。
 *
 * [id] 用来在页面里现取对象（作品 id / 地标点 id），[index] 只作列表 key。
 * **不要用下标取对象**：`window.__mikomiHits` 是搜索那一刻的快照，页面一旦重建
 * （SPA 换路由、地图数据分片重新灌入）下标就和界面上的列表对不上了。
 */
@Serializable
data class AniMapHit(
    val kind: String = KIND_BANGUMI,
    /** 作品 id 或地标点 id；城市条目为空串。 */
    val id: String = "",
    /** 在命中列表里的位置，仅用于列表项 key。 */
    val index: Int = -1,
    /** 主标题：作品名 / 地标名 / 城市名。 */
    val title: String = "",
    /** 副标题：原名，或地标所属的作品名。 */
    val subtitle: String = "",
    /** 所属城市。 */
    val city: String = "",
    /** 封面绝对地址（地标用其实拍图），可能为空。 */
    val cover: String = "",
    /** 作品收录的取景地数量（城市 / 地标恒为 0）。 */
    val points: Int = 0,
) {
    /** 是否指向一个具体取景地标。 */
    val isSpot: Boolean get() = kind == KIND_POINT

    /** 是否是城市条目。 */
    val isCity: Boolean get() = kind == KIND_CITY

    companion object {
        const val KIND_CITY = "city"
        const val KIND_POINT = "point"
        const val KIND_BANGUMI = "bangumi"
    }
}
