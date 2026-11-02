package com.liomix.mikomi.feature.settings.plugin

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 数据源插件设置页的 ViewModel。
 *
 * 目前是**空壳**：没有任何仓库、没有 IO，插件清单是写死的占位数据，
 * 开关只在内存里翻转（退出页面就还原）。这样页面骨架、导航与交互先跑通，
 * 接 `PluginRepository` 时只替换 [init] 里的数据来源与 [onToggle] 的落盘即可，UI 一行不用改。
 */
@HiltViewModel
class PluginViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(PluginUiState(plugins = PLACEHOLDER_PLUGINS))
    val uiState: StateFlow<PluginUiState> = _uiState.asStateFlow()

    /**
     * 启用/停用某个插件。
     *
     * TODO: 接仓库后改成「写 DataStore → 由仓库重启对应源的会话」，
     *  现在只翻内存里的开关，明确不落盘（见页面底部的占位说明）。
     */
    fun onToggle(id: String) {
        _uiState.update { state ->
            state.copy(
                plugins = state.plugins.map {
                    if (it.id == id) it.copy(enabled = !it.enabled) else it
                },
            )
        }
    }

    private companion object {
        /**
         * 占位插件。名字沿用播放器里已出现的源（插件源 / AniRpb / Mikan），
         * 免得日后接入时还要再编一批对不上的假名字。
         */
        val PLACEHOLDER_PLUGINS = listOf(
            PluginItem(
                id = "plugin-source",
                name = "插件源",
                version = "1.0.3",
                description = "聚合插件，内置 DM84 与樱花两条线路",
                enabled = true,
            ),
            PluginItem(
                id = "anirpb",
                name = "AniRpb",
                version = "0.9.1",
                description = "在线资源站，按番名检索剧集",
                enabled = true,
            ),
            PluginItem(
                id = "mikan",
                name = "Mikan",
                version = "0.4.0",
                description = "种子站，需要本地下载器配合",
                enabled = false,
            ),
        )
    }
}
