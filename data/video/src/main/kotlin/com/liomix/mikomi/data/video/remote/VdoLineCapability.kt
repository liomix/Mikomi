package com.liomix.mikomi.data.video.remote

/**
 * 一条线路**实际**怎么出流。这是观察结果，不是规则声明。
 *
 * 同一份规则下的不同线路可以落在不同结论上：A 线明文直链、B 线加密配置、C 线跨站播放器。
 * 所以结论必须按线路记（[VdoLineRef]），不能按源记。
 *
 * 只留两种结论：拿到了直链、或者必须渲染。第三态用 `null` 表示「还没观察过」，
 * 这样调用方不需要再写一个 `Unknown` 分支去处理「不知道」——不知道就等于没缓存。
 */
sealed interface VdoLineCapability {

    /**
     * 这条线路能出直链。
     *
     * 刻意**不存请求头**：Cookie 会过期、Referer 与具体页面有关，
     * 存下来的头下次用只会制造 403。命中缓存后重新算一遍头，成本可以忽略。
     */
    data class Direct(
        val url: String,
        val kind: VdoStreamKind,
    ) : VdoLineCapability

    /** 这条线路必须把页面渲染出来（站点播放器自己取地址）。[note] 是下这个结论的原因。 */
    data class Rendered(val note: String) : VdoLineCapability
}
