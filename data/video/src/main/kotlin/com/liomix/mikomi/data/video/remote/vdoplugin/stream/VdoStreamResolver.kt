package com.liomix.mikomi.data.video.remote.vdoplugin.stream

import com.liomix.mikomi.data.video.remote.VdoLineCapability
import com.liomix.mikomi.data.video.remote.VdoLineRef
import com.liomix.mikomi.data.video.remote.VdoStream
import com.liomix.mikomi.data.video.remote.vdoplugin.rule.VdoRuleDocument

/**
 * 一种出流办法。
 *
 * 这是整个设计的支点：**新增一种出流方式 = 新增一个实现类**，规则格式不动、播放层不动。
 * 想支持「站点自带的 JSON 接口」就加一个 `VdoJsonApiResolver`，想支持「注入 JS 调它自己的
 * 取址函数」就加一个 `VdoJsBridgeResolver`，谁也不用改谁。
 *
 * 三个约定：
 * - [order] 小的先试，顺序是显式的，不搞评分。评分看着聪明，实际调起来没人说得清
 *   为什么这次跳过了某一个。
 * - 拿不到地址要返回 [VdoResolveOutcome.NotApplicable]，**不要抛异常**。抛异常会中断整条链，
 *   而「这条办法不适用」恰恰是继续试下一条的理由。
 * - 真正抛异常的只有两种情况：规则坏了、或者网络层彻底不通。这两种后面也不用再试了。
 */
interface VdoStreamResolver {

    /** 日志与 trace 里的名字。用类名容易读，例如 `StaticHtml`、`EncryptedConfig`。 */
    val name: String

    /** 越小越先试。 */
    val order: Int

    suspend fun resolve(request: VdoResolveRequest): VdoResolveOutcome
}

/**
 * 一次解析要用到的全部上下文。
 *
 * resolver 只看得见它，**永远不认识具体站点**——不认识域名、不认识规则名。
 * 一旦某个 resolver 开始判断「这是不是 7sefun」，它就变成了特判，插件一多就会失控。
 *
 * @property cached 这条线路上次的结论；没观察过就是 null。[VdoCachedDirectResolver] 之外的
 *   resolver 一般不看它。
 * @property pages 本次解析的页面缓存。`StaticHtml` 抓过的页面，`EncryptedConfig` 直接用，
 *   省掉一次重复请求。
 */
class VdoResolveRequest(
    val rule: VdoRuleDocument,
    val line: VdoLineRef,
    val pageUrl: String,
    val cached: VdoLineCapability?,
    val pages: VdoPageFetcher,
)

/** 一个 resolver 的结论。 */
sealed interface VdoResolveOutcome {

    /** 拿到了能播的地址，链到此结束。 */
    data class Playable(val stream: VdoStream) : VdoResolveOutcome

    /**
     * 只能渲染，链也到此结束。
     *
     * 短路是刻意的：一旦确定「必须让站点播放器自己取地址」，再试其它静态办法都是浪费时间。
     */
    data class NeedRender(val reason: String) : VdoResolveOutcome

    /** 这条办法不适用 / 没找到，交给下一条。 */
    data object NotApplicable : VdoResolveOutcome
}
