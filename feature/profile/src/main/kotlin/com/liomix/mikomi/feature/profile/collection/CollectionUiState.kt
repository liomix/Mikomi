package com.liomix.mikomi.feature.profile.collection

import androidx.compose.runtime.Immutable
import com.liomix.mikomi.core.model.Favorite

/**
 * 收藏区状态。
 *
 * 一屏一份：内容（Loading / Empty / Loaded）+ 整理模式 + 最近一次取消的凭据。
 * 合成一个对象而不是三条流，是因为它们必须在同一帧里彼此一致 ——
 * 拆开写一定会出现「整理模式已退出、选中集还留着」这种中间态，列表会闪一下高亮。
 *
 * @property content 收藏内容本身（来自数据库，ViewModel 只负责映射成三态）。
 * @property managing 是否处于整理（多选）模式。
 * @property selection 已选中的番剧 id。**与内容求过交**：被别处删掉的条目不会残留成幽灵选中。
 * @property undo 非空时底部出现「已移除 · 撤销」横条，超时或撤销后由 ViewModel 清掉。
 */
@Immutable
data class CollectionUiState(
    val content: CollectionContent = CollectionContent.Loading,
    val managing: Boolean = false,
    val selection: Set<String> = emptySet(),
    val undo: CollectionUndo? = null,
) {
    /** 收藏总数（加载中为 0）。 */
    val total: Int get() = (content as? CollectionContent.Loaded)?.items?.size ?: 0

    /** 是否已全选（整理模式下的「全选 / 全不选」翻转依据）。 */
    val allSelected: Boolean get() = total > 0 && selection.size == total
}

/** 收藏内容态：首帧未读到 / 一条都没有 / 有内容。 */
sealed interface CollectionContent {
    /** 读库尚未返回。与 [Empty] 分开：空态要显示引导，加载态要显示骨架。 */
    data object Loading : CollectionContent

    data object Empty : CollectionContent

    data class Loaded(val items: List<Favorite>) : CollectionContent
}

/**
 * 一次「取消收藏」的撤销凭据。
 *
 * [items] 是被删掉的行本身（含 `addedAt`），撤销时原样写回，卡片回到原来的位次。
 * [token] 用来判定超时：连续两次取消时，先发起的那条延时任务不能把后一次的横条收掉。
 */
@Immutable
data class CollectionUndo(
    val items: List<Favorite>,
    val token: Long,
)

/**
 * 收藏区的用户动作。
 *
 * 汇成一个类型而不是七八个 lambda：入口页（我的页收藏区）与收藏页共用同一套事件，
 * Route 侧也只映射一个函数，转发链路短到能一眼读完。
 */
sealed interface CollectionEvent {
    /** 浏览模式下点卡片：打开详情（由 Route 转给导航，ViewModel 不碰导航）。 */
    data class Open(val animeId: String) : CollectionEvent

    /**
     * 进入收藏页。
     *
     * @param editAnimeId 非空表示长按了某一张卡：进去就整理并选中它。
     */
    data class OpenCollect(val editAnimeId: String?) : CollectionEvent

    /** 进入整理模式并选中这一部（长按，或从收藏页带参进入时的首次选中）。 */
    data class StartManage(val animeId: String) : CollectionEvent

    /** 整理模式下点卡片：切换选中。 */
    data class ToggleSelect(val animeId: String) : CollectionEvent

    /** 进入 / 退出整理模式。 */
    data object ToggleManage : CollectionEvent

    /** 全选 / 全不选。 */
    data object ToggleSelectAll : CollectionEvent

    /** 移除选中的收藏。 */
    data object RemoveSelected : CollectionEvent

    /** 撤销上一次移除。 */
    data object Undo : CollectionEvent
}
