package com.liomix.mikomi.feature.detail

import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.model.AnimeCharacter
import com.liomix.mikomi.core.model.CreditComment
import com.liomix.mikomi.core.model.CreditDetail
import com.liomix.mikomi.core.model.StaffMember
import com.liomix.mikomi.core.model.TeasingComment

/**
 * 详情页的**唯一**状态。
 *
 * 这里曾经是七条并列的流（内容态、绑定 id、收藏、主创、主创弹层、弹层评论、吐槽），
 * Route 因而要收集七次、`DetailScreen` 要收六个状态参数。拆开的理由当时是「哪个子组件需要就拆哪个」，
 * 但那是**按消费方分层**，不是按变化频率分层 —— 这七个值全都是用户操作或一次网络请求驱动的低频状态，
 * 拆开没有任何性能收益，只换来两个真实代价：
 *
 * - **非原子**：`openCreditDetail` 先写详情态再写评论态，中间那一帧「详情已 Loading、评论还是 Hidden」
 *   会被看到；`bind()` 要连续改五个可变源，任何一步抛异常都会留下半新半旧的页面。
 * - **接口膨胀**：六个状态参数让 `DetailScreen` 无法用一组样例数据摆出来看，`@Preview` 与后续拆组件
 *   都得先造六份假数据。
 *
 * 唯一的例外是播放进度那类每帧都变的值 —— 它们留在 `PlayerPlaybackState` 这种 holder 里，
 * 不进 UiState。判据是**变化频率**，不是「谁在用」。
 *
 * 含 `List`，因此刻意不加 `@Immutable`：Strong Skipping 下给含集合的模型加不可变标注，
 * 编译器会跳过相等性检查，反而让「内容没变但实例变了」的重组躲不掉。
 */
data class DetailUiState(
    /**
     * 当前已绑定的番剧 id。
     *
     * ViewModel 已按 entry 存活（`rememberViewModelStoreNavEntryDecorator`），但新 entry 的 ViewModel
     * 拿不到 Nav3 的参数，`bind` 仍要等 `LaunchedEffect`（首帧之后）才执行。UI 层用本字段对账
     * 「数据是不是这一部的」，不一致就先按 Loading 渲染 —— 任何路径下把「尚未绑定」当成有效数据，
     * 症状都是「点 A 打开了 B」。
     */
    val boundId: String? = null,
    val content: DetailContent = DetailContent.Loading,
    /** 当前番剧是否已收藏（跟随本地收藏列表实时变化）。 */
    val isFavorite: Boolean = false,
    val credits: AnimeCreditsUiState = AnimeCreditsUiState.Idle,
    val creditDetail: CreditDetailUiState = CreditDetailUiState.Hidden,
    val creditComments: CreditCommentsUiState = CreditCommentsUiState.Hidden,
    val teasing: TeasingUiState = TeasingUiState.Loading,
)

/** 番剧主体内容的加载状态。 */
sealed interface DetailContent {
    data object Loading : DetailContent

    data class Success(val anime: Anime) : DetailContent

    data object Error : DetailContent
}

/** 内部用：内容态连同它所属的 id，让 `flatMapLatest` 的输出自带归属信息。 */
internal data class BoundContent(
    val id: String?,
    val content: DetailContent,
)

/** 主创 Tab 的加载状态（懒加载：进入主创 Tab 才拉取）。 */
sealed interface AnimeCreditsUiState {
    /** 尚未加载（首次进入主创 Tab 前）。 */
    data object Idle : AnimeCreditsUiState

    data object Loading : AnimeCreditsUiState

    data class Loaded(
        val characters: List<AnimeCharacter>,
        val staff: List<StaffMember>,
    ) : AnimeCreditsUiState

    data object Error : AnimeCreditsUiState
}

/** 主创详情弹层的状态。 */
sealed interface CreditDetailUiState {
    /** 弹层未打开。 */
    data object Hidden : CreditDetailUiState

    data object Loading : CreditDetailUiState

    data class Loaded(val detail: CreditDetail) : CreditDetailUiState

    data object Error : CreditDetailUiState
}

/** 主创吐槽/评论的状态。 */
sealed interface CreditCommentsUiState {
    /** 弹层未打开。 */
    data object Hidden : CreditCommentsUiState

    data object Loading : CreditCommentsUiState

    data class Loaded(val comments: List<CreditComment>) : CreditCommentsUiState

    data object Error : CreditCommentsUiState
}

/**
 * 主创弹层的两个面（详情 + 吐槽）。
 *
 * ViewModel 内部把它们存成一份而不是两份：这两件事同开同关，分开存就要写两次赋值，
 * 而中间那一帧是可以被看见的（详情转圈、评论区还是空白）。
 */
internal data class CreditSheetUiState(
    val detail: CreditDetailUiState = CreditDetailUiState.Hidden,
    val comments: CreditCommentsUiState = CreditCommentsUiState.Hidden,
) {
    companion object {
        val Loading = CreditSheetUiState(
            detail = CreditDetailUiState.Loading,
            comments = CreditCommentsUiState.Loading,
        )
        val Hidden = CreditSheetUiState()
    }
}

/** 吐槽 Tab 状态（分页懒加载）。 */
sealed interface TeasingUiState {
    /** 首屏加载中。 */
    data object Loading : TeasingUiState

    data object Error : TeasingUiState

    data class Loaded(
        val comments: List<TeasingComment>,
        /** 是否还有更多（服务端仍有下一页）。 */
        val hasMore: Boolean,
        /** 正在加载下一页。 */
        val appending: Boolean = false,
        /** 下一页加载失败（可点击重试）。 */
        val appendFailed: Boolean = false,
    ) : TeasingUiState
}
