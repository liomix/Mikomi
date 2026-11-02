package com.liomix.mikomi.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 播放页 ViewModel。
 *
 * 目前只维护**页面级**状态（Tab、排序、当前集、源/线路列表），数据都是占位的；
 * 接 `PlaybackCatalogRepository` 后，`bind` 里换成订阅目录即可，UI 不用动。
 *
 * 注意本项目 `NavDisplay` 没有 per-entry 的 ViewModelStore，播放页拿到的是
 * Activity 级实例，所以 [bind] 必须按 `animeId` 判等并**无条件重算**状态，
 * 不能写成「初始化过就跳过」，否则会带着上一部番的选集显示。
 */
@HiltViewModel
class PlayerViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var boundAnimeId: String? = null

    /** 绑定路由参数；同一部番内切换集只校正当前集，换番则整页重置。 */
    fun bind(animeId: String, episodeId: String) {
        _uiState.update { state ->
            if (boundAnimeId == animeId && state.episodes.isNotEmpty()) {
                state.withCurrentEpisode(episodeId)
            } else {
                placeholderState(animeId, episodeId)
            }
        }
        boundAnimeId = animeId
    }

    fun onTabSelected(tab: PlayerTab) {
        _uiState.update { it.copy(tab = tab) }
    }

    fun onOrderToggle() {
        _uiState.update {
            it.copy(
                order = when (it.order) {
                    EpisodeOrder.ASCENDING -> EpisodeOrder.DESCENDING
                    EpisodeOrder.DESCENDING -> EpisodeOrder.ASCENDING
                },
            )
        }
    }

    fun onEpisodeSelected(episode: EpisodeItem) {
        _uiState.update { it.copy(currentEpisode = episode) }
    }

    fun onDanmakuToggle() {
        _uiState.update { it.copy(danmakuEnabled = !it.danmakuEnabled) }
    }

    /** 打开换源弹窗。检测本身不在这里触发：源列表是订阅来的，弹窗只是它的视图。 */
    fun onSourceClick() {
        _uiState.update { it.copy(sourceSheetVisible = true) }
    }

    fun onSourceSheetDismiss() {
        _uiState.update { it.copy(sourceSheetVisible = false) }
    }

    /**
     * 选线路并切换播放源。
     *
     * 只在 UI 里点「线路」时调用；单线路源由整行点击直接落到这里。
     * 非就绪线路在这里再挡一层，避免旧列表的事件残留把当前源切到查不到资源的那条上。
     */
    fun onLineSelected(sourceId: String, lineId: String) {
        _uiState.update { state ->
            val source = state.sources.firstOrNull { it.id == sourceId }
            val line = source?.lines?.firstOrNull { it.id == lineId }
            if (source == null || line == null || !line.availability.selectable) {
                state
            } else {
                state.copy(
                    sources = state.sources.map {
                        if (it.id == sourceId) it.copy(selectedLineId = lineId) else it
                    },
                    selectedSourceId = sourceId,
                    sourceSheetVisible = false,
                )
            }
        }
    }

    /** 重试单个源：源自身与它的全部线路一起重查。 */
    fun onRetrySource(sourceId: String) {
        retry(sourceIds = setOf(sourceId), lineIds = emptySet())
    }

    /** 重试单条线路：不动同源下的其它线路，避免把已经能用的结果也清掉。 */
    fun onRetryLine(sourceId: String, lineId: String) {
        retry(sourceIds = emptySet(), lineIds = setOf(sourceId to lineId))
    }

    /** 「全部重试」：只重查非就绪的源，能用的源不打扰。 */
    fun onRetryAllSources() {
        val retryable = _uiState.value.sources
            .filter { it.availability == SourceAvailability.NO_RESULT || it.availability == SourceAvailability.FAILED }
            .map { it.id }
            .toSet()
        if (retryable.isEmpty()) return
        retry(sourceIds = retryable, lineIds = emptySet())
    }

    /**
     * 重试的统一入口：先打回 `CHECKING`，模拟延迟后置为 `READY`。
     *
     * TODO: 接入 `PlaybackCatalog` 后整段替换为「向会话层请求重订阅」，
     *  这里的状态翻转只是为了占位数据能演示流转。
     */
    private fun retry(sourceIds: Set<String>, lineIds: Set<Pair<String, String>>) {
        updateAvailability(sourceIds, lineIds) { SourceAvailability.CHECKING }
        viewModelScope.launch {
            delay(RETRY_SIMULATED_DELAY_MILLIS)
            updateAvailability(sourceIds, lineIds) { availability ->
                // 只翻转还停留在检测中的，避免用户中途手动切走的结果被覆盖。
                if (availability == SourceAvailability.CHECKING) SourceAvailability.READY else availability
            }
        }
    }

    private fun updateAvailability(
        sourceIds: Set<String>,
        lineIds: Set<Pair<String, String>>,
        transform: (SourceAvailability) -> SourceAvailability,
    ) {
        if (sourceIds.isEmpty() && lineIds.isEmpty()) return
        _uiState.update { state ->
            state.copy(
                sources = state.sources.map sourceLoop@{ source ->
                    val wholeSource = source.id in sourceIds
                    if (!wholeSource && lineIds.none { it.first == source.id }) return@sourceLoop source
                    source.copy(
                        status = if (wholeSource) transform(source.status) else source.status,
                        lines = source.lines.map { line ->
                            val touched = wholeSource || (source.id to line.id) in lineIds
                            if (touched) line.copy(availability = transform(line.availability)) else line
                        },
                    )
                },
            )
        }
    }

    private fun PlayerUiState.withCurrentEpisode(episodeId: String): PlayerUiState {
        val target = episodes.firstOrNull { it.id == episodeId } ?: currentEpisode ?: episodes.firstOrNull()
        return copy(currentEpisode = target)
    }

    // TODO: 接入 PlaybackCatalog 后删除；这里只是让 UI 有可看的占位数据。
    private fun placeholderState(animeId: String, episodeId: String): PlayerUiState {
        val episodes = PLACEHOLDER_SUBTITLES.mapIndexed { index, subtitle ->
            EpisodeItem(
                id = "ep-${index + 1}",
                number = index + 1,
                subtitle = subtitle,
            )
        }
        val current = episodes.firstOrNull { it.id == episodeId } ?: episodes.first()
        return PlayerUiState(
            animeId = animeId,
            animeTitle = "战车椅子",
            currentEpisode = current,
            episodes = episodes,
            sources = PLACEHOLDER_SOURCES,
            selectedSourceId = PLACEHOLDER_SOURCES.first().id,
        )
    }

    private companion object {
        /** 重试的模拟耗时，接入真实会话后删除。 */
        const val RETRY_SIMULATED_DELAY_MILLIS = 1_200L

        /**
         * 占位小标题。第 3 集故意给长标题：它用来验证选集卡片被选中时
         * 小标题的单行无限滚动（未选中的卡片静态省略，见 `EpisodeCard`）。
         */
        val PLACEHOLDER_SUBTITLES = listOf(
            "第1話",
            "第2話",
            "第3話 各自的旅途",
            "第4話",
            "第5話",
            "第6話",
            "第7話",
            "第8話",
            "第9話",
            "第10話",
            "第11話",
            "第12話",
        )

        /**
         * 占位源。覆盖了弹窗要演示的全部形态：
         * 多线路、单线路、源级检测中、线路级无结果、线路级失败。
         */
        val PLACEHOLDER_SOURCES = listOf(
            // 插件源：一个源里两条线路，正是「同一个源内有 DM84 与樱花」的形态。
            PlayerSource(
                id = "plugin",
                name = "插件源",
                lines = listOf(
                    PlayerLine("dm84", "DM84", SourceAvailability.READY, quality = "1080P"),
                    PlayerLine("sakura", "樱花", SourceAvailability.READY, quality = "720P"),
                ),
                selectedLineId = "dm84",
            ),
            PlayerSource(
                id = "anicyk",
                name = "AniCyk",
                lines = listOf(
                    PlayerLine("anicyk-default", "默认线路", SourceAvailability.READY, quality = "1080P"),
                ),
                selectedLineId = "anicyk-default",
            ),
            // 源级还在检测：接口都还没回，谈不上线路。
            PlayerSource(
                id = "anirpb",
                name = "AniRpb",
                status = SourceAvailability.CHECKING,
            ),
            // 线路级无结果：源通了、线路也探到了，但这一集没有资源。
            PlayerSource(
                id = "mikan",
                name = "Mikan",
                lines = listOf(
                    PlayerLine("mikan-main", "Mikan 主线路", SourceAvailability.NO_RESULT),
                ),
            ),
            // 线路级失败：两条线路都挂了，可以逐条重试。
            PlayerSource(
                id = "selfhost",
                name = "自建源",
                lines = listOf(
                    PlayerLine("self-a", "线路 A", SourceAvailability.FAILED, quality = "1080P"),
                    PlayerLine("self-b", "线路 B", SourceAvailability.FAILED, quality = "720P"),
                ),
            ),
        )
    }
}
