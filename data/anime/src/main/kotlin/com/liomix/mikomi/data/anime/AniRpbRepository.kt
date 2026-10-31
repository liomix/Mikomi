package com.liomix.mikomi.data.anime

import com.liomix.mikomi.core.model.AniRpbHotItem
import com.liomix.mikomi.core.model.RankRegion

/**
 * AniRpb 特殊数据源。
 *
 * 与 Bangumi / AniList 不同，AniRpb 的接口必须经官方 `libzhuque.so` 的本地帧
 * 传输层访问；这里只暴露应用需要的只读能力。
 */
interface AniRpbRepository {

    /**
     * 拉取日漫 / 国漫热播榜。
     *
     * 频道 id 启动时通过 `type.listActive` 动态解析；接口失败会向上抛出，
     * 由 UI 显示可重试错误态。
     */
    suspend fun fetchHotRanking(region: RankRegion): List<AniRpbHotItem>
}
