package com.liomix.mikomi.data.anime.remote.bangumi

import android.util.Log
import com.liomix.mikomi.core.common.dispatcher.IoDispatcher
import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.model.anime.Search
import com.liomix.mikomi.data.anime.remote.bangumi.client.BangumiApi
import com.liomix.mikomi.data.anime.remote.bangumi.client.BangumiTrendApi
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiCreatorKind
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiDto
import com.liomix.mikomi.core.model.anime.AnimeComment
import com.liomix.mikomi.core.model.anime.AnimeDetail
import com.liomix.mikomi.core.model.anime.CreatorComment
import com.liomix.mikomi.core.model.anime.CreatorDetail
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiSearchDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Bangumi 数据源的**唯一出口**：对外只给领域模型，不给 DTO。
 *
 * ## 边界
 *
 * ```
 * client ──▶ DTO ──▶ mapper ──▶ 领域模型（本类返回的东西）
 * ```
 *
 * DTO 与 mapper 是这一层的**内部细节**。上层不需要知道 Bangumi 用 `name_cn` 还是
 * `nameCN`，也不需要知道详情接口叫 `subject` —— 那些都是上游的术语，泄漏出去就会在
 * Repository、ViewModel 甚至 UI 里冒出「subject」这种词。
 *
 * ## 负责 / 不负责
 *
 * 负责：选接口、切 IO、把 DTO 转成领域模型、滤掉非动画条目。
 * 不负责：排序策略、分页游标、缓存、重试 —— 那是 Repository 的判断。
 *
 * 不 try/catch：异常原样上抛。在数据源里吞掉异常，上层就分不清「没有数据」和「请求失败」。
 */
@Singleton
internal class BangumiSource @Inject constructor(
    private val api: BangumiApi,
    private val trendApi: BangumiTrendApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * 关键词搜索。
     *
     * 空关键词直接返回空结果、不发请求：Bangumi 对空 `keyword` 会返回一堆无关内容，
     * 而多这一次请求只会让联想框闪一下。
     *
     * [Search.rawCount] 取过滤**前**的条数：分页游标按服务端返回的位置推进。
     */
    suspend fun search(
        keyword: String,
        sort: String,
        limit: Int,
        offset: Int = 0,
    ): Search = withContext(ioDispatcher) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return@withContext Search()

        val response = api.search(
            request = BangumiSearchDto(keyword = trimmed, sort = sort),
            limit = limit,
            offset = offset,
        )
        Search(
            items = response.data.onlyAnime().map { it.toDomain() },
            rawCount = response.data.size,
        )
    }

    /** 输入联想：只要条目，不要分页信息。 */
    suspend fun suggest(keyword: String, limit: Int): List<Anime> =
        search(keyword = keyword, sort = SORT_MATCH, limit = limit).items

    /**
     * 条目详情，连同角色与职员一起给。
     *
     * 三个接口并行取：详情页打开时这三块都要，串行会让首屏依次等三次
     * （简介出来 → 角色转圈 → 职员转圈）。
     */
    suspend fun detail(id: Int): AnimeDetail = withContext(ioDispatcher) {
        coroutineScope {
            val detailDeferred = async { api.getSubject(id).toDetail() }
            val charactersDeferred = async {
                api.fetchSubjectCharacters(id).mapNotNull { it.toDetailCharacter() }
            }
            val staffDeferred = async {
                // 制作人员来自另一个域名（next.bgm.tv），它挂了不该让整页详情不可用 ——
                // 详情与角色照常显示，只是「制作」那一栏空着。
                try {
                    trendApi.fetchSubjectStaff(id).data
                        // 报文没有 kind 字段，DTO 默认 Character —— 这里必须显式标成 Staff。
                        .map { it.copy(kind = BangumiCreatorKind.Staff) }
                        .mapNotNull { it.toDetailStaff() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "拉制作人员失败，详情页照常显示（staff 留空）", e)
                    emptyList()
                }
            }
            detailDeferred.await().copy(
                characters = charactersDeferred.await(),
                staff = staffDeferred.await(),
            )
        }
    }

    /** 首页轮播 / 首页列表：Next 的趋势榜，已按热度降序。 */
    suspend fun trending(limit: Int, offset: Int = 0): List<Anime> = withContext(ioDispatcher) {
        trendApi.fetchTrending(type = TYPE_ANIME, limit = limit, offset = offset)
            .data
            .map { it.toDomain() }
    }

    /** 条目评论（分页）。 */
    suspend fun comments(subjectId: Int, limit: Int, offset: Int = 0): List<AnimeComment> =
        withContext(ioDispatcher) {
            trendApi.fetchSubjectComments(subjectId = subjectId, limit = limit, offset = offset)
                .data
                .mapNotNull { it.toAnimeComment() }
        }

    /** 主创详情：角色或人物（[isCharacter] 选边，两个接口形状不同但产物相同）。 */
    suspend fun creatorDetail(id: Int, isCharacter: Boolean): CreatorDetail =
        withContext(ioDispatcher) {
            if (isCharacter) {
                trendApi.fetchCharacterDetail(id).toCreatorDetail()
            } else {
                trendApi.fetchPersonDetail(id).toCreatorDetail()
            }
        }

    /**
     * 主创的评论。
     *
     * 角色与人物是两个接口、返回同一种结构，用一个布尔开关选边 ——
     * 「角色走这个、人物走那个」这个知识属于数据源，不该让调用方记住。
     */
    suspend fun creatorComments(id: Int, isCharacter: Boolean): List<CreatorComment> =
        withContext(ioDispatcher) {
            val raw = if (isCharacter) {
                trendApi.fetchCharacterComments(id)
            } else {
                trendApi.fetchPersonComments(id)
            }
            raw.mapNotNull { it.toCreatorComment() }
        }

    private companion object {
        const val TAG = "BangumiSource"

        const val SORT_MATCH = "match"

        /** 条目类型：2 = 动画。趋势接口也要它，否则会混进书籍与游戏。 */
        const val TYPE_ANIME = 2
    }
}

/**
 * 只保留动画条目。
 *
 * 放在数据源里而不是上层：搜索请求虽然带了 `type = [2]`，但服务端并不总遵守。
 * 这一层兜住它，上层的每个调用点就不必各自再滤一遍 —— 也保证了「本数据源只产出动画」
 * 这条约定是真的。
 */
private fun List<BangumiDto>.onlyAnime(): List<BangumiDto> =
    filter { it.type == TYPE_ANIME }

/** 条目类型：2 = 动画（Bangumi 的 type 枚举）。 */
private const val TYPE_ANIME = 2
