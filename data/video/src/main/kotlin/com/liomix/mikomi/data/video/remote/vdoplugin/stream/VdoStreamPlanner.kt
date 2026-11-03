package com.liomix.mikomi.data.video.remote.vdoplugin.stream

import com.liomix.mikomi.data.video.remote.VdoFailureReason
import com.liomix.mikomi.data.video.remote.VdoLineCapability
import com.liomix.mikomi.data.video.remote.VdoLineCapabilityStore
import com.liomix.mikomi.data.video.remote.VdoLineRef
import com.liomix.mikomi.data.video.remote.VdoPlanResult
import com.liomix.mikomi.data.video.remote.VdoSourceException
import com.liomix.mikomi.data.video.remote.VdoStream
import com.liomix.mikomi.data.video.remote.VdoStreamPlan
import com.liomix.mikomi.data.video.remote.vdoplugin.VdoPluginEngine
import com.liomix.mikomi.data.video.remote.vdoplugin.rule.VdoRuleDocument
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * 出流策略链的编排者。**播放层只跟它打交道**，不再问规则「要不要 WebView」。
 *
 * 一次 [plan] 做的事：
 * 1. 解析规则、取这条线路的能力缓存；
 * 2. 按 [VdoStreamResolver.order] 依次问每个策略；
 * 3. 谁先给出「可播」或「只能渲染」就停，后面的不再试；
 * 4. 把结论写回能力缓存，并把每一步的结论汇成一条 trace。
 *
 * 三个刻意的取舍：
 * - **规则里的布尔值不做判决**。它只影响 WebView 的 UA，以及最后报错时的措辞。
 *   让它当 gate 的后果已经见过了：作者一句 `useWebview: true`，能直链的线路也一并锁死在网页里。
 * - **缓存只是候选**。命中也必须再探一次，不验证就用等于赌。
 * - **失败要带 trace**。用户看到的是白屏，我们唯一能靠得住的信息就是「试过哪几条、各自什么结论」。
 */
@Singleton
class VdoStreamPlanner @Inject constructor(
    private val engine: VdoPluginEngine,
    private val capabilityStore: VdoLineCapabilityStore,
    cachedDirectResolver: VdoCachedDirectResolver,
    staticHtmlResolver: VdoStaticHtmlResolver,
    encryptedConfigResolver: VdoEncryptedConfigResolver,
    webRenderResolver: VdoWebRenderResolver,
) {

    /** 顺序是显式的：数字小的先试。想插一条新策略，给它一个中间的数字即可。 */
    private val resolvers: List<VdoStreamResolver> = listOf(
        cachedDirectResolver,
        staticHtmlResolver,
        encryptedConfigResolver,
        webRenderResolver,
    ).sortedBy { it.order }

    /**
     * 出流计划。拿不到直链时会返回 [VdoStreamPlan.Web]，而不是抛错。
     *
     * @throws VdoSourceException 规则坏了、网络彻底不通，或所有策略都没结果。
     */
    suspend fun plan(request: VdoPlanRequest): VdoPlanResult =
        execute(request, allowRender = true)

    /**
     * 只跑「不渲染也能拿到流」的那几条策略，给规则测试面板用。
     *
     * 测试面板要回答的是「这条规则写得对不对」，所以不能因为「反正能交给 WebView」就说通过——
     * 那样每条规则都通过，测试就失去意义了。
     */
    suspend fun probeDirect(request: VdoPlanRequest): VdoPlanResult =
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

    private suspend fun execute(request: VdoPlanRequest, allowRender: Boolean): VdoPlanResult {
        val rule = VdoRuleDocument.parse(request.ruleJson)
        val cached = capabilityStore.find(request.line)
        val steps = mutableListOf<String>()

        // 上次已经确认「这条线路只能渲染」：直接复用结论，省掉抓页面与解配置的两轮请求。
        // 只有 plan 走这条捷径——[probeDirect] 是规则测试，必须真的跑一遍静态解析。
        if (allowRender && cached is VdoLineCapability.Rendered) {
            steps += "CachedRender:hit"
            return VdoPlanResult(
                plan = VdoStreamPlan.Web(
                    pageUrl = request.episodeUrl,
                    userAgent = engine.userAgentOf(request.ruleJson),
                    reason = cached.note,
                ),
                trace = traceOf(steps),
            )
        }

        val context = VdoResolveRequest(
            rule = rule,
            line = request.line,
            pageUrl = request.episodeUrl,
            cached = cached,
            pages = VdoPageFetcher(engine),
        )

        var firstFailure: VdoSourceException? = null

        for (resolver in resolvers) {
            val startedAt = System.currentTimeMillis()
            val outcome = try {
                resolver.resolve(context)
            } catch (e: CancellationException) {
                throw e
            } catch (e: VdoSourceException) {
                // 规则坏 / 网络不通：记下来继续试下一条，最后再决定报哪个。
                firstFailure = firstFailure ?: e
                steps += "${resolver.name}:${e.reason}"
                continue
            }
            val elapsed = System.currentTimeMillis() - startedAt

            when (outcome) {
                is VdoResolveOutcome.Playable -> {
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

                is VdoResolveOutcome.NeedRender -> {
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

                VdoResolveOutcome.NotApplicable -> {
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
