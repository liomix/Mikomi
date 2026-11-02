package com.liomix.mikomi.data.video.remote.vdoplugin

import com.liomix.mikomi.core.model.PluginRule
import kotlinx.coroutines.flow.StateFlow

/**
 * 插件源（规则文件）仓库：规则的唯一可信来源。
 *
 * 落盘形态很简单，两份文件各管一件事：
 * - `files/vdoplugin/rules/<name>.json` —— 规则正文，与仓库上下载的那份**逐字一致**。
 *   单独成文件是为了编辑和分享都能原样进出，不用先把字段模型再拼回去。
 * - `files/vdoplugin/state.json` —— 顺序与启停。这两项是本地偏好，不属于规则本身，
 *   混进正文会让「分享出去的规则」带着别人的排序状态。
 *
 * [rules] 是热流：插件源页看它、商店页也算已装版本，安装/删除/改名在任一页面发生后，
 * 另一页会自己跟着变，不需要谁去通知谁。
 *
 * 写操作都是挂起的，内部串行（同一时刻只有一个在改文件），失败以异常抛出：
 * - [InvalidVdoPluginRuleException] —— 规则本身不合法；
 * - [java.io.IOException] —— 写盘失败。
 */
interface VdoPluginRepository {

    /** 已安装规则，下标即搜索优先级。首次读取前是空列表，用 [loaded] 区分「还没读完」与「真没有」。 */
    val rules: StateFlow<List<PluginRule>>

    /** 首次磁盘扫描是否完成。为 false 时界面应当显示加载态，而不是「还没有插件源」。 */
    val loaded: StateFlow<Boolean>

    /** 按名字取一条；列表里没有但磁盘上有（刚被外部文件改动）时也会读出来。 */
    suspend fun get(name: String): PluginRule?

    /**
     * 安装或更新一份规则。
     *
     * 同名的直接覆盖：商店里的「更新」与「安装」在存储上是一回事，只是版本号变了。
     * 已存在的规则**保留**原来的启停状态与排序位置。
     */
    suspend fun install(json: String): PluginRule

    /**
     * 保存编辑结果。
     *
     * @param originalName 编辑前的名字。改了名就等于换了文件，这里负责把旧文件删掉，
     *   并把新名字顶在原来的位置上——否则改个名字规则就会掉到列表末尾。
     */
    suspend fun save(originalName: String, json: String): PluginRule

    /** 真删文件。删完顺序自动顺延，不留空位。 */
    suspend fun remove(name: String)

    /** 启停：**不删文件**，只记进 state。停用的源不进搜索。 */
    suspend fun setEnabled(name: String, enabled: Boolean)

    /** 上移/下移一格；[delta] 只接受 ±1，越界时什么都不做。 */
    suspend fun move(name: String, delta: Int)
}
