package com.liomix.mikomi.feature.settings.plugin.store

import androidx.annotation.StringRes
import com.liomix.mikomi.feature.settings.R

/**
 * 商店目录里的一条规则，对应仓库 `index.json` 的其中一项。
 *
 * 这里只有**目录元信息**，没有规则正文：正文（搜索地址、XPath 之类）在真正点「安装」时
 * 才按 `<name>.json` 去下载，所以列表本身很轻，一屏几十条也不占内存。
 *
 * 字段名与 Kazumi 的 `index.json` 一一对应，不做重命名，减少日后对齐成本。
 */
data class StoreItem(
    /** 规则名，同时也是文件名：`<name>.json`。安装 URL 与去重都靠它。 */
    val name: String,
    /** 目录里登记的版本号，与本地已装版本比较决定「安装 / 更新 / 已安装」。 */
    val version: String,
    /** 作者；很多规则不写，空串表示这一行不显示作者。 */
    val author: String,
    /** 规则最后更新时间（epoch 毫秒）；仓库没写时是 0，界面上不显示日期。 */
    val updatedAtMillis: Long,
    /** 规则是否带反爬验证：装上后搜索会多一道 WebView 验证，装之前就该知道。 */
    val antiCrawler: Boolean,
)

/** 一条规则相对本地安装情况的状态。三态由版本比较推导，不单独存储。 */
enum class StoreItemStatus {
    /** 本地没有。 */
    Install,

    /** 本地有，且目录里的版本更新。 */
    Update,

    /** 本地有，且不比目录里的旧。 */
    Installed,
}

/**
 * 列表筛选。
 *
 * 三个选项都带计数：计数本身就是「仓库有多少、我装了多少、有几个能更新」的摘要，
 * 所以不再单独做一段统计文字。
 */
enum class StoreFilter(@param:StringRes val labelRes: Int) {
    All(R.string.settings_store_filter_all),
    Installed(R.string.settings_store_filter_installed),
    Updatable(R.string.settings_store_filter_updatable),
}

/**
 * 排序方式。
 *
 * 默认按更新时间：商店的第一诉求是「最近有什么新规则」，而不是按拼音背目录。
 * 名称排序留给「我知道自己要找哪一条」的场景。
 */
enum class StoreSort(@param:StringRes val labelRes: Int) {
    RecentlyUpdated(R.string.settings_store_sort_recent),
    Name(R.string.settings_store_sort_name),
}

/**
 * 一次性提示。
 *
 * 安装结果不走 `UiState` 的持久字段，而是「放进去 → 弹一次 → 立刻清空」，
 * 免得旋屏或返回时把已经弹过的提示再弹一遍。
 */
sealed interface StoreMessage {
    /** 安装（或更新）成功。 */
    data class Installed(val name: String) : StoreMessage

    /** 下载或校验失败；[reason] 直接用异常信息，方便现场判断是网络还是仓库里没有这条。 */
    data class InstallFailed(val name: String, val reason: String) : StoreMessage
}

/**
 * 插件商店页的界面状态。
 *
 * 只装「目录 + 本地已装版本 + 视图选项」三件事。目录本身来自仓库的 `index.json`，
 * 页面不缓存到磁盘——它变得快，每次进页面重新拉一次比维护缓存简单。
 *
 * @property loading 正在拉目录。**已有数据时为「刷新中」**：页面上保留旧列表，
 *   只显示一条细进度条，不清空、不闪烁——这是从 Kazumi 那儿抄的，代价很小但体感差别很大。
 * @property error 最近一次拉取的失败原因。已有数据时只做行内提示，不覆盖列表。
 */
data class PluginStoreUiState(
    val items: List<StoreItem> = emptyList(),
    /** 已安装规则的版本表：`name -> version`。判定三态、显示版本都以它为准。 */
    val installedVersions: Map<String, String> = emptyMap(),
    /** 正在下载的规则名；同一行按钮据此转圈并禁用，也用来挡重复点击。 */
    val installing: Set<String> = emptySet(),
    val query: String = "",
    val filter: StoreFilter = StoreFilter.All,
    val sort: StoreSort = StoreSort.RecentlyUpdated,
    val loading: Boolean = false,
    val error: String? = null,
    val message: StoreMessage? = null,
) {
    val hasContent: Boolean get() = items.isNotEmpty()

    /** 有数据时再刷新：保留列表，只在顶部加一条进度。 */
    val isRefreshing: Boolean get() = loading && hasContent

    /** 首屏加载：还没有任何数据可比划，用骨架屏而不是转圈。 */
    val isFirstLoad: Boolean get() = loading && !hasContent

    /** 已安装数量：包含「有更新」的那些——它们确实装着。 */
    val installedCount: Int get() = items.count { statusOf(it) != StoreItemStatus.Install }

    /** 可更新数量。 */
    val updatableCount: Int get() = items.count { statusOf(it) == StoreItemStatus.Update }

    /** 某个筛选条件下的条目数，用于 chip 上的计数。 */
    fun countFor(filter: StoreFilter): Int = when (filter) {
        StoreFilter.All -> items.size
        StoreFilter.Installed -> installedCount
        StoreFilter.Updatable -> updatableCount
    }

    fun statusOf(item: StoreItem): StoreItemStatus {
        val localVersion = installedVersions[item.name] ?: return StoreItemStatus.Install
        return if (isRemoteNewer(localVersion, item.version)) {
            StoreItemStatus.Update
        } else {
            StoreItemStatus.Installed
        }
    }

    /**
     * 按当前搜索词、筛选与排序算出要展示的条目。
     *
     * 每次调用都重算：目录只有几十条，排序一次的成本远低于维护一份派生状态的复杂度。
     * 调用方用 `remember` 把结果缓存到这几个输入变化为止。
     */
    fun visibleItems(): List<StoreItem> {
        val keyword = query.trim().lowercase()
        val filtered = items.filter { item ->
            val matched = keyword.isEmpty() ||
                item.name.lowercase().contains(keyword) ||
                item.author.lowercase().contains(keyword)
            if (!matched) return@filter false
            when (filter) {
                StoreFilter.All -> true
                StoreFilter.Installed -> statusOf(item) != StoreItemStatus.Install
                StoreFilter.Updatable -> statusOf(item) == StoreItemStatus.Update
            }
        }
        return when (sort) {
            StoreSort.RecentlyUpdated -> filtered.sortedByDescending { it.updatedAtMillis }
            StoreSort.Name -> filtered.sortedBy { it.name.lowercase() }
        }
    }
}

/**
 * 远端版本是否比本地新。
 *
 * 逐段按数字比：`1.10` 大于 `1.4`——直接比字符串会得到相反结论，而规则版本里两位数段很常见。
 * 非数字段（`1.4-beta`）退回字典序，只为不抛错，不追求严格语义化版本。
 */
internal fun isRemoteNewer(local: String, remote: String): Boolean =
    compareVersions(local, remote) < 0

private fun compareVersions(local: String, remote: String): Int {
    val left = local.trim().split('.', '-', '_', '+')
    val right = remote.trim().split('.', '-', '_', '+')
    for (index in 0 until maxOf(left.size, right.size)) {
        val a = left.getOrNull(index) ?: "0"
        val b = right.getOrNull(index) ?: "0"
        val aNumber = a.toIntOrNull()
        val bNumber = b.toIntOrNull()
        val result = if (aNumber != null && bNumber != null) {
            aNumber.compareTo(bNumber)
        } else {
            a.compareTo(b, ignoreCase = true)
        }
        if (result != 0) return result
    }
    return 0
}
