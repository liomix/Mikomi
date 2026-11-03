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
 * [id] 是**播放页地址**而不是数据库 id：插件源里「一集」就是某个 URL，
 * 切集就是拿这个 URL 去出流，中间不需要任何再映射。
 */
data class EpisodeItem(
    val id: String,
    val number: Int,
    /** 小标题；站点只给集数时为 null（卡片退化为只显示集号）。 */
    val subtitle: String? = null,
)

/**
 * 单个源的可用状态。
 *
 * 与 `MediaFetchSession` 的完整 8 态（含 `RateLimited` / `Abandoned`）保持同一个方向，
 * 这里先收敛成 UI 需要的四种：结果上只有「能播 / 还在查 / 查完没有 / 查挂了」四种表现。
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
 * 线路是**源内部**的分支：同一份规则的 `chapterRoads` 往往给出好几套供片地址，
 * 它们共享同一套解析逻辑，但可用性各自独立。所以状态必须细到线路这一层。
 */
data class PlayerLine(
    val id: String,
    val name: String,
    val availability: SourceAvailability,
    /** 清晰度标签（`1080P` / `720P`）；线路没上报时为 null。 */
    val quality: String? = null,
    /**
     * 这条线路最近一次出流的形态：直链（我们控制进度）还是网页（站点播放器接管）。
     *
     * 只有真正播过才知道，所以未播过时为 null。透出来的目的是让用户在换线路时
     * 能看出「为什么这条没有进度条」——否则那看起来像个 bug。
     */
    val playbackMode: PlaybackMode? = null,
) {
    /** 只有「查完没有」和「查挂了」值得再点一次；检测中与已就绪都不用。 */
    val isRetryable: Boolean
        get() = availability == SourceAvailability.NO_RESULT || availability == SourceAvailability.FAILED
}

/**
 * 换源弹窗里的一条源（一个插件源 = 一份规则）。
 *
 * [status] 是**源自身**的探测结果（搜索命中与否、页面能不能打开），
 * [lines] 是它给出的线路（规则的 `chapterRoads`）。
 *
 * [availability] 是给用户看的结果：源没就绪就直接透出，就绪后按线路汇总。
 * 注意「还没取到线路」不等于「没有结果」——就绪但线路为空时按源状态呈现，
 * 否则每个源都会先显示成「无结果」再翻成可用，闪一下很廉价。
 */
data class PlayerSource(
    val id: String,
    val name: String,
    val status: SourceAvailability = SourceAvailability.READY,
    val lines: List<PlayerLine> = emptyList(),
    /** 当前选中的线路；源刚被发现、用户还没选过时为 null。 */
    val selectedLineId: String? = null,
) {
    val availability: SourceAvailability
        get() = when {
            status != SourceAvailability.READY -> status
            lines.isEmpty() -> status
            lines.any { it.availability == SourceAvailability.READY } -> SourceAvailability.READY
            lines.any { it.availability == SourceAvailability.CHECKING } -> SourceAvailability.CHECKING
            lines.any { it.availability == SourceAvailability.FAILED } -> SourceAvailability.FAILED
            else -> SourceAvailability.NO_RESULT
        }

    val currentLine: PlayerLine?
        get() = lines.firstOrNull { it.id == selectedLineId }

    /**
     * Tab 内的线路顺序：能播的排前面，随后是还在查的，最后是查完没有/查挂的。
     * 用稳定排序保证同状态内维持源自己给的先后。
     */
    fun sortedLines(): List<PlayerLine> = lines.sortedBy { it.availability.ordinal }

    /** 源整体不可用时仍可整源重试；已就绪或正在检测的源不给重试入口。 */
    val isRetryable: Boolean
        get() = availability == SourceAvailability.NO_RESULT || availability == SourceAvailability.FAILED
}

/**
 * 这一集用什么播。
 *
 * 由出流层给出的**计划**决定（`VdoStreamPlan`），不是由规则里的布尔值决定：
 * - [NATIVE]：拿到了直链，交给 Media3，进度、倍速、填充统统归我们管；
 * - [WEB]：拿不到直链，只能把播放页放进 WebView 让站点播放器自己取地址。
 *
 * 两种形态下控制层的含义不同：WebView 里播放/进度由站点播放器掌握，
 * 我们只保留「返回 / 选集 / 全屏」，不去假装能拖它的进度条。
 */
enum class PlaybackMode {
    NATIVE,
    WEB,
}

/**
 * 播不出来的原因。
 *
 * 比数据层的 `VdoFailureReason` 多两种纯 UI 侧的原因（没有启用的源、拿不到番名），
 * 也因为这里的文案是**播放页语境**（「换个源试试」），不是测试面板语境（「这条规则有 2 项未通过」）。
 */
enum class PlayerFailureReason {
    /** 一个启用的插件源都没有。 */
    NoSource,

    /** 拿不到番名，没法拿去搜。 */
    NoTitle,

    /** 所有源都搜不到这部番。 */
    NoMatch,

    /** 规则本身坏了（JSON / 缺字段 / XPath 写错）。 */
    RuleBroken,

    /** 连不上：超时、DNS、TLS。 */
    Network,

    /** 搜到了、进得去，但取不到剧集。 */
    NoEpisodes,

    /** 播放页里翻不出地址。 */
    NoStream,

    /** 站点靠 JS 现拼地址，静态页面里没有。 */
    NeedWebView,

    /** 地址拿到了，但播放器解不了/被拒。 */
    Playback,
}

/** 一次失败：原因 + 现场细节（字段名、HTTP 码、错误码），细节只给人排查用。 */
data class PlayerFailure(
    val reason: PlayerFailureReason,
    val detail: String? = null,
)

/**
 * 播放页 UI 状态。
 *
 * 播放进度、缓冲、播放中这些**由播放层持有**（`PlayerPlaybackState`），刻意不放在这里：
 * 它们每帧都在变，混进来会让整个页面（包含选集网格）跟着重组。
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
    /** 正在探测 / 取剧集 / 出流。画面区据此显示转圈。 */
    val loading: Boolean = false,
    /** 当前这一集用哪种形态播。 */
    val mode: PlaybackMode = PlaybackMode.NATIVE,
    /** WebView 形态要加载的播放页地址；[mode] 为 [PlaybackMode.WEB] 时才有值。 */
    val webUrl: String? = null,
    /** 递增即让 WebView 重新加载（重试同一个地址时用）。 */
    val webReloadToken: Int = 0,
    /**
     * WebView 形态用的 UA。规则里写了就用规则的，没写由出流层补桌面 UA——
     * 不能留空让 WebView 用默认移动 UA，这类站点会把 Android UA 302 到「下载 App」页。
     */
    val webUserAgent: String? = null,
    /** 播不出来的原因；非空时画面区显示错误与重试。 */
    val failure: PlayerFailure? = null,
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
     * 用户记的是线路（DM84、樱花），源名反而抽象；线路还没选定时再退回源名。
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

    /** 下一集；没有下一集时为 null。 */
    val nextEpisode: EpisodeItem?
        get() = neighbor(1)

    /** 当前集在 [episodes] 里的下标。换线路时以它为准：不同线路的集号未必对得上。 */
    val currentEpisodeIndex: Int
        get() = episodes.indexOfFirst { it.id == currentEpisode?.id }.coerceAtLeast(0)

    private fun neighbor(direction: Int): EpisodeItem? {
        val currentId = currentEpisode?.id ?: return null
        val ordered = episodes.sortedBy { it.number }
        val index = ordered.indexOfFirst { it.id == currentId }
        return if (index < 0) null else ordered.getOrNull(index + direction)
    }
}
