package com.liomix.mikomi.feature.settings.plugin

import androidx.annotation.StringRes
import com.liomix.mikomi.core.model.PluginRule
import com.liomix.mikomi.data.video.remote.vdoplugin.InvalidVdoPluginRuleException
import com.liomix.mikomi.data.video.remote.vdoplugin.VdoRuleTestReport
import com.liomix.mikomi.feature.settings.R

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
 * 一次规则测试的现场。
 *
 * 关键词留在状态里（而不是 sheet 内部的 `remember`）：测试要跑网络，转屏或切后台回来时
 * 结果和「用的是哪个词」必须对得上，否则报告就成了无源之水。
 *
 * @property running 正在跑「搜索 → 剧集 → 出流」。UI 据此转圈并禁用按钮。
 * @property report 最近一次报告；还没跑过时为 null。
 */
data class RuleTestState(
    val ruleName: String,
    val keyword: String = "",
    val running: Boolean = false,
    val report: VdoRuleTestReport? = null,
)

/**
 * 规则编辑面板的状态。非空即面板打开。
 *
 * @property text 编辑区里的内容（用户可能改了一半）。
 * @property originalText 打开时的原文。两者不等即「有未保存修改」，关闭前要问一句。
 * @property reason 保存前的本地校验失败原因；不合法就不该写盘，规则仓库里留半份坏文件没意义。
 * @property failure 写盘或改名撞车这类**非格式**失败的原因，原样来自数据层，便于现场判断。
 *
 * 保存成功不留在状态里：那等于「面板已经关了」，ViewModel 直接把整份状态置空，
 * 于是不需要额外的一次性事件去驱动关闭。
 */
data class RuleEditorState(
    val ruleName: String = "",
    val loading: Boolean = true,
    /** 规则文件已经不在磁盘上（被别处删了，或刚从列表里删掉）。 */
    val missing: Boolean = false,
    val text: String = "",
    val originalText: String = "",
    val saving: Boolean = false,
    val reason: InvalidVdoPluginRuleException.Reason? = null,
    val failure: String? = null,
) {
    /** 有未保存的修改。关闭面板时据此决定是直接关还是先确认。 */
    val dirty: Boolean get() = !loading && !missing && text != originalText

    /** 没有改动时保存按钮就该灰着：点一下写一遍同样的内容，只会让人以为「是不是没生效」。 */
    val canSave: Boolean get() = dirty && !saving
}

/**
 * 待分享的规则。
 *
 * 分享要拉起系统面板，必须拿到 Activity 上下文；而 ViewModel 不该碰 Context。
 * 所以这里只把「分享什么」放进状态，由 Route 取走并调起 Intent，再回头清空——
 * 与商店页的一次性提示同一套路子。
 */
data class PluginShare(val name: String, val text: String)

/**
 * 插件源设置页的界面状态。
 *
 * 只装「已安装的源」与「从哪儿拉新源」两件事：仓库里的可安装列表属于另一个页面，
 * 不塞进这里，否则同一次刷新会把两个页面的失败态搅在一起。
 */
data class PluginUiState(
    val rules: List<PluginRule> = emptyList(),
    /** 首次磁盘扫描尚未完成。为 true 时不能显示「还没有插件源」——那会闪一下再被列表顶掉。 */
    val loading: Boolean = true,
    val mirror: RuleMirror = RuleMirror.GhProxy,
    /** 规则仓库根地址，末尾不带斜杠。 */
    val repoUrl: String = DEFAULT_RULES_REPO_URL,
    /** 打开着的测试面板；null 表示关闭。 */
    val test: RuleTestState? = null,
    /** 打开着的编辑面板；null 表示关闭。 */
    val editor: RuleEditorState? = null,
    /** 待拉起的分享；Route 消费后置空。 */
    val pendingShare: PluginShare? = null,
) {
    val isEmpty: Boolean get() = !loading && rules.isEmpty()

    /** 真正参与搜索的源数量：全停用时播放侧应当直接报「没有可用来源」。 */
    val enabledCount: Int get() = rules.count { it.enabled }
}
