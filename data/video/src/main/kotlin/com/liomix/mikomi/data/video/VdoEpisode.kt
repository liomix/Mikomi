package com.liomix.mikomi.data.video

/** 一集（或一个播放源）。 */
data class VdoEpisode(val name: String, val url: String)

/**
 * 一条线路上的全部剧集。
 *
 * 「线路」是站点自己的概念：同一条规则下常常有 2~4 条线路，清晰度与可用性各不相同，
 * 各条线路的解析方式也可能完全不同（见 [VdoLineCapability]）。
 */
data class VdoEpisodeRoad(val name: String, val episodes: List<VdoEpisode>)
