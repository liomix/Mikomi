package com.liomix.mikomi.core.model

/**
 * 一个插件源（一份 JSON 规则）。
 *
 * 规则文件本身描述「某个视频站怎么搜、怎么取剧集」，本类只把列表要展示的几项抽出来，
 * 外加**原文** [json]：编辑、分享、测试三处都要用原文，与其各自去磁盘再读一次，
 * 不如让仓库一次读全——规则只有几百字节，重复读的 IO 成本远高于这点内存。
 *
 * [name] 同时也是稳定标识：仓库里存成 `<name>.json`，删除/排序/查重都靠它。
 * 因此改名的「编辑」等于换一个文件，由仓库负责把旧文件删掉。
 *
 * 列表顺序即搜索优先级，顺序由仓库返回的列表下标表达，本类不另存序号。
 */
data class PluginRule(
    /** 显示名兼文件名，对应 json 的 `name`。 */
    val name: String,
    /** 规则版本，对应 json 的 `version`；缺省时为空串。 */
    val version: String,
    /** 作者，对应 json 的 `author`；多数规则不写，空串表示不显示。 */
    val author: String,
    /** 站点根地址，对应 json 的 `baseURL`；列表上只显示 host。 */
    val baseUrl: String,
    /** 停用的源保留在列表里但不参与搜索——这是「不删但关掉」的唯一手段。 */
    val enabled: Boolean,
    /** 规则 JSON 原文，编辑/分享/测试直接用它，避免二次读盘。 */
    val json: String,
) {
    /** 稳定标识，与 [name] 同值。 */
    val id: String get() = name
}
