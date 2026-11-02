package com.liomix.mikomi.feature.settings.plugin

import androidx.annotation.StringRes
import com.liomix.mikomi.feature.settings.R

/**
 * 一个插件源。
 *
 * 「插件源」特指**一份 JSON 规则文件**：它描述某个视频站怎么搜索、怎么取剧集，
 * 名字、版本、站点地址都写在文件里。所以这里的字段是「从 json 里读出来的元信息」，
 * 不是用户手填的配置项——UI 只展示，不编辑（编辑走规则编辑器，尚未实现）。
 *
 * 与番剧元数据源（Bangumi 等）无关：那些是 App 内置的固定接口，不走插件。
 *
 * 列表**顺序即搜索优先级**，顺序由 [PluginUiState.sources] 的下标表达，条目里不另存 index。
 */
data class PluginSource(
    /** 稳定标识，删除/排序时用它定位；取 json 的 name，重名时由仓库侧加后缀。 */
    val id: String,
    /** 显示名，对应 json 的 `name`。 */
    val name: String,
    /** 规则版本，对应 json 的 `version`。 */
    val version: String,
    /** 作者，对应 json 的 `author`；很多规则不写，空串表示不显示。 */
    val author: String,
    /** 站点根地址，对应 json 的 `baseURL`；列表上只显示 host。 */
    val baseUrl: String,
    /** 停用的源保留在列表里但不参与搜索——这是「不删但关掉」的唯一手段。 */
    val enabled: Boolean,
    /** 与规则仓库最新版本不一致时置位；仓库未接入前恒为 false。 */
    val updateAvailable: Boolean = false,
)

/**
 * 规则仓库的加速方式。
 *
 * 仓库在 GitHub，国内直连经常超时，所以拉 `index.json` 与 `<name>.json` 时统一加一层前缀。
 * 四档的来源与语义照抄 Kazumi 的 `RulesMirror`；**只影响下载，不影响已装好的规则**——
 * 这也是它能做成随手可切的原因。
 *
 * 排序即弹窗里的展示顺序：默认档打头，备用与国内镜像在后，直连垫底。
 */
enum class RuleMirror(
    @param:StringRes val labelRes: Int,
    @param:StringRes val descriptionRes: Int,
) {
    GhProxy(R.string.settings_plugin_mirror_ghproxy, R.string.settings_plugin_mirror_ghproxy_desc),
    GhFast(R.string.settings_plugin_mirror_ghfast, R.string.settings_plugin_mirror_ghfast_desc),
    GitCode(R.string.settings_plugin_mirror_gitcode, R.string.settings_plugin_mirror_gitcode_desc),
    Direct(R.string.settings_plugin_mirror_direct, R.string.settings_plugin_mirror_direct_desc),
}

/**
 * 规则仓库的默认地址。
 *
 * 写的是上游社区维护的 KazumiRules——**插件源格式与它一致，所以地址可以直接用**。
 * 结尾带 `/main` 这一段是 raw.githubusercontent 的分支名，少了它拼出来的
 * `<repo>/index.json` 会直接 404。
 *
 * 用户可在页面上改（自建仓库、换镜像仓库都靠这一项），改了只影响拉取，不影响已装好的规则。
 */
const val DEFAULT_RULES_REPO_URL = "https://raw.githubusercontent.com/Predidit/KazumiRules/main"

/**
 * 插件源设置页的界面状态。
 *
 * 只装「已安装的源」与「从哪儿拉新源」两件事：仓库里的可安装列表属于另一个页面，
 * 不塞进这里，否则同一次刷新会把两个页面的失败态搅在一起。
 */
data class PluginUiState(
    val sources: List<PluginSource> = emptyList(),
    val mirror: RuleMirror = RuleMirror.GhProxy,
    /** 规则仓库根地址，末尾不带斜杠。 */
    val repoUrl: String = DEFAULT_RULES_REPO_URL,
) {
    val isEmpty: Boolean get() = sources.isEmpty()

    /** 真正参与搜索的源数量：全停用时播放侧应当直接报「没有可用来源」。 */
    val enabledCount: Int get() = sources.count { it.enabled }
}
