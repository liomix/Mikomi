package com.liomix.mikomi.feature.settings.plugin

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 插件源设置页的 ViewModel。
 *
 * 目前是**空壳**：没有仓库、没有 IO。列表是写死的两份真实规则（DM84 / 7sefun），
 * 启停、排序、删除、加速方式、仓库地址都只改内存，退出页面即还原。
 *
 * 换成真实现时，唯一要动的地方是把占位初始化换成 `PluginSourceRepository.observeAll()`，
 * 并把事件换成仓库的挂起写操作——语义已经按仓库该有的行为定好了：
 * - 启停：**不删除**，只标记，停用的源不进搜索；
 * - 排序：列表下标就是搜索优先级，移动一格就要整表落盘；
 * - 删除：真删文件，删完序号顺延；
 * - 加速：只改下载前缀，不重装不重下，所以可以随手切。
 */
@HiltViewModel
class PluginViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(PluginUiState(sources = PLACEHOLDER_SOURCES))
    val uiState: StateFlow<PluginUiState> = _uiState.asStateFlow()

    /** 启用/停用：保留条目，只是不再参与搜索。 */
    fun onToggle(id: String) {
        _uiState.update { state ->
            state.copy(
                sources = state.sources.map {
                    if (it.id == id) it.copy(enabled = !it.enabled) else it
                },
            )
        }
    }

    /**
     * 上移一格：长按拖动列表时，被拖的那条每越过半行就调一次。
     *
     * 已在首位时不动，也不抛错——UI 侧按行高判断阈值，边界上仍可能多喊一次。
     */
    fun onMoveUp(id: String) = move(id, -1)

    /** 下移一格。 */
    fun onMoveDown(id: String) = move(id, 1)

    /** 删除：内存里移除即可，真实现要连 .json 文件一起删。 */
    fun onRemove(id: String) {
        _uiState.update { state ->
            state.copy(sources = state.sources.filterNot { it.id == id })
        }
    }

    /**
     * 改规则仓库地址。
     *
     * 末尾斜杠统一去掉：拼 `<repo>/index.json` 时多一道斜杠有些 CDN 会 404。
     * 空串与非 http(s) 直接忽略——UI 侧已做校验，这里是最后一道。
     *
     * TODO: 接仓库后要落盘（DataStore）。
     */
    fun onRepoUrlChanged(url: String) {
        val normalized = url.trim().trimEnd('/')
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) return
        _uiState.update { it.copy(repoUrl = normalized) }
    }

    /**
     * 切换规则仓库加速。
     *
     * TODO: 接仓库后要落盘（DataStore），并让请求层按新前缀重建 OkHttp 实例；
     *  现在只改内存，明面上也不承诺「切了就生效」——仓库还没接。
     */
    fun onMirrorSelected(mirror: RuleMirror) {
        _uiState.update { it.copy(mirror = mirror) }
    }

    private fun move(id: String, delta: Int) {
        _uiState.update { state ->
            val from = state.sources.indexOfFirst { it.id == id }
            if (from < 0) return@update state
            val to = (from + delta).coerceIn(0, state.sources.lastIndex)
            if (to == from) return@update state
            val reordered = state.sources.toMutableList().apply {
                add(to, removeAt(from))
            }
            state.copy(sources = reordered)
        }
    }

    private companion object {
        /**
         * 占位数据取 Kazumi 官方规则里的两份真规则（同名字段、同语义），
         * 好处是日后接导入时这份数据可以直接当测试样本，不用再编一批对不上的假名字。
         *
         * 其中 7sefun 标了「可更新」，只为让更新态在界面上有样本；真实状态由仓库比对得出。
         */
        val PLACEHOLDER_SOURCES = listOf(
            PluginSource(
                id = "DM84",
                name = "DM84",
                version = "1.4",
                author = "",
                baseUrl = "https://dmbus.cc/",
                enabled = true,
            ),
            PluginSource(
                id = "7sefun",
                name = "7sefun",
                version = "1.3",
                author = "",
                baseUrl = "https://www.7sefun.top/",
                enabled = true,
                updateAvailable = true,
            ),
        )
    }
}
