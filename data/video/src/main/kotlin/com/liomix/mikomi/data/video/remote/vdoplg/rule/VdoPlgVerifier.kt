package com.liomix.mikomi.data.video.remote.vdoplg.rule

import com.liomix.mikomi.data.video.VdoEpisodeRoad
import com.liomix.mikomi.data.video.VdoFailureReason
import com.liomix.mikomi.data.video.VdoLineRef
import com.liomix.mikomi.data.video.VdoSearchHit
import com.liomix.mikomi.data.video.VdoSourceException
import com.liomix.mikomi.data.video.VdoStream
import com.liomix.mikomi.data.video.VdoStreamPlan
import com.liomix.mikomi.data.video.remote.vdoplg.VdoPlgEngine
import com.liomix.mikomi.data.video.remote.vdoplg.stream.VdoPlgPlanRequest
import com.liomix.mikomi.data.video.remote.vdoplg.stream.VdoPlgStreamPlanner
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/** 一次测试的三步。顺序即执行顺序：上一步不成就没法进行下一步。 */
enum class VdoPlgTestStage {
    /** 按 `searchURL` + `searchList` 搜一次，看能不能拿到片子。 */
    Search,

    /** 进第一部片的详情页，按 `chapterRoads`/`chapterResult` 取剧集。 */
    Episodes,

    /** 进第一集的播放页，把播放地址真拉一次。 */
    Stream,
}

/**
 * 一步的结论。
 *
 * 只有三态：过了、没过（带原因与现场细节）、没跑到（前面已经失败）。
 * 不打分、不算权重——用户只需要知道「能不能看」。
 */
sealed interface VdoPlgStageResult {

    /** 通过了。详情由报告里的数据（命中数、线路、流地址）来展示，不在这里重复一份。 */
    data object Passed : VdoPlgStageResult

    /** @property detail 现场细节：字段名、HTTP 码、出问题的地址。 */
    data class Failed(val reason: VdoFailureReason, val detail: String? = null) : VdoPlgStageResult

    /** 没跑到：前面那步已经失败，或者还没填关键词。 */
    data object Skipped : VdoPlgStageResult
}

/**
 * 一次测试的完整报告。
 *
 * 只留「剧集」和「出流」这两件真正要紧的事，外加它们的入口——搜索：
 * 搜索不通，剧集无从谈起；剧集拿不到，出流无从谈起。字段级体检不再单独列出，
 * 缺字段会直接在对应那一步以 [VdoFailureReason.RuleBroken] 暴露出来（detail 里就是字段名）。
 */
data class VdoPlgTestReport(
    val keyword: String = "",
    val search: VdoPlgStageResult = VdoPlgStageResult.Skipped,
    val episodes: VdoPlgStageResult = VdoPlgStageResult.Skipped,
    val stream: VdoPlgStageResult = VdoPlgStageResult.Skipped,
    /** 搜索命中，用于展示「搜到了什么」。 */
    val searchHits: List<VdoSearchHit> = emptyList(),
    /** 剧集线路（多线路站点会有多条）。 */
    val roads: List<VdoEpisodeRoad> = emptyList(),
    /** 出流结果，成功时才有。 */
    val streamInfo: VdoStream? = null,
) {

    /** 三步全过才算「能看」。 */
    val allPassed: Boolean
        get() = search is VdoPlgStageResult.Passed &&
            episodes is VdoPlgStageResult.Passed &&
            stream is VdoPlgStageResult.Passed

    /** 没过的步数，用于结论标签。 */
    val failedCount: Int
        get() = listOf(search, episodes, stream).count { it is VdoPlgStageResult.Failed }

    /** 剧集总数（所有线路相加）。 */
    val episodeCount: Int
        get() = roads.sumOf { it.episodes.size }
}

/**
 * 插件测试：**只测「剧集」和「出流」**。
 *
 * 一次跑完三步，任何一步失败就把原因摊开、后面的步骤标成跳过：
 * - 搜索：`searchURL`（`@keyword` 替换）→ 按 `searchList`/`searchName`/`searchResult` 解析；
 * - 剧集：取第一部片的详情页 → 按 `chapterRoads`/`chapterResult` 取剧集；
 * - 出流：取第一集的播放页 → 跑**不渲染**的那几级策略（静态解析、加密配置）→ 真拉开头 2KB。
 *
 * 出流这一步刻意不跑 WebView 那一级：测试要回答的是「这份源写得对不对」。
 * 如果把「反正能交给网页播放」也算通过，那每个源都会通过，测试就没意义了。
 *
 * 不缓存中间结果、也不复用上一步的 HTTP 响应：这三步在真实播放链路上本来就是三次独立请求，
 * 测试若走了捷径，测出来的就不是用户会遇到的那条路。
 */
@Singleton
class VdoPlgVerifier @Inject constructor(
    private val engine: VdoPlgEngine,
    private val planner: VdoPlgStreamPlanner,
) {

    /**
     * 跑一遍完整链路。
     *
     * @param keyword 搜索词。空白时不发任何请求，三步都标成跳过——没有目标就没法测。
     */
    suspend fun verify(ruleJson: String, keyword: String): VdoPlgTestReport {
        if (keyword.isBlank()) return VdoPlgTestReport()

        val hits = try {
            engine.search(ruleJson, keyword)
        } catch (e: CancellationException) {
            throw e
        } catch (e: VdoSourceException) {
            return VdoPlgTestReport(
                keyword = keyword,
                search = VdoPlgStageResult.Failed(e.reason, e.detail),
            )
        }
        if (hits.isEmpty()) {
            return VdoPlgTestReport(
                keyword = keyword,
                search = VdoPlgStageResult.Failed(VdoFailureReason.NoResults, "searchList"),
            )
        }

        val roads = try {
            engine.episodes(ruleJson, hits.first().url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: VdoSourceException) {
            return VdoPlgTestReport(
                keyword = keyword,
                search = VdoPlgStageResult.Passed,
                searchHits = hits,
                episodes = VdoPlgStageResult.Failed(e.reason, e.detail),
            )
        }
        val firstEpisode = roads.firstOrNull()?.episodes?.firstOrNull()
        if (firstEpisode == null) {
            return VdoPlgTestReport(
                keyword = keyword,
                search = VdoPlgStageResult.Passed,
                searchHits = hits,
                episodes = VdoPlgStageResult.Failed(VdoFailureReason.NoEpisodes, "chapterResult"),
            )
        }

        val stream = try {
            val request = VdoPlgPlanRequest(
                ruleJson = ruleJson,
                line = VdoLineRef(
                    sourceName = VdoPlgDocument.parse(ruleJson).name.ifBlank { "source" },
                    lineName = roads.first().name,
                    firstEpisodeUrl = roads.first().episodes.first().url,
                ),
                episodeUrl = firstEpisode.url,
            )
            (planner.probeDirect(request).plan as VdoStreamPlan.Native).stream
        } catch (e: CancellationException) {
            throw e
        } catch (e: VdoSourceException) {
            return VdoPlgTestReport(
                keyword = keyword,
                search = VdoPlgStageResult.Passed,
                searchHits = hits,
                episodes = VdoPlgStageResult.Passed,
                roads = roads,
                stream = VdoPlgStageResult.Failed(e.reason, e.detail),
            )
        }

        return VdoPlgTestReport(
            keyword = keyword,
            search = VdoPlgStageResult.Passed,
            episodes = VdoPlgStageResult.Passed,
            stream = VdoPlgStageResult.Passed,
            searchHits = hits,
            roads = roads,
            streamInfo = stream,
        )
    }
}
