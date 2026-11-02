package com.liomix.mikomi.data.video.remote.vdoplugin

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/** 一次测试的三步。顺序即执行顺序：上一步不成就没法进行下一步。 */
enum class VdoTestStage {
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
sealed interface VdoStageResult {

    /** 通过了。详情由报告里的数据（命中数、线路、流地址）来展示，不在这里重复一份。 */
    data object Passed : VdoStageResult

    /** @property detail 现场细节：字段名、HTTP 码、出问题的地址。 */
    data class Failed(val reason: VdoTestReason, val detail: String? = null) : VdoStageResult

    /** 没跑到：前面那步已经失败，或者还没填关键词。 */
    data object Skipped : VdoStageResult
}

/**
 * 一次测试的完整报告。
 *
 * 只留「剧集」和「出流」这两件真正要紧的事，外加它们的入口——搜索：
 * 搜索不通，剧集无从谈起；剧集拿不到，出流无从谈起。字段级体检不再单独列出，
 * 缺字段会直接在对应那一步以 [VdoTestReason.RuleBroken] 暴露出来（detail 里就是字段名）。
 */
data class VdoRuleTestReport(
    val keyword: String = "",
    val search: VdoStageResult = VdoStageResult.Skipped,
    val episodes: VdoStageResult = VdoStageResult.Skipped,
    val stream: VdoStageResult = VdoStageResult.Skipped,
    /** 搜索命中，用于展示「搜到了什么」。 */
    val searchHits: List<VdoSearchHit> = emptyList(),
    /** 剧集线路（多线路站点会有多条）。 */
    val roads: List<VdoEpisodeRoad> = emptyList(),
    /** 出流结果，成功时才有。 */
    val streamInfo: VdoStream? = null,
) {

    /** 三步全过才算「能看」。 */
    val allPassed: Boolean
        get() = search is VdoStageResult.Passed &&
            episodes is VdoStageResult.Passed &&
            stream is VdoStageResult.Passed

    /** 没过的步数，用于结论标签。 */
    val failedCount: Int
        get() = listOf(search, episodes, stream).count { it is VdoStageResult.Failed }

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
 * - 出流：取第一集的播放页 → 翻 iframe 与播放器配置 → 真拉开头 2KB 确认是 HLS/mp4。
 *
 * 不缓存中间结果、也不复用上一步的 HTTP 响应：这三步在真实播放链路上本来就是三次独立请求，
 * 测试若走了捷径，测出来的就不是用户会遇到的那条路。
 */
@Singleton
class VdoPluginRuleVerifier @Inject constructor(
    private val engine: VdoPluginEngine,
) {

    /**
     * 跑一遍完整链路。
     *
     * @param keyword 搜索词。空白时不发任何请求，三步都标成跳过——没有目标就没法测。
     */
    suspend fun verify(ruleJson: String, keyword: String): VdoRuleTestReport {
        if (keyword.isBlank()) return VdoRuleTestReport()

        val hits = try {
            engine.search(ruleJson, keyword)
        } catch (e: CancellationException) {
            throw e
        } catch (e: VdoPluginException) {
            return VdoRuleTestReport(
                keyword = keyword,
                search = VdoStageResult.Failed(e.reason, e.detail),
            )
        }
        if (hits.isEmpty()) {
            return VdoRuleTestReport(
                keyword = keyword,
                search = VdoStageResult.Failed(VdoTestReason.NoResults, "searchList"),
            )
        }

        val roads = try {
            engine.episodes(ruleJson, hits.first().url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: VdoPluginException) {
            return VdoRuleTestReport(
                keyword = keyword,
                search = VdoStageResult.Passed,
                searchHits = hits,
                episodes = VdoStageResult.Failed(e.reason, e.detail),
            )
        }
        val firstEpisode = roads.firstOrNull()?.episodes?.firstOrNull()
        if (firstEpisode == null) {
            return VdoRuleTestReport(
                keyword = keyword,
                search = VdoStageResult.Passed,
                searchHits = hits,
                episodes = VdoStageResult.Failed(VdoTestReason.NoEpisodes, "chapterResult"),
            )
        }

        val stream = try {
            engine.stream(ruleJson, firstEpisode.url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: VdoPluginException) {
            return VdoRuleTestReport(
                keyword = keyword,
                search = VdoStageResult.Passed,
                searchHits = hits,
                episodes = VdoStageResult.Passed,
                roads = roads,
                stream = VdoStageResult.Failed(e.reason, e.detail),
            )
        }

        return VdoRuleTestReport(
            keyword = keyword,
            search = VdoStageResult.Passed,
            episodes = VdoStageResult.Passed,
            stream = VdoStageResult.Passed,
            searchHits = hits,
            roads = roads,
            streamInfo = stream,
        )
    }
}
