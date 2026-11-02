package com.liomix.mikomi.feature.settings.plugin

/**
 * 一个数据源插件。
 *
 * 字段先按「列表上要显示什么」定，是**占位模型**：接入真正的插件仓库后，
 * 大概率还要补 author / homepage / 权限声明 / 更新状态，届时再一起对齐。
 */
data class PluginItem(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val enabled: Boolean,
)

/**
 * 数据源插件设置页的界面状态。
 *
 * 「已安装列表」与「当前启用的源」是两件事：一个插件可以装着但关掉，
 * 所以启用状态记在插件条目上，不在页面级。
 */
data class PluginUiState(
    val plugins: List<PluginItem> = emptyList(),
) {
    val isEmpty: Boolean get() = plugins.isEmpty()
}
