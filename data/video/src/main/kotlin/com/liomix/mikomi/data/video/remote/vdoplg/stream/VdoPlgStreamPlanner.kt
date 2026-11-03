package com.liomix.mikomi.data.video.remote.vdoplg.stream

import com.liomix.mikomi.data.video.VdoFailureReason
import com.liomix.mikomi.data.video.VdoLineCapability
import com.liomix.mikomi.data.video.VdoLineCapabilityStore
import com.liomix.mikomi.data.video.VdoLineRef
import com.liomix.mikomi.data.video.VdoPlanResult
import com.liomix.mikomi.data.video.VdoSourceException
import com.liomix.mikomi.data.video.VdoStream
import com.liomix.mikomi.data.video.VdoStreamPlan
import com.liomix.mikomi.data.video.remote.vdoplg.VdoPlgEngine
import com.liomix.mikomi.data.video.remote.vdoplg.rule.InvalidVdoPlgSourceException
import com.liomix.mikomi.data.video.remote.vdoplg.rule.VdoPlgDocument
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * 出流策略链的编排者。**播放层只跟它打交道**，不再问源「要不要 WebView」。
 *
 * 一次 [plan] 做的事：
 * 1. 解析源、取这条线路的能力缓存；
 * 2. 按 [VdoPlgStreamResolver.order] 依次问每个策略；
 * 3. 谁先给出「可播」或「只能渲染」就停，后面的不再试；
 * 4. 把结论写回能力缓存，并把每一步的结论汇成一条 trace。
 *
 * 三个刻意的取舍：
 * - **源里的布尔值不做判决**。它只影响 WebView 的 UA，以及最后报错时的措辞。
 *   让它当 gate 的后果已经见过了：作者一句 `useWebview: true`，能直链的线路也一并锁死在网页里。
 * - **缓存只是候选**。命中也必须再探一次，不验证就用等于赌。
 * - **失败要带 trace**。用户看到的是白屏，我们唯一能靠得住的信息就是「试过哪几条、各自什么结论」。
 */
@Singleton
class VdoPlgStreamPlanner @Inject constructor(
    private val engine: VdoPlgEngine,
    private val capabilityStore: VdoLineCapabilityStore,
    cachedDirectResolver: VdoPlgCachedDirectResolver,
    staticHtmlResolver: VdoPlgStaticHtmlResolver,
    encryptedConfigResolver: VdoPlgEncryptedConfigResolver,
    webRenderResolver: VdoPlgWebRenderResolver,
) {

    /** 顺序是显式的：数字小的先试。想插一条新策略，给它一个中间的数字即可。 */
    private val resolvers: List<VdoPlgStreamResolver> = listOf(
        cachedDirectResolver,
        staticHtmlResolver,
        encryptedConfigResolver,
        webRenderResolver,
    ).sortedBy { it.order }

    /**
     * 出流计划。拿不到直链时会返回 [VdoStreamPlan.Web]，而不是抛错。
     *
     * @throws VdoSourceException 源坏了、网络彻底不通，或所有策略都没结果。
     */
    suspend fun plan(request: VdoPlgPlanRequest): VdoPlanResult =
        execute(request, allowRender = true)

    /**
     * 只跑「不渲染也能拿到流」的那几条策略，给源测试面板用。
     *
     * 测试面板要回答的是「这份源写得对不对」，所以不能因为「反正能交给 WebView」就说通过——
     * 那样每个源都通过，测试就失去意义了。
     */
    suspend fun probeDirect(request: VdoPlgPlanRequest): VdoPlanResult =
        execute(request, allowRender = false)

    /** 网络层嗅探到了流：记成这条线路的直链结论，下次进来直接验证它。 */
    suspend fun rememberSniffed(line: VdoLineRef, stream: VdoStream) {
        capabilityStore.remember(line, VdoLineCapability.Direct(stream.url, stream.kind))
    }

    /** 直链播不动：记一次「与缓存不符」，连续两次会把缓存作废。 */
    suspend fun reportMismatch(line: VdoLineRef) {
        capabilityStore.reportMismatch(line)
    }

    /**
     * 丢掉一条线路的结论。
     *
     * 用户点「重试」时调用：缓存不参与的话，重试才会真的重新走一遍解析，
     * 否则只是把上次的结论再验一遍——站点改版了也发现不了。
     */
    suspend fun forget(line: VdoLineRef) {
        capabilityStore.forget(line)
    }

    /** 读一条线路的结论，只用于展示（例如在线路上标「直链 / 网页」）。 */
    suspend fun capabilityOf(line: VdoLineRef): VdoLineCapability? = capabilityStore.find(line)

    private suspend fun execute(request: VdoPlgPlanRequest, allowRender: Boolean): VdoPlanResult {
        // 契约是「只抛 VdoSourceException」：调用方（播放页、测试页）也只认这一种。
        // 格式异常若原样穿出去，那边的 catch 接不住，就变成一次崩溃——
        // 而它其实只是「这份 json 写坏了」，属于用户能自己修的一类。
        val rule = try {
            VdoPlgDocument.parse(request.ruleJson)
        } catch (e: InvalidVdoPlgSourceException) {
            throw VdoSourceException(VdoFailureReason.RuleBroken, e.field ?: e.reason.name, e)
        }
        val cached = capabilityStore.find(request.line)
        val steps = mutableListOf<String>()

        // **不做「上次只能渲染，这次直接交网页」的捷径**（曾经有过，已删）。
        //
        // 那条捷径省下一次页面抓取，代价却是：只要某条线路失败过一次，它就永远进网页，
        // 站点后来改好了、我们后来补了新的解析手段，都不会再被尝试到。
        // 用户看到的就是「点进去永远是网页」，而且怎么重试都没用——结论是落盘的。
        //
        // 现在的口径：每次出流都先老老实实跑一遍静态策略（同一页有 [VdoPlgPageFetcher] 缓存，
        // 多个 resolver 共用一次请求），真拿不到才交给 WebView。
        // `VdoLineCapability.Rendered` 只保留「这条线路是网页形态」这个事实，用于展示，不再当判决。

        val context = VdoPlgResolveRequest(
            rule = rule,
            line = request.line,
            pageUrl = request.episodeUrl,
            cached = cached,
            pages = VdoPlgPageFetcher(engine),
        )

        var firstFailure: VdoSourceException? = null

        for (resolver in resolvers) {
            val startedAt = System.currentTimeMillis()
            val outcome = try {
                resolver.resolve(context)
            } catch (e: CancellationException) {
                throw e
            } catch (e: VdoSourceException) {
                // 源坏 / 网络不通：记下来继续试下一条，最后再决定报哪个。
                firstFailure = firstFailure ?: e
                steps += "${resolver.name}:${e.reason}"
                continue
            }
            val elapsed = System.currentTimeMillis() - startedAt

            when (outcome) {
                is VdoPlgResolveOutcome.Playable -> {
                    steps += "${resolver.name}:ok(${outcome.stream.kind},${elapsed}ms)"
                    val reusedCache = cached is VdoLineCapability.Direct && cached.url == outcome.stream.url
                    if (!reusedCache) {
                        // 换了地址就覆盖缓存：站点改版后旧地址已经没用了，留着只会多一次探测。
                        capabilityStore.remember(
                            request.line,
                            VdoLineCapability.Direct(outcome.stream.url, outcome.stream.kind),
                        )
                    }
                    return VdoPlanResult(VdoStreamPlan.Native(outcome.stream), traceOf(steps))
                }

                is VdoPlgResolveOutcome.NeedRender -> {
                    steps += "${resolver.name}:render(${elapsed}ms)"
                    if (!allowRender) {
                        // 只测静态能力时，这里就是失败：不能拿「反正有 WebView」当通过。
                        throw VdoSourceException(VdoFailureReason.NeedWebView, outcome.reason)
                    }
                    // 一页都没打开过：「必须渲染」不是结论，是猜测。这时候交给 WebView
                    // 只会得到一张打不开的白页，不如把网络问题如实报出来。
                    if (context.pages.loadedPages == 0) {
                        val fatal = firstFailure
                        throw if (fatal != null) {
                            VdoSourceException(
                                reason = fatal.reason,
                                detail = listOfNotNull(fatal.detail, traceOf(steps)).joinToString(" | "),
                                cause = fatal,
                            )
                        } else {
                            VdoSourceException(VdoFailureReason.NetworkError, traceOf(steps))
                        }
                    }
                    capabilityStore.remember(
                        request.line,
                        VdoLineCapability.Rendered(outcome.reason),
                    )
                    return VdoPlanResult(
                        plan = VdoStreamPlan.Web(
                            pageUrl = request.episodeUrl,
                            userAgent = engine.userAgentOf(request.ruleJson),
                            reason = outcome.reason,
                        ),
                        trace = traceOf(steps),
                    )
                }

                VdoPlgResolveOutcome.NotApplicable -> {
                    steps += "${resolver.name}:skip(${elapsed}ms)"
                }
            }
        }

        // 走到这里说明一条都没成。
        if (cached is VdoLineCapability.Direct) capabilityStore.reportMismatch(request.line)
        val failure = firstFailure
        throw if (failure != null) {
            VdoSourceException(
                reason = failure.reason,
                detail = listOfNotNull(failure.detail, traceOf(steps)).joinToString(" | "),
                cause = failure,
            )
        } else {
            VdoSourceException(
                reason = if (rule.prefersSitePlayer) VdoFailureReason.NeedWebView else VdoFailureReason.NoStream,
                detail = traceOf(steps),
            )
        }
    }

    private fun traceOf(steps: List<String>): String =
        steps.joinToString(separator = " → ").ifBlank { "no-resolver" }
}
