package com.liomix.mikomi.data.video.remote.vdoplg.stream

import com.liomix.mikomi.data.video.VdoLineRef

/**
 * 请求一次出流计划。
 *
 * 为什么只有它留在插件源这边：它带着 `ruleJson`——**这种 JSON 格式是插件源独有**的东西。
 * 出流的结果（`VdoStreamPlan`）是三大源共用的契约，
 * 请求则不是。
 *
 * @property ruleJson 源正文。这里刻意传原文而不是解析结果：一份源就几百字节，
 *   每次重新解析的成本远低于「解析结果与磁盘上的源不一致」这种问题。
 * @property line 线路身份，决定能力缓存读写哪一条。
 * @property episodeUrl 这一集的播放页地址。
 */
data class VdoPlgPlanRequest(
    val ruleJson: String,
    val line: VdoLineRef,
    val episodeUrl: String,
)
