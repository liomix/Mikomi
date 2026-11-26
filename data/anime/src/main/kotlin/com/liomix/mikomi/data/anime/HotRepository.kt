package com.liomix.mikomi.data.anime

import com.liomix.mikomi.core.model.AniRpbHotItem
import com.liomix.mikomi.core.model.anime.RankRegion
import kotlinx.coroutines.flow.Flow

/**
 * 热播榜：AniRpb 分类整榜（sort=hottest），日漫 / 国漫两榜。
 *
 * 与 [RankRepository] 的区别只在缓存形态：热播榜没有自己的业务表，数据整块存进
 * [com.liomix.mikomi.core.datastore.cache.CacheStore]，所以这里是**一份 JSON 快照**，
 * 而不是 Room 里的行。
 */
interface HotRepository {

    /** 观察某一产地榜单；还没有缓存时为空列表。 */
    fun observeHot(region: RankRegion): Flow<List<AniRpbHotItem>>

    /** 进页面时调用：刚拉过就跳过。 */
    suspend fun refreshHotIfStale()

    /** 无条件拉取两榜；失败时保留已缓存的内容。 */
    suspend fun refreshHot()
}
