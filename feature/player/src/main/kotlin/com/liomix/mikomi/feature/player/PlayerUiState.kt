package com.liomix.mikomi.feature.player

/** 播放页内容区 Tab。 */
enum class PlayerTab {
    EPISODES,
    COMMENTS,
}

/** 剧集排序方向：只在展示层排序，不改动数据源返回顺序。 */
enum class EpisodeOrder {
    ASCENDING,
    DESCENDING,
}

/**
 * 选集卡片的数据。
 *
 * 与领域模型 `Episode` 分开：卡片只需要「集号 + 小标题」，
 * 后续接入 `PlaybackCatalog` 时再把线路、是否有资源等状态并进来。
 */
data class EpisodeItem(
    val id: String,
    val number: Int,
    /** 小标题；只给集数、没有小标题的数据源为 null（卡片退化为只显示集号）。 */
    val subtitle: String? = null,
)

/**
 * 单个源的可用状态。
 *
 * 与 `MediaFetchSession` 的完整 8 态（含 `RateLimited` / `Abandoned`）保持同一个方向，
 * 这里先收敛成 UI 需要的四种：结果上只有「能播 / 还在查 / 查完没有 / 查挂了」四种表现，
 * 细分的限流、放弃在接入真实会话后由时间与错误码推导，不必让 UI 也认识 8 个分支。
 */
enum class SourceAvailability {
    /** 找到本集资源，可切换。 */
    READY,

    /** 检测中。 */
    CHECKING,

    /** 查询成功但没有本集资源。 */
    NO_RESULT,

    /** 请求失败（超时、解析错误、被限流）。 */
    FAILED,
    ;

    /** 只有就绪的源能被选中；其余状态下单行置灰且不可点。 */
    val selectable: Boolean get() = this == READY
}

/**
 * 一条具体线路。
 *
 * 线路是**源内部**的分支：同一个插件源可能同时给出 DM84 与樱花两套供片地址，
 * 它们共享同一个源的解析逻辑，但可用性、清晰度各自独立 ——
 * 常见情况是「源的接口通了，但其中一条线路的 CDN 已经挂了」，
 * 所以状态必须细到线路这一层，不能只记在源上。
 */
data class PlayerLine(
    val id: String,
    val name: String,
    val availability: SourceAvailability,
    /** 清晰度标签（`1080P` / `720P`）；线路没上报时为 null。 */
    val quality: String? = null,
) {
    /** 只有「查完没有」和「查挂了」值得再点一次；检测中与已就绪都不用。 */
    val isRetryable: Boolean
        get() = availability == SourceAvailability.NO_RESULT || availability == SourceAvailability.FAILED
}

/**
 * 换源弹窗里的一条源。
 *
 * 在弹窗里它是**一个 Tab**，[lines] 是这个 Tab 下的内容：
 * - [status] 是**源自身**的状态（插件 JSON 解析、鉴权、域名可达性），与线路无关；
 * - [availability] 才是给用户看的结果，由源状态与线路状态汇总得出，画成 Tab 上的状态点。
 *
 * `label` 是展示名，不是 ID：弹窗展示的是「用户可以理解的名字」，
 * 真实匹配键（canonicalId）在会话层，不进 UI 状态。
 */
data class PlayerSource(
    val id: String,
    val name: String,
    val status: SourceAvailability = SourceAvailability.READY,
    val lines: List<PlayerLine> = emptyList(),
    /** 当前选中的线路；源刚被发现、用户还没选过时为 null。 */
    val selectedLineId: String? = null,
) {
    /**
     * 源对用户呈现的可用性。
     *
     * 源自己没就绪时直接透出自己的状态（连接口都没通，谈不上线路）；
     * 源就绪后按线路汇总：只要有一条通了就算这个源能用 —— 用户只关心「能不能播」，
     * 不会因为同一源里另一条线路挂了就放弃整个源。
     */
    val availability: SourceAvailability
        get() = when {
            status != SourceAvailability.READY -> status
            lines.any { it.availability == SourceAvailability.READY } -> SourceAvailability.READY
            lines.any { it.availability == SourceAvailability.CHECKING } -> SourceAvailability.CHECKING
            lines.isEmpty() -> SourceAvailability.NO_RESULT
            lines.any { it.availability == SourceAvailability.FAILED } -> SourceAvailability.FAILED
            else -> SourceAvailability.NO_RESULT
        }

    val currentLine: PlayerLine?
        get() = lines.firstOrNull { it.id == selectedLineId }

    /**
     * Tab 内的线路顺序：能播的排前面，随后是还在查的，最后是查完没有/查挂的。
     * 用稳定排序保证同状态内维持数据源给的先后（那是源自己的偏好顺序）。
     */
    fun sortedLines(): List<PlayerLine> = lines.sortedBy { it.availability.ordinal }

    /** 源整体不可用时仍可整源重试；已就绪或正在检测的源不给重试入口。 */
    val isRetryable: Boolean
        get() = availability == SourceAvailability.NO_RESULT || availability == SourceAvailability.FAILED
}

/**
 * 播放页 UI 状态。
 *
 * 播放进度、缓冲、播放中这些**由播放层持有**的状态刻意不放在这里：
 * 它们每帧都在变，混进来会让整个页面（包含选集网格）跟着重组。
 * 目前播放器区域还没接 Media3，先用组件内部的占位状态。
 */
data class PlayerUiState(
    val animeId: String = "",
    val animeTitle: String = "",
    val currentEpisode: EpisodeItem? = null,
    val episodes: List<EpisodeItem> = emptyList(),
    val tab: PlayerTab = PlayerTab.EPISODES,
    val order: EpisodeOrder = EpisodeOrder.ASCENDING,
    val sources: List<PlayerSource> = emptyList(),
    val selectedSourceId: String? = null,
    /** 换源弹窗是否可见；弹窗只是当前源列表的一个视图，不持有自己的数据。 */
    val sourceSheetVisible: Boolean = false,
    val danmakuEnabled: Boolean = true,
) {
    /** 当前播放源。 */
    val currentSource: PlayerSource?
        get() = sources.firstOrNull { it.id == selectedSourceId }

    /** 当前线路；源还没选定线路时为 null。 */
    val currentLine: PlayerLine?
        get() = currentSource?.currentLine

    /**
     * 导航条胶囊上的名字：**优先线路名**。
     *
     * 「插件源 · DM84」这一层信息里，用户记的是线路（DM84、樱花），源名反而抽象；
     * 线路还没选定时再退回源名。
     */
    val sourceLabel: String
        get() = currentLine?.name ?: currentSource?.name.orEmpty()

    /** 当前排序方向下的展示顺序；排序在 UI 侧用 `remember` 缓存，避免每帧重排。 */
    fun sortedEpisodes(): List<EpisodeItem> = when (order) {
        EpisodeOrder.ASCENDING -> episodes.sortedBy { it.number }
        EpisodeOrder.DESCENDING -> episodes.sortedByDescending { it.number }
    }

    /**
     * 上一集。
     *
     * 按**集号**而不是当前展示顺序算：用户在倒序下点「上一集」，
     * 期望的是第 3 集而不是列表里的下一张卡。没有上一集时为 null（按钮置灰）。
     */
    val previousEpisode: EpisodeItem?
        get() = neighbor(-1)

    /** 下一集；是否可用见 [nextEpisode]。 */
    val nextEpisode: EpisodeItem?
        get() = neighbor(1)

    private fun neighbor(direction: Int): EpisodeItem? {
        val currentId = currentEpisode?.id ?: return null
        val ordered = episodes.sortedBy { it.number }
        val index = ordered.indexOfFirst { it.id == currentId }
        return if (index < 0) null else ordered.getOrNull(index + direction)
    }
}
